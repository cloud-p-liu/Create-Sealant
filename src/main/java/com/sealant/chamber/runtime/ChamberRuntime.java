package com.sealant.chamber.runtime;

import com.sealant.Sealant;
import com.sealant.chamber.*;
import com.sealant.config.SealantServerConfig;
import com.sealant.chamber.topology.ChamberTopology;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;
import org.joml.Vector3d;

/** Transient membership overlay; never writes automatic decisions into manual/saved buoyancy flags. */
@EventBusSubscriber(modid=Sealant.ID)
public final class ChamberRuntime {
    private static final Map<Level,State> states=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Pose3d WORLD=new Pose3d();
    private record Frame(UUID marker,AABB local,Pose3d pose,AABB world,SubLevel owner) {}
    private record Overlap(ChamberTopology.Group group,UUID body,double volume,double fraction,Vector3d centroid,boolean accepted) {}
    private record Cached(long tick,AABB box,boolean inside) {}
    private static final class State {
        final Set<ChamberEntity> chambers=new HashSet<>();
        final List<Frame> frames=new ArrayList<>();
        final ChamberGrid<Frame> grid=new ChamberGrid<>();
        final ChamberIntersection intersection=new ChamberIntersection();
        final Map<Integer,Cached> entityCache=new HashMap<>();
        final Set<Integer> heldEntities=new HashSet<>();
        List<ChamberTopology.Node> volumeSignature=List.of();List<ChamberTopology.Group> volumeGroups=List.of();
        final List<Overlap> overlaps=new ArrayList<>();String volumeFailure="";
        SealantServerConfig.Simulation clientMode;double clientMargin=.125;
        Set<UUID> managed=Set.of(); long tick=Long.MIN_VALUE; boolean enabled=true;
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if(event.getEntity() instanceof ChamberEntity chamber) {
            State state=states.computeIfAbsent(event.getLevel(),k->new State());
            state.chambers.add(chamber); state.tick=Long.MIN_VALUE;
            if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel server)com.sealant.chamber.physics.ChamberPhysics.invalidate(server);
        }
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        State state=states.get(event.getLevel()); if(state==null)return;
        state.entityCache.remove(event.getEntity().getId());state.heldEntities.remove(event.getEntity().getId());
        if(event.getEntity() instanceof ChamberEntity chamber) { state.chambers.remove(chamber);state.tick=Long.MIN_VALUE;
            if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel server)com.sealant.chamber.physics.ChamberPhysics.chamberLeft(server,chamber); }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel server)com.sealant.chamber.physics.ChamberPhysics.clear(server);
        states.remove(event.getLevel()); if(event.getLevel() instanceof Level level){ChamberPlayerWater.clear(level);ChamberMetrics.clear(level);}
    }
    @SubscribeEvent public static void preTick(LevelTickEvent.Pre event) { update(event.getLevel()); }

    public static void update(Level level) {
        State state=states.get(level); if(state==null || state.tick==level.getGameTime())return;
        long start=ChamberMetrics.begin(level); state.tick=level.getGameTime();
        try {
            state.frames.clear();state.overlaps.clear();
            state.entityCache.clear();
            Set<UUID> managed=new HashSet<>();double margin=entryMargin(level);
            if(state.enabled) for(ChamberEntity chamber:state.chambers) {
                if(!chamber.isAlive())continue;
                SubLevel owner=Sable.HELPER.getContaining(chamber);
                if(owner==null || owner.isRemoved())continue;
                AABB local=chamber.getBoundingBox(); Pose3d pose=new Pose3d(owner.logicalPose());
                AABB world=ChamberIntersection.worldSearchBounds(local,pose).toMojang().inflate(1e-7);
                state.frames.add(new Frame(chamber.getUUID(),local,pose,world,owner));
                managed.add(owner.getUniqueId()); // The carrier itself is always handed over.
            }
            if(!level.isClientSide){
                if(SealantServerConfig.volumeCheck())collectVolumes(level,state,managed);
                else for(var frame:state.frames){
                    ChamberMetrics.count(level,"structure_queries",1);
                    for(SubLevel other:Sable.HELPER.getAllIntersecting(level,new dev.ryanhcode.sable.companion.math.BoundingBox3d(frame.world))){
                        if(!(other instanceof ServerSubLevel) || other.isRemoved() || managed.contains(other.getUniqueId()))continue;
                        var b=other.getPlot().getBoundingBox();if(b==null)continue;
                        var box=new AABB(b.minX(),b.minY(),b.minZ(),(double)b.maxX()+1,(double)b.maxY()+1,(double)b.maxZ()+1);
                        ChamberMetrics.count(level,"structure_sat",1);
                        if(ChamberTakeoverGeometry.touches(state.intersection,frame.local,frame.pose,box,other.logicalPose(),state.managed.contains(other.getUniqueId()),margin))managed.add(other.getUniqueId());
                    }
                }
            }
            state.grid.rebuild(state.frames,Frame::world);
            if(state.frames.isEmpty())state.heldEntities.clear();
            Set<UUID> old=state.managed; state.managed=Set.copyOf(managed);
            long changed=old.stream().filter(id->!managed.contains(id)).count()+managed.stream().filter(id->!old.contains(id)).count();
            ChamberMetrics.count(level,"membership_changes",changed);
            ChamberMetrics.count(level,"chamber_frames",state.frames.size());
        } finally { ChamberMetrics.end(level,"auto_tick",start); }
    }
    /** Optional volume policy is server-only. Disabled mode never builds a union or clips polyhedra. */
    private static void collectVolumes(Level level,State state,Set<UUID> managed){
        var nodes=state.frames.stream().map(f->new ChamberTopology.Node(f.marker,f.owner.getUniqueId(),f.local))
                .sorted(Comparator.comparing(ChamberTopology.Node::id)).toList();
        if(!nodes.equals(state.volumeSignature)){
            state.volumeSignature=nodes;state.volumeFailure="";
            try {state.volumeGroups=ChamberTopology.build(nodes);}
            catch(IllegalArgumentException budget){state.volumeGroups=List.of();state.volumeFailure=budget.getMessage();}
            ChamberMetrics.count(level,"obb_volume_topology_rebuilds",1);
        }
        Map<UUID,SubLevel> owners=new HashMap<>();for(var f:state.frames)owners.put(f.owner.getUniqueId(),f.owner);
        if(!state.volumeFailure.isEmpty()){
            // A union budget failure must not crash the world, admit unmeasured bodies,
            // or strand old overlays outside the original chamber. Retain only old contacts.
            for(var frame:state.frames)for(var other:Sable.HELPER.getAllIntersecting(level,new dev.ryanhcode.sable.companion.math.BoundingBox3d(frame.world))){
                if(!(other instanceof ServerSubLevel) || other.isRemoved() || !state.managed.contains(other.getUniqueId()))continue;
                var b=other.getPlot().getBoundingBox();if(b==null)continue;
                var box=new AABB(b.minX(),b.minY(),b.minZ(),(double)b.maxX()+1,(double)b.maxY()+1,(double)b.maxZ()+1);
                if(state.intersection.touches(frame.local,frame.pose,box,other.logicalPose()))managed.add(other.getUniqueId());
            }
            ChamberMetrics.count(level,"obb_volume_domain_failures",1);return;
        }
        for(var group:state.volumeGroups){
            var owner=owners.get(group.owner());if(owner==null)continue;var pose=new Pose3d(owner.logicalPose());
            var world=ChamberIntersection.worldSearchBounds(group.union().bounds,pose).toMojang().inflate(1e-7);
            ChamberMetrics.count(level,"structure_queries",1);
            for(var other:Sable.HELPER.getAllIntersecting(level,new dev.ryanhcode.sable.companion.math.BoundingBox3d(world))){
                if(!(other instanceof ServerSubLevel) || other.isRemoved() || owners.containsKey(other.getUniqueId()))continue;
                var b=other.getPlot().getBoundingBox();if(b==null)continue;
                var box=new AABB(b.minX(),b.minY(),b.minZ(),(double)b.maxX()+1,(double)b.maxY()+1,(double)b.maxZ()+1);
                var bodyPose=new Pose3d(other.logicalPose());double bodyVolume=ChamberObbVolume.boxVolume(box,bodyPose);
                if(!Double.isFinite(bodyVolume) || bodyVolume<=0)continue;
                boolean touched=false;double volume=0;var moment=new Vector3d();
                var bc=box.getCenter();var reference=bodyPose.transformPosition(new Vector3d(bc.x,bc.y,bc.z));
                for(var piece:group.union().pieces){
                    ChamberMetrics.count(level,"structure_sat",1);
                    if(!state.intersection.touches(piece.box(),pose,box,bodyPose))continue;
                    touched=true;long timing=ChamberMetrics.begin(level);
                    var overlap=ChamberObbVolume.intersect(piece.box(),pose,box,bodyPose);
                    ChamberMetrics.end(level,"obb_volume",timing);ChamberMetrics.count(level,"obb_volume_queries",1);
                    if(overlap.volume()>0){volume+=overlap.volume();moment.fma(overlap.volume(),new Vector3d(overlap.centroid()).sub(reference));}
                }
                if(!touched)continue;
                var centroid=volume>0?moment.div(volume).add(reference):null;
                volume=Math.min(bodyVolume,volume);double fraction=ChamberObbVolume.fraction(volume,bodyVolume);
                boolean accepted=ChamberObbVolume.takeover(volume,bodyVolume,state.managed.contains(other.getUniqueId()));
                if(accepted)managed.add(other.getUniqueId());
                state.overlaps.add(new Overlap(group,other.getUniqueId(),volume,fraction,centroid,accepted));
                ChamberMetrics.count(level,"obb_volume_candidates",1);
            }
        }
    }
    public static List<String> volumeReport(Level level,UUID marker){
        if(!SealantServerConfig.volumeCheck())return List.of("OBB相交體積檢測：關閉（未進行裁切計算）；takeover.intersectionVolume可啟用。");
        update(level);var state=states.get(level);List<String> lines=new ArrayList<>();
        lines.add("完整進出接管：開啟；完整進入才接管、完整離開才釋放（邊界容忍緩衝）；途中保持原狀態。");
        if(state!=null && !state.volumeFailure.isEmpty()){lines.add("OBB體積檢測暫停："+state.volumeFailure+"；暫停新接管，既有接管仍按原框釋放。");return lines;}
        if(state!=null)for(var value:state.overlaps){
            if(marker!=null && value.group.members().stream().noneMatch(n->n.id().equals(marker)))continue;
            lines.add(String.format(Locale.ROOT,"group %s → body %s：相交 %.6f 格³；%s",value.group.id(),value.body,value.volume,value.accepted?"接管中":"未接管（等待完整進入）"));
            if(value.centroid!=null)lines.add(String.format(Locale.ROOT,"相交重心 world=(%.4f, %.4f, %.4f)",value.centroid.x,value.centroid.y,value.centroid.z));
            if(lines.size()>=25)break;
        }
        if(lines.size()==1)lines.add("目前沒有相交的其他Sable結構；carrier不需通過體積門檻。");
        return lines;
    }
    public static Collection<ChamberEntity> chambers(Level level) {
        State state=states.get(level);
        return state==null?List.of():List.copyOf(state.chambers);
    }
    public static boolean isManaged(ServerSubLevel structure) {
        State state=states.get(structure.getLevel()); return state!=null && state.enabled && state.managed.contains(structure.getUniqueId());
    }
    public static boolean enabled(Level level) { State s=states.get(level);return s==null || s.enabled; }
    public static void setEnabled(Level level,boolean enabled) {
        State state=states.computeIfAbsent(level,k->new State());state.enabled=enabled;state.tick=Long.MIN_VALUE;state.entityCache.clear();if(!enabled)state.heldEntities.clear();update(level);
        if(level instanceof net.minecraft.server.level.ServerLevel server) {
            com.sealant.chamber.physics.ChamberPhysics.invalidate(server);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersInDimension(server,new ChamberModePacket(enabled));
        }
    }
    public static boolean contains(Entity entity) {
        if(entity instanceof ChamberEntity || entity.isRemoved())return false;
        Level level=entity.level(); State state=states.get(level);
        if(state==null || !state.enabled || state.chambers.isEmpty())return false;
        update(level); if(state.frames.isEmpty())return false;
        long start=ChamberMetrics.begin(level);
        try {
            AABB box=entity.getBoundingBox(); Cached cache=state.entityCache.get(entity.getId());
            if(cache!=null && cache.tick==state.tick && cache.box.equals(box)) { ChamberMetrics.count(level,"entity_cache_hits",1);return cache.inside; }
            SubLevel owner=Sable.HELPER.getContaining(entity);
            Pose3dc pose=owner==null?WORLD:owner.logicalPose();
            AABB world=owner==null?box:ChamberIntersection.worldSearchBounds(box,pose).toMojang();
            boolean held=state.heldEntities.contains(entity.getId());double margin=entryMargin(level);
            AABB search=world.inflate(1e-7);
            boolean inside=state.grid.anyMatch(search,frame -> {
                if(!frame.world.intersects(search))return false;
                ChamberMetrics.count(level,"entity_sat",1);
                return ChamberTakeoverGeometry.touches(state.intersection,frame.local,frame.pose,box,pose,held,margin);
            });
            if(inside)state.heldEntities.add(entity.getId());else state.heldEntities.remove(entity.getId());
            state.entityCache.put(entity.getId(),new Cached(state.tick,box,inside));
            return inside;
        } finally { ChamberMetrics.end(level,"entity_lookup",start); }
    }
    @SubscribeEvent public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) { sync(event.getEntity()); }
    @SubscribeEvent public static void dimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) { sync(event.getEntity()); }
    @SubscribeEvent public static void respawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) { sync(event.getEntity()); }
    private static void sync(net.minecraft.world.entity.player.Player player) {
        if(player instanceof net.minecraft.server.level.ServerPlayer server){
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(server,new ChamberModePacket(enabled(server.level())));
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(server,new ChamberSimulationPacket(SealantServerConfig.simulation(),SealantServerConfig.entryMargin()));
        }
    }
    public static void acceptSimulation(Level level,ChamberSimulationPacket packet){
        if(!level.isClientSide)return;
        var state=states.computeIfAbsent(level,k->new State());state.clientMode=packet.mode();state.clientMargin=packet.entryMargin();
        state.tick=Long.MIN_VALUE;state.entityCache.clear();
    }
    public static boolean lightweight(Level level){
        var state=states.get(level);
        return level.isClientSide?state!=null && state.clientMode==SealantServerConfig.Simulation.LIGHTWEIGHT
                :SealantServerConfig.simulation()==SealantServerConfig.Simulation.LIGHTWEIGHT;
    }
    private static double entryMargin(Level level){var state=states.get(level);return level.isClientSide?(state==null?.125:state.clientMargin):SealantServerConfig.entryMargin();}
    /** Exact point test, deliberately independent of takeover hysteresis and presentation options. */
    public static boolean containsPoint(Level level,net.minecraft.world.phys.Vec3 world){
        var state=states.get(level);if(state==null || !state.enabled)return false;update(level);
        return state.grid.anyMatch(new AABB(world,world).inflate(1e-7),frame->
                ChamberVisualGeometry.contains(frame.local,frame.pose,world));
    }
    private ChamberRuntime() {}
}
