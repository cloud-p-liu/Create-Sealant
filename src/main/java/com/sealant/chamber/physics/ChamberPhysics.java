package com.sealant.chamber.physics;

import com.sealant.chamber.ChamberEntity;
import com.sealant.config.SealantServerConfig;
import com.sealant.chamber.ChamberIntersection;
import com.sealant.chamber.ChamberObbVolume;
import com.sealant.chamber.topology.*;
import com.sealant.chamber.runtime.ChamberMetrics;
import com.sealant.chamber.runtime.ChamberRuntime;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.joml.Matrix3d;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Chamber-bounded local finite-volume field. Cells exchange only through their actual shared
 * faces; no room objects or component-wide water/pressure averaging participate in simulation. */
public final class ChamberPhysics {
    private record Drag(ServerSubLevel body,Vector3d localPoint,double coefficient) {}
    private record Load(ServerSubLevel body,Vector3d localPoint,Vector3d worldForce) {}
    private record BodyLoad(ServerSubLevel body,Vector3d worldForce,Vector3d localTorque) {}
    private record Hit(boolean solid,ServerSubLevel body) {}
    private record BodyPose(ServerSubLevel body,Pose3d pose) {}
    private record PoseKey(Vector3d position,org.joml.Quaterniond orientation,Vector3d scale) {}
    private record BodyKey(UUID id,PoseKey pose) {}
    private record GeometryKey(long revision,List<String> rules,int resolution,List<BodyKey> bodies) {}
    private record VolumeKey(AABB cell,PoseKey cellPose,PoseKey blockPose) {}
    private record DomainPoseKey(Entry entry,GeometryKey geometry,List<BodyKey> poses) {}
    private record GradientKey(long revision,UUID body,PoseKey pose,Vector3d com,List<DomainPoseKey> domains) {}
    private static PoseKey poseKey(dev.ryanhcode.sable.companion.math.Pose3dc pose){
        return new PoseKey(new Vector3d(pose.position()),new org.joml.Quaterniond(pose.orientation()),new Vector3d(pose.scale()));
    }
    private record Free(int cell,UUID member,ChamberSampling.Cell local,ChamberFluid.Cell fluid) {}
    private record Outside(Vector3d point,Vector3d normal,double area,ServerSubLevel body,double depth) {}
    private record Patch(int a,int b,Vector3d point,Vector3d normal,double area,Hit wall,double depth,double riseA,double riseB) {}
    private static final class Entry {
        final ChamberTopology.Group group;final ChamberCellGrid grid;
        final List<Free> free=new ArrayList<>();
        List<ChamberWaterStatePacket.Cell> interactionCells=List.of();
        ChamberField field;double[] faceFlux=new double[0];int[] linkPatch=new int[0];
        final List<Outside> outside=new ArrayList<>();final List<Patch> patches=new ArrayList<>();
        final Map<Long,List<Integer>> cellIndex=new HashMap<>();
        final List<BodyPose> bodies=new ArrayList<>();
        final Map<UUID,Map<Integer,Double>> referenceOccupied=new HashMap<>();
        final Map<UUID,List<ChamberGridState.Cell>> inventory=new LinkedHashMap<>();
        List<ChamberGridState.Cell> parked=List.of();
        ServerSubLevel owner;double gravity,netY,weight;boolean valid,initialized;
        GeometryKey geometryKey;
        int faceResolution;
        double ownerCapacity,rootCapacityLoss,otherCapacityLoss,maxCapacityChange;
        int openings,blocked;String status="等待網格";
        Entry(ChamberTopology.Group group){
            this.group=group;this.grid=new ChamberCellGrid(group.union().pieces);
            for(int i=0;i<grid.cells.size();i++){
                var c=grid.cells.get(i).box().getCenter();
                cellIndex.computeIfAbsent(BlockPos.containing(c).asLong(),k->new ArrayList<>()).add(i);
            }
        }
        int fluidCell(Vector3d p){
            for(int i:cellIndex.getOrDefault(BlockPos.containing(p.x,p.y,p.z).asLong(),List.of())){
                var box=grid.cells.get(i).box();if(box.contains(p.x,p.y,p.z) && field.capacity(i)>0)return i;
            }return -1;
        }
    }
    private static final class State {
        final Map<UUID,Entry> entries=new LinkedHashMap<>();
        final ChamberLitePhysics lite=new ChamberLitePhysics();
        long waterRevision;
        SealantServerConfig.Simulation mode;
        CollisionCache collision;ExternalWater external;
        final ChamberLazyCache<GradientKey,Map<PressureKey,double[]>> pressureGradients=new ChamberLazyCache<>(256);
        final Map<UUID,List<ChamberBodyPressure.Sample>> bodySurfaces=new HashMap<>();
        final Map<UUID,Double> bodyForceY=new LinkedHashMap<>(),bodyEOSForceY=new LinkedHashMap<>();
        final List<String> bodyWarnings=new ArrayList<>();
        final List<Load> loads=new ArrayList<>();final List<Drag> drag=new ArrayList<>();
        final List<BodyLoad> bodyLoads=new ArrayList<>();
        List<ChamberTopology.Node> signature=List.of();
        boolean enabled=true,topologyDirty;long tick=Long.MIN_VALUE;String failure="";
        final Set<UUID> detached=new HashSet<>(),debugPlayers=new HashSet<>(),airDebugPlayers=new HashSet<>();
        long debugTick=Long.MIN_VALUE,visualTick=Long.MIN_VALUE;
    }
    private static final Map<ServerLevel,State> states=new ConcurrentHashMap<>();
    public static void clear(ServerLevel level){states.remove(level);}
    static ExternalWater externalWater(ServerLevel level){
        var state=states.computeIfAbsent(level,k->new State());
        if(state.external==null)state.external=new ExternalWater(level);return state.external;
    }
    private static CollisionCache collision(State state,ServerLevel level){
        if(state.collision==null)state.collision=new CollisionCache(level);
        state.collision.refreshConfig();return state.collision;
    }
    private static boolean relevant(State state,ServerSubLevel body,AABB world){
        if(body!=null){
            if(state.bodySurfaces.containsKey(body.getUniqueId()))return true;
            for(var entry:state.entries.values())for(var b:entry.bodies)if(b.body==body)return true;
            return false;
        }
        for(var entry:state.entries.values())if(entry.owner!=null && !entry.owner.isRemoved()
                && ChamberIntersection.worldSearchBounds(entry.group.union().bounds,entry.owner.logicalPose()).toMojang().inflate(1.1).intersects(world))return true;
        return false;
    }
    public static void blockChanged(ServerLevel level,BlockPos pos){
        var state=states.get(level);if(state==null)return;
        var containing=Sable.HELPER.getContaining(level,(double)pos.getX(),(double)pos.getZ());
        var body=containing instanceof ServerSubLevel server?server:null;
        if(state.collision!=null && relevant(state,body,new AABB(pos)))state.collision.invalidate();
        if(state.external!=null)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)state.external.columns.invalidate(pos.getX()+x,pos.getZ()+z);
        if(body!=null)state.bodySurfaces.remove(body.getUniqueId());
    }
    public static void chunkChanged(ServerLevel level,int x,int z){
        var state=states.get(level);if(state==null)return;
        var containing=Sable.HELPER.getContaining(level,(double)(x<<4),(double)(z<<4));
        var body=containing instanceof ServerSubLevel server?server:null;
        var area=new AABB(x<<4,level.getMinBuildHeight(),z<<4,(x<<4)+16,level.getMaxBuildHeight(),(z<<4)+16);
        if(state.collision!=null && relevant(state,body,area))state.collision.invalidate();
        if(state.external!=null)state.external.columns.invalidateChunk(x,z);
        if(body!=null)state.bodySurfaces.remove(body.getUniqueId());
    }
    public static void invalidateBodySurface(ServerLevel level,int x,int z){
        var state=states.get(level);if(state==null)return;
        var containing=Sable.HELPER.getContaining(level,(double)x,(double)z);
        if(containing instanceof ServerSubLevel body){
            if(state.collision!=null && relevant(state,body,new AABB(x,level.getMinBuildHeight(),z,x+16,level.getMaxBuildHeight(),z+16)))state.collision.invalidate();
            state.bodySurfaces.remove(body.getUniqueId());
        }
    }
    public static void setWaterDebug(net.minecraft.server.level.ServerPlayer player,boolean enabled){
        var state=states.computeIfAbsent(player.serverLevel(),k->new State());
        if(enabled){state.debugPlayers.add(player.getUUID());state.debugTick=Long.MIN_VALUE;}
        else for(var s:states.values())s.debugPlayers.remove(player.getUUID());
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new ChamberWaterDebugPacket(List.of()));
    }
    public static void setAirDebug(net.minecraft.server.level.ServerPlayer player,boolean enabled){
        var state=states.computeIfAbsent(player.serverLevel(),k->new State());
        if(enabled){state.airDebugPlayers.add(player.getUUID());state.debugTick=Long.MIN_VALUE;}
        else for(var s:states.values())s.airDebugPlayers.remove(player.getUUID());
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new ChamberWaterDebugPacket(List.of()));
    }
    public static boolean waterDebugEnabled(net.minecraft.server.level.ServerPlayer player){var s=states.get(player.serverLevel());return s!=null && s.debugPlayers.contains(player.getUUID());}
    public static boolean airDebugEnabled(net.minecraft.server.level.ServerPlayer player){var s=states.get(player.serverLevel());return s!=null && s.airDebugPlayers.contains(player.getUUID());}
    public static boolean enabled(ServerLevel level){var s=states.get(level);return s==null || s.enabled;}
    public static void setEnabled(ServerLevel level,boolean enabled){var s=states.computeIfAbsent(level,k->new State());s.enabled=enabled;s.loads.clear();s.drag.clear();s.bodyLoads.clear();s.tick=Long.MIN_VALUE;}
    public static void invalidate(ServerLevel level){var s=states.get(level);if(s!=null){s.tick=Long.MIN_VALUE;s.topologyDirty=true;}}
    public static void step(ServerLevel level,PhysicsPipeline pipeline,double dt) {
        if(dt<=0 || !Double.isFinite(dt))return;
        State state=states.computeIfAbsent(level,k->new State());
        if(!state.enabled || !ChamberRuntime.enabled(level))return;
        var mode=SealantServerConfig.simulation();
        if(state.mode!=mode){
            // Field inventory is already saved on markers. Keep it frozen in lightweight mode,
            // including spatial parked shares; never reset or reinterpret it as one room.
            for(var entry:state.entries.values())if(entry.field!=null)entry.field.wake();
            state.mode=mode;state.tick=Long.MIN_VALUE;state.loads.clear();state.drag.clear();state.bodyLoads.clear();
            state.visualTick=Long.MIN_VALUE;state.debugTick=Long.MIN_VALUE;
        }
        long age=state.tick==Long.MIN_VALUE?Long.MAX_VALUE:level.getGameTime()-state.tick;
        if(age<0 || age>=mode.interval){
            if(mode.field())prepare(level,state,.05*(age==Long.MAX_VALUE?1:Math.clamp(age,1,mode.interval)));
            else prepareLightweight(level,state);
        }
        long timing=ChamberMetrics.begin(level);
        try {
            // Sum in world space about the current COM, then rotate impulses to native local axes.
            Map<ServerSubLevel,ChamberWrench> totals=new HashMap<>();
            Map<ServerSubLevel,Pose3d> poses=new HashMap<>();
            for(Load load:state.loads) {
                if(load.body.isRemoved() || load.body.getMassTracker().getCenterOfMass()==null)continue;
                Pose3d pose=poses.computeIfAbsent(load.body,b->pipeline.readPose(b,new Pose3d(b.logicalPose())));
                Vector3d worldPoint=pose.transformPosition(new Vector3d(load.localPoint));
                Vector3d center=pose.transformPosition(new Vector3d(load.body.getMassTracker().getCenterOfMass()));
                ChamberWrench total=totals.computeIfAbsent(load.body,b->new ChamberWrench());
                total.add(worldPoint,load.worldForce,center);
            }
            for(var load:state.bodyLoads){
                var body=load.body;if(body.isRemoved() || !ChamberRuntime.isManaged(body) || body.getMassTracker().getCenterOfMass()==null)continue;
                var pose=poses.computeIfAbsent(body,b->pipeline.readPose(b,new Pose3d(b.logicalPose())));
                var total=totals.computeIfAbsent(body,b->new ChamberWrench());
                total.force.add(load.worldForce);
                total.torque.add(pose.orientation().transform(new Vector3d(load.localTorque)));
            }
            if(mode.field())applyLivePressure(level,state,pipeline,dt,totals,poses);
            for(var entry:totals.entrySet()) {
                var body=entry.getKey();var total=entry.getValue();
                if(total.force.lengthSquared()+total.torque.lengthSquared()<1e-14)continue;
                Pose3d pose=poses.get(body);
                // JNI applyForceAndTorque actually applies LOCAL impulses, not persistent forces.
                total.toLocalImpulse(pose.orientation(),dt);
                pipeline.applyLinearAndAngularImpulse(body,total.force,total.torque,true);
            }
            Map<ServerSubLevel,List<ChamberDamping.Point>> wetPoints=new HashMap<>();
            for(Drag drag:state.drag) {
                if(drag.body.isRemoved() || drag.body.getMassTracker().getCenterOfMass()==null)continue;
                Pose3d pose=poses.computeIfAbsent(drag.body,b->pipeline.readPose(b,new Pose3d(b.logicalPose())));
                Vector3d point=pose.transformPosition(new Vector3d(drag.localPoint));
                Vector3d com=pose.transformPosition(new Vector3d(drag.body.getMassTracker().getCenterOfMass()));
                wetPoints.computeIfAbsent(drag.body,b->new ArrayList<>()).add(new ChamberDamping.Point(point.sub(com),drag.coefficient));
            }
            for(var entry:wetPoints.entrySet()) {
                var body=entry.getKey();var pose=poses.get(body);
                Matrix3d rotation=new Matrix3d().rotation(pose.orientation());
                Matrix3d inertia=new Matrix3d(rotation).mul(body.getMassTracker().getInertiaTensor()).mul(new Matrix3d(rotation).transpose());
                var impulse=ChamberDamping.solve(body.getMassTracker().getMass(),inertia,
                        pipeline.getLinearVelocity(body,new Vector3d()),pipeline.getAngularVelocity(body,new Vector3d()),entry.getValue(),dt);
                pose.orientation().transformInverse(impulse.linear());pose.orientation().transformInverse(impulse.angular());
                pipeline.applyLinearAndAngularImpulse(body,impulse.linear(),impulse.angular(),false);
            }
            ChamberMetrics.count(level,"chamber_loaded_bodies",totals.size());
            ChamberMetrics.count(level,"chamber_drag_bodies",wetPoints.size());
        } finally {ChamberMetrics.end(level,"chamber_impulses",timing);}
    }
    public static void chamberLeft(ServerLevel level,ChamberEntity chamber){
        var state=states.get(level);if(state==null)return;
        boolean destroyed=chamber.getRemovalReason()!=null && chamber.getRemovalReason().shouldDestroy();
        if(!destroyed){for(var entry:state.entries.values())entry.inventory.remove(chamber.getUUID());state.detached.add(chamber.getUUID());}
        state.topologyDirty=true;state.tick=Long.MIN_VALUE;
        if(destroyed && state.mode!=null && state.mode.field())prepare(level,state,0);
        else {state.loads.clear();state.drag.clear();state.bodyLoads.clear();}
    }
    private static void prepareLightweight(ServerLevel level,State state){
        state.tick=level.getGameTime();state.loads.clear();state.drag.clear();state.bodyLoads.clear();state.failure="";
        try {
            state.lite.prepare(level);
            for(var load:state.lite.forces){
                addLoad(state,load.body(),load.point(),new Vector3d(0,ChamberFluid.DENSITY*load.gravity()*load.volume(),0));
                addDrag(state,load.body(),load.point(),load.drag());
            }
            sendLightweightState(level,state);
        }catch(IllegalArgumentException failure){state.failure=failure.getMessage();state.loads.clear();state.drag.clear();state.bodyLoads.clear();}
    }
    private static void sendLightweightState(ServerLevel level,State state){
        if(state.visualTick!=Long.MIN_VALUE && level.getGameTime()-state.visualTick<4)return;
        state.visualTick=level.getGameTime();
        var groups=new ArrayList<ChamberWaterStatePacket.Group>();int budget=0;
        for(var domain:state.lite.domains){
            var pieces=domain.group.union().pieces;
            if(groups.size()>=ChamberWaterStatePacket.MAX_GROUPS || budget+pieces.size()>ChamberWaterStatePacket.MAX_BOXES)continue;
            var cells=pieces.stream().map(p->new ChamberWaterStatePacket.Cell(p.box(),0,-1e6,ChamberFluid.AMBIENT)).toList();
            groups.add(new ChamberWaterStatePacket.Group(domain.group.id(),domain.group.owner(),cells));budget+=cells.size();
        }
        for(var player:level.players()){
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new ChamberWaterStatePacket(List.copyOf(groups)));
            if(state.debugPlayers.contains(player.getUUID()) || state.airDebugPlayers.contains(player.getUUID())){
                var points=new ArrayList<ChamberWaterDebugPacket.Point>();
                if(state.airDebugPlayers.contains(player.getUUID()))for(var domain:state.lite.domains)for(var box:domain.samples){
                    if(points.size()>=ChamberWaterDebugPacket.LIMIT)break;
                    var c=box.getCenter();points.add(new ChamberWaterDebugPacket.Point(c.x,c.y,c.z,0xFF77BB,.1f));
                }
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new ChamberWaterDebugPacket(List.copyOf(points)));
            }
        }
    }
    private static void prepare(ServerLevel level,State state,double elapsed){
        state.tick=level.getGameTime();state.loads.clear();state.drag.clear();state.bodyLoads.clear();
        long timing=ChamberMetrics.begin(level);
        try{
            Map<UUID,ChamberEntity> entities=new HashMap<>();Map<UUID,ServerSubLevel> owners=new HashMap<>();
            List<ChamberTopology.Node> nodes=new ArrayList<>();
            for(var chamber:ChamberRuntime.chambers(level))if(chamber.isAlive() && Sable.HELPER.getContaining(chamber) instanceof ServerSubLevel owner && !owner.isRemoved()){
                entities.put(chamber.getUUID(),chamber);owners.put(owner.getUniqueId(),owner);
                nodes.add(new ChamberTopology.Node(chamber.getUUID(),owner.getUniqueId(),chamber.getBoundingBox()));
            }
            nodes.sort(Comparator.comparing(ChamberTopology.Node::id));
            Map<UUID,List<ChamberGridState.Cell>> sources=new HashMap<>();Map<UUID,List<ChamberUnion.Piece>> covered=new HashMap<>();Set<UUID> known=new HashSet<>();
            for(var entry:state.entries.values()){
                for(var values:entry.inventory.values())sources.computeIfAbsent(entry.group.owner(),k->new ArrayList<>()).addAll(values);
                if(entry.initialized){
                    covered.computeIfAbsent(entry.group.owner(),k->new ArrayList<>()).addAll(entry.group.union().pieces);
                    for(var n:entry.group.members())known.add(n.id());
                }
            }
            List<ChamberTopology.Node> legacy=new ArrayList<>();
            for(var node:nodes)if(!known.contains(node.id()) || state.detached.contains(node.id())){
                var marker=entities.get(node.id());
                if(marker.gridInitialized())sources.computeIfAbsent(node.owner(),k->new ArrayList<>()).addAll(marker.gridState());
                else if(marker.fluidShareInitialized())legacy.add(node);
                if(marker.gridInitialized() || marker.fluidShareInitialized())covered.computeIfAbsent(node.owner(),k->new ArrayList<>()).add(new ChamberUnion.Piece(node.id(),node.box()));
            }
            if(state.topologyDirty || !nodes.equals(state.signature)){
                Map<UUID,Entry> next=new LinkedHashMap<>();
                for(var group:ChamberTopology.build(nodes))next.put(group.id(),new Entry(group));
                state.pressureGradients.clear();state.entries.clear();state.entries.putAll(next);state.signature=List.copyOf(nodes);state.topologyDirty=false;state.detached.clear();
                ChamberMetrics.count(level,"chamber_topology_rebuilds",1);
            }
            state.failure="";var external=externalWater(level);var collision=collision(state,level);
            long readsBefore=external.reads,queriesBefore=collision.queries,buildsBefore=collision.builds;
            long hitsBefore=collision.overlaps.hits,missesBefore=collision.overlaps.misses;
            long geometryTiming=ChamberMetrics.begin(level);
            for(var entry:state.entries.values())sample(level,entry,owners.get(entry.group.owner()),external,collision);
            ChamberMetrics.end(level,"field_geometry",geometryTiming);
            // Legacy aggregate saves are migrated once to spatial cell shares. No hidden reset.
            for(var node:legacy){
                List<Free> free=new ArrayList<>();
                for(var entry:state.entries.values())if(entry.group.owner().equals(node.owner()))for(var cell:entry.free)if(node.box().contains(cell.local.point().x,cell.local.point().y,cell.local.point().z))free.add(cell);
                var marker=entities.get(node.id());var old=new ChamberFluid();old.restore(marker.fluid().totalWater(),marker.fluid().gasPV());
                old.advance(free.stream().map(Free::fluid).toList(),List.of(),10,0);
                var migrated=distribute(free,old);
                if(migrated.isEmpty() && (old.totalWater()>0 || old.gasPV()>0))migrated=List.of(new ChamberGridState.Cell(node.box(),old.totalWater(),old.gasPV()));
                sources.computeIfAbsent(node.owner(),k->new ArrayList<>()).addAll(migrated);
            }
            for(var entry:state.entries.values()){
                if(!entry.valid){
                    // Preserve loaded/runtime inventory while an external chunk or owner is unavailable.
                    var values=sources.getOrDefault(entry.group.owner(),List.of()).stream().filter(c->entry.group.union().pieces.stream().anyMatch(p->ChamberUnion.intersectionVolume(p.box(),c.box())>0)).toList();
                    if(!values.isEmpty()){entry.inventory.clear();entry.inventory.put(entry.group.members().getFirst().id(),values);entry.initialized=true;}
                    continue;
                }
                long phaseTiming=ChamberMetrics.begin(level);
                List<ChamberGridInventory.Target> targets=new ArrayList<>();
                for(var cell:entry.free)targets.add(new ChamberGridInventory.Target(cell.cell,cellBox(cell.local)));
                var assigned=ChamberGridInventory.assign(sources.getOrDefault(entry.group.owner(),List.of()),targets,entry.grid.cells.size(),entry.group.union().pieces.stream().map(ChamberUnion.Piece::box).toList());
                entry.parked=assigned.parked();
                double[] gas=assigned.gas();
                for(var cell:entry.free)gas[cell.cell]+=ChamberFluid.AMBIENT*cell.fluid.volume()*ChamberInventory.uncovered(cellBox(cell.local),covered.getOrDefault(entry.group.owner(),List.of()))/ChamberUnion.volume(cellBox(cell.local));
                ChamberMetrics.end(level,"field_remap",phaseTiming);
                boolean wasSleeping=entry.field!=null && entry.field.sleeping();
                phaseTiming=ChamberMetrics.begin(level);
                buildField(entry,assigned.water(),gas);
                ChamberMetrics.end(level,"field_build",phaseTiming);
                phaseTiming=ChamberMetrics.begin(level);
                entry.field.advance(elapsed);
                ChamberMetrics.end(level,"field_exchange",phaseTiming);
                phaseTiming=ChamberMetrics.begin(level);
                entry.interactionCells=waterCells(entry);state.waterRevision++;
                ChamberMetrics.end(level,"field_snapshot",phaseTiming);
                ChamberMetrics.count(level,entry.field.skippedExchange()?"fluid_exchange_skips":"fluid_exchange_updates",1);
                if(entry.field.sleeping())ChamberMetrics.count(level,"fluid_sleep_groups",1);
                if(wasSleeping && !entry.field.sleeping())ChamberMetrics.count(level,"fluid_sleep_wakes",1);
                var flux=entry.field.flux();Arrays.fill(entry.faceFlux,0);
                for(int i=0;i<flux.length;i++)entry.faceFlux[entry.linkPatch[i]]=flux[i];
                phaseTiming=ChamberMetrics.begin(level);
                applyPressure(state,entry);
                ChamberMetrics.end(level,"field_loads",phaseTiming);
                phaseTiming=ChamberMetrics.begin(level);
                saveShares(entry,entities);
                ChamberMetrics.end(level,"field_save",phaseTiming);
            }
            applyBodyPressure(level,state,collision);
            long syncTiming=ChamberMetrics.begin(level);
            sendWaterState(level,state);sendWaterDebug(level,state);
            ChamberMetrics.end(level,"field_sync",syncTiming);
            ChamberMetrics.count(level,"pressure_world_reads",external.reads-readsBefore);
            ChamberMetrics.count(level,"grid_collision_queries",collision.queries-queriesBefore);
            ChamberMetrics.count(level,"grid_collision_blocks",collision.builds-buildsBefore);
            ChamberMetrics.count(level,"volume_cache_hits",collision.overlaps.hits-hitsBefore);
            ChamberMetrics.count(level,"volume_cache_misses",collision.overlaps.misses-missesBefore);
            ChamberMetrics.count(level,"chamber_connected_groups",state.entries.size());
        }catch(IllegalArgumentException failure){state.failure=failure.getMessage();state.loads.clear();state.drag.clear();state.bodyLoads.clear();}
        finally{ChamberMetrics.end(level,"chamber_pressure",timing);}
    }
    private static List<ChamberGridState.Cell> distribute(List<Free> free,ChamberFluid fluid){
        List<ChamberGridState.Cell> result=new ArrayList<>();double wet=0,dry=0,capacity=0;
        for(var cell:free){double v=cell.fluid.volume(),f=fluid.water()>1e-8?ChamberFluid.fraction(fluid.surface(),cell.fluid):0;wet+=v*f;dry+=v*(1-f);capacity+=v;}
        double assignedW=0,assignedG=0;
        for(int i=0;i<free.size();i++){
            var cell=free.get(i);double v=cell.fluid.volume(),f=fluid.water()>1e-8?ChamberFluid.fraction(fluid.surface(),cell.fluid):0;
            double w=i==free.size()-1?fluid.totalWater()-assignedW:fluid.totalWater()*(wet>1e-12?v*f/wet:v/capacity);
            double g=i==free.size()-1?fluid.gasPV()-assignedG:fluid.gasPV()*(dry>1e-12?v*(1-f)/dry:v/capacity);
            result.add(new ChamberGridState.Cell(cellBox(cell.local),Math.max(0,w),Math.max(0,g)));assignedW+=w;assignedG+=g;
        }return result;
    }
    private static AABB cellBox(ChamberSampling.Cell cell){var p=cell.point();var d=cell.size();return new AABB(p.x-d.x/2,p.y-d.y/2,p.z-d.z/2,p.x+d.x/2,p.y+d.y/2,p.z+d.z/2);}
    private static void sample(ServerLevel level,Entry entry,ServerSubLevel owner,ExternalWater external,CollisionCache collision){
        entry.valid=false;entry.owner=owner;entry.outside.clear();entry.openings=entry.blocked=0;entry.netY=0;
        if(owner==null || owner.isRemoved()){entry.status="沒有有效 owner";return;}
        var pose=new Pose3d(owner.logicalPose());var gravity=DimensionPhysicsData.getGravity(level,pose.position());
        if(gravity.y>=-1e-6 || Math.abs(gravity.x)+Math.abs(gravity.z)>1e-6 || pose.scale().distanceSquared(new Vector3d(1,1,1))>1e-10){entry.status="網格僅支援垂直重力／單位縮放";return;}
        entry.gravity=-gravity.y;entry.weight=owner.getMassTracker().getMass()*entry.gravity;
        entry.referenceOccupied.clear();
        List<BodyPose> bodies=entry.bodies;bodies.clear();bodies.add(new BodyPose(owner,pose));
        for(var other:Sable.HELPER.getAllIntersecting(level,ChamberIntersection.worldSearchBounds(entry.group.union().bounds,pose)))if(other instanceof ServerSubLevel body && body!=owner && !body.isRemoved())bodies.add(new BodyPose(body,new Pose3d(body.logicalPose())));
        bodies.subList(1,bodies.size()).sort(Comparator.comparing(b->b.body.getUniqueId()));
        int resolution=ChamberFacePassage.resolution(SealantServerConfig.simulation());
        var key=new GeometryKey(collision.revision,List.copyOf(SealantServerConfig.blockVolumes()),resolution,
                bodies.stream().map(b->new BodyKey(b.body.getUniqueId(),poseKey(b.pose))).toList());
        if(!key.equals(entry.geometryKey)){
            if(ChamberMetrics.active(level)){
                var previousKey=entry.geometryKey;
                if(previousKey==null)ChamberMetrics.count(level,"geometry_initial",1);
                else{
                    if(previousKey.revision!=key.revision)ChamberMetrics.count(level,"geometry_collision_revision",1);
                    if(!previousKey.rules.equals(key.rules))ChamberMetrics.count(level,"geometry_rules",1);
                    if(previousKey.resolution!=key.resolution)ChamberMetrics.count(level,"geometry_resolution",1);
                    if(!previousKey.bodies.getFirst().pose.equals(key.bodies.getFirst().pose))ChamberMetrics.count(level,"geometry_owner_pose",1);
                    boolean sameBodies=previousKey.bodies.size()==key.bodies.size();
                    if(sameBodies)for(int i=0;i<key.bodies.size();i++)if(!previousKey.bodies.get(i).id.equals(key.bodies.get(i).id)){sameBodies=false;break;}
                    if(!sameBodies)ChamberMetrics.count(level,"geometry_body_set",1);
                    else for(int i=1;i<key.bodies.size();i++)if(!previousKey.bodies.get(i).pose.equals(key.bodies.get(i).pose)){
                        ChamberMetrics.count(level,"geometry_other_body_pose",1);break;
                    }
                }
            }
            entry.geometryKey=null;entry.free.clear();entry.patches.clear();
            entry.ownerCapacity=entry.rootCapacityLoss=entry.otherCapacityLoss=0;
            int n=entry.grid.cells.size();
            for(int i=0;i<n;i++){
                var grid=entry.grid.cells.get(i);var box=grid.box();
                double cellVolume=ChamberUnion.volume(box),occupied=0;
                // Integrate DECLARED block values over exact block-envelope intersections.
                // These clips locate portions of unit blocks, never measure collision-shape volume.
                var ownerPos=BlockPos.containing(box.getCenter());
                if(!level.hasChunkAt(ownerPos)){entry.status="方塊體積查詢區塊未載入";return;}
                occupied=cellVolume*collision.declared(ownerPos);
                double ownCapacity=ChamberBlockVolumes.capacity(cellVolume,occupied);entry.ownerCapacity+=ownCapacity;
                for(var body:bodies)if(body.body!=owner)occupied+=declaredOverlap(level,box,pose,body.pose,collision);
                double beforeRoot=ChamberBlockVolumes.capacity(cellVolume,occupied);entry.otherCapacityLoss+=ownCapacity-beforeRoot;
                occupied+=declaredOverlap(level,box,pose,new Pose3d(),collision);
                double capacity=ChamberBlockVolumes.capacity(cellVolume,occupied);entry.rootCapacityLoss+=beforeRoot-capacity;
                if(capacity<=1e-12)continue;
                // Shape subtraction is only spatial support for V3 inventory/water height.
                // Normalize all support weights to the separately declared capacity.
                List<AABB> support=new ArrayList<>(List.of(box));
                var center=box.getCenter();var pos=BlockPos.containing(center);
                for(var shape:collision.shapes(pos)){
                    List<AABB> next=new ArrayList<>();for(var part:support)next.addAll(ChamberUnion.subtract(part,shape));support=next;
                }
                if(support.isEmpty())support=List.of(box); // Explicit state overrides can differ from collision geometry.
                double supportVolume=support.stream().mapToDouble(ChamberUnion::volume).sum();
                var up=pose.orientation().transformInverse(new Vector3d(0,1,0));
                for(var part:support){
                    var c=part.getCenter();var size=new Vector3d(part.getXsize(),part.getYsize(),part.getZsize());
                    var point=new Vector3d(c.x,c.y,c.z);var world=pose.transformPosition(new Vector3d(point));
                    double half=(Math.abs(up.x*size.x)+Math.abs(up.y*size.y)+Math.abs(up.z*size.z))/2;
                    double volume=capacity*ChamberUnion.volume(part)/supportVolume;
                    var cell=new ChamberSampling.Cell(point,size,volume);
                    entry.free.add(new Free(i,grid.member(),cell,new ChamberFluid.Cell(world.y,Math.max(1e-6,half),volume)));
                    if(entry.free.size()>65536)throw new IllegalArgumentException("Declared volume spatial-support budget");
                }
            }

            if(entry.faceResolution!=resolution){entry.faceResolution=resolution;entry.faceFlux=new double[0];}
            var up=pose.orientation().transformInverse(new Vector3d(0,1,0));
            for(var face:entry.grid.faces){
                int axis=face.axis(),u=(axis+1)%3,v=(axis+2)%3;var rect=face.rectangle();double du=ChamberCellGrid.size(rect,u)/resolution,dv=ChamberCellGrid.size(rect,v)/resolution;
                var normal=pose.orientation().transform(new Vector3d().setComponent(axis,1));
                double riseA=Math.abs(up.get(u)*du),riseB=Math.abs(up.get(v)*dv);
                for(int i=0;i<resolution;i++)for(int j=0;j<resolution;j++){
                    var local=ChamberFacePassage.point(rect,axis,face.plane(),i,j,resolution);
                    var point=pose.transformPosition(new Vector3d(local));
                    var left=hit(collision,bodies,new Vector3d(point).fma(-ChamberFacePassage.SIDE_OFFSET,normal));var right=hit(collision,bodies,new Vector3d(point).fma(ChamberFacePassage.SIDE_OFFSET,normal));var wall=left.solid?left:right;
                    entry.patches.add(new Patch(face.a(),face.b(),point,new Vector3d(normal),du*dv,wall,0,riseA,riseB));
                }
            }
            entry.geometryKey=key;ChamberMetrics.count(level,"field_geometry_rebuilds",1);
        }else ChamberMetrics.count(level,"field_geometry_cache_hits",1);
        List<Patch> refreshed=new ArrayList<>(entry.patches.size());
        for(var patch:entry.patches){
            boolean outside=patch.a<0 || patch.b<0;double depth=0;
            if(outside){depth=external.depth(new Vector3d(patch.point).fma(patch.a<0?-.06:.06,patch.normal));if(!Double.isFinite(depth)){entry.status="外部區塊未載入";return;}}
            refreshed.add(new Patch(patch.a,patch.b,patch.point,patch.normal,patch.area,patch.wall,depth,patch.riseA,patch.riseB));
            if(outside && patch.wall.solid){entry.blocked++;entry.outside.add(new Outside(patch.point,new Vector3d(patch.normal).mul(patch.a<0?-1:1),patch.area,patch.wall.body,depth));}
        }
        entry.patches.clear();entry.patches.addAll(refreshed);
        entry.valid=true;entry.status="運行中（三維逐格場／局部通量）";
        ChamberMetrics.count(level,"pressure_cells",entry.free.size());ChamberMetrics.count(level,"pressure_panels",entry.patches.size());
    }
    private static void buildField(Entry entry,double[] water,double[] gas){
        int n=entry.grid.cells.size();double[] capacity=new double[n],low=new double[n],high=new double[n];
        Arrays.fill(low,Double.POSITIVE_INFINITY);Arrays.fill(high,Double.NEGATIVE_INFINITY);
        for(var cell:entry.free){int i=cell.cell;capacity[i]+=cell.fluid.volume();low[i]=Math.min(low[i],cell.fluid.height()-cell.fluid.halfHeight());high[i]=Math.max(high[i],cell.fluid.height()+cell.fluid.halfHeight());}
        List<ChamberField.Geometry> geometry=new ArrayList<>();
        for(int i=0;i<n;i++)geometry.add(new ChamberField.Geometry(capacity[i],capacity[i]>0?low[i]:0,capacity[i]>0?high[i]:0));
        List<ChamberField.Link> links=new ArrayList<>();List<Integer> map=new ArrayList<>();
        if(entry.faceFlux.length!=entry.patches.size())entry.faceFlux=new double[entry.patches.size()];
        for(int i=0;i<entry.patches.size();i++){
            var p=entry.patches.get(i);
            if(p.wall.solid || p.a>=0 && capacity[p.a]<=0 || p.b>=0 && capacity[p.b]<=0)continue;
            var localNormal=entry.owner.logicalPose().orientation().transformInverse(new Vector3d(p.normal));
            int axis=Math.abs(localNormal.x)>.5?0:Math.abs(localNormal.y)>.5?1:2;
            double length=(p.a>=0?ChamberCellGrid.size(entry.grid.cells.get(p.a).box(),axis)/2:0)+(p.b>=0?ChamberCellGrid.size(entry.grid.cells.get(p.b).box(),axis)/2:0);
            double normalY=p.a<0?-p.normal.y:p.normal.y;
            links.add(new ChamberField.Link(p.a,p.b,p.point.y,p.area,Math.max(.01,length),p.riseA,p.riseB,ChamberFluid.AMBIENT+ChamberFluid.DENSITY*entry.gravity*p.depth,p.depth>0,normalY>.5));map.add(i);
            if(p.a<0 || p.b<0)entry.openings++;
        }
        entry.linkPatch=map.stream().mapToInt(Integer::intValue).toArray();double[] velocity=new double[map.size()];for(int i=0;i<map.size();i++)velocity[i]=entry.faceFlux[map.get(i)];
        var previous=entry.field;
        entry.field=new ChamberField(geometry,links,water,gas,velocity,entry.gravity);
        entry.maxCapacityChange=0;
        if(previous!=null)for(int i=0;i<n;i++)entry.maxCapacityChange=Math.max(entry.maxCapacityChange,Math.abs(capacity[i]-previous.capacity(i)));
        entry.field.inheritRest(previous);
    }
    private static void applyPressure(State state,Entry entry){
        double g=entry.gravity;
        // Gauge pressure on solid boundaries: external water pushes inward, each cell's
        // internal fluid pushes outward. Atmospheric baseline cancels, including partition walls.
        for(var wall:entry.outside)if(wall.body==entry.owner){
            Vector3d force=ChamberWrench.pressureForce(wall.normal,wall.area,ChamberFluid.DENSITY*g*wall.depth,0);
            addLoad(state,wall.body,wall.point,force);entry.netY+=force.y;
            addDrag(state,wall.body,wall.point,1.7*wall.area*.5*Math.clamp(wall.depth/.5,0,1));
        }
        for(var patch:entry.patches)if(patch.wall.solid && patch.wall.body==entry.owner)for(int side=0;side<2;side++){
            int i=side==0?patch.a:patch.b;if(i<0 || entry.field.capacity(i)<=0)continue;
            var normal=new Vector3d(patch.normal).mul(side==0?1:-1);
            double gauge=entry.field.at(i,patch.point.y)-ChamberFluid.AMBIENT;
            Vector3d force=ChamberWrench.pressureForce(normal,patch.area,0,gauge);
            addLoad(state,patch.wall.body,patch.point,force);entry.netY+=force.y;
            double wet=entry.field.liquid(i)>0?Math.clamp((entry.field.surface(i)-patch.point.y)/.5,0,1):0;
            addDrag(state,patch.wall.body,patch.point,1.7*patch.area*.5*wet);
        }
    }
    /** Surface forces for non-carrier bodies. Grid faces still block flow, but no longer
     * supply a second, alignment-dependent force on those same bodies. */
    private static void applyBodyPressure(ServerLevel level,State state,CollisionCache collision){
        long timing=ChamberMetrics.begin(level);state.bodyForceY.clear();state.bodyWarnings.clear();
        try {
            var entries=state.entries.values().stream().filter(e->e.valid && e.field!=null)
                    .sorted(Comparator.comparing(e->e.group.id())).toList();
            Set<UUID> carriers=new HashSet<>();Map<UUID,BodyPose> candidates=new LinkedHashMap<>();
            for(var e:entries){carriers.add(e.owner.getUniqueId());for(var b:e.bodies)candidates.putIfAbsent(b.body.getUniqueId(),b);}
            state.bodySurfaces.keySet().retainAll(candidates.keySet());
            for(var candidate:candidates.values()){
                var body=candidate.body;if(carriers.contains(body.getUniqueId()) || body.isRemoved() || body.getMassTracker().getCenterOfMass()==null || !ChamberRuntime.isManaged(body))continue;
                List<ChamberBodyPressure.Sample> samples;
                try {samples=state.bodySurfaces.computeIfAbsent(body.getUniqueId(),id->ChamberBodySurfaces.build(level,body));}
                catch(IllegalArgumentException budget){state.bodyWarnings.add("內水受力略過 "+body.getUniqueId()+"："+budget.getMessage());continue;}
                var wrench=new ChamberWrench();
                var com=candidate.pose.transformPosition(new Vector3d(body.getMassTracker().getCenterOfMass()));
                long hits=0;
                for(var sample:samples){
                    var point=candidate.pose.transformPosition(new Vector3d(sample.point()));
                    var normal=candidate.pose.orientation().transform(new Vector3d(sample.outward()));
                    Entry selected=null;int index=-1;
                    for(var entry:entries){
                        var pose=entry.owner.logicalPose();
                        var local=pose.transformPositionInverse(new Vector3d(point).fma(.003,normal));
                        if(!entry.group.union().contains(local.x,local.y,local.z))continue;
                        // Contact against another solid is not an exposed fluid surface.
                        if(hit(collision,entry.bodies,new Vector3d(point).fma(.003,normal)).solid)break;
                        index=entry.fluidCell(local);
                        // A coarse cell can be fully occluded although its face is exposed.
                        // Extend only into adjacent open fluid along the outward normal;
                        // stop at walls/domain bounds, never borrow pressure across a partition.
                        for(int k=1;index<0 && k<=8;k++){
                            var probe=new Vector3d(point).fma(k*.125,normal);
                            local=pose.transformPositionInverse(new Vector3d(probe));
                            if(!entry.group.union().contains(local.x,local.y,local.z) || hit(collision,entry.bodies,probe).solid)break;
                            index=entry.fluidCell(local);
                        }
                        if(index>=0){selected=entry;break;}
                    }
                    if(selected==null)continue;
                    // Hold only the hydrostatic head between field updates. EOS/gas pressure
                    // is coupled to live motion implicitly in applyLivePressure each native substep.
                    double gauge=selected.field.at(index,point.y)-selected.field.pressure(index);
                    var force=ChamberBodyPressure.force(normal,sample.area(),gauge);
                    wrench.add(point,force,com);hits++;
                    double wet=selected.field.liquid(index)>0?Math.clamp((selected.field.surface(index)-point.y)/.5,0,1):0;
                    addDrag(state,body,point,.85*sample.area()*wet);
                }
                // Freeze the integrated wrench, not each face's world normal. Otherwise a
                // body rotating between low-rate field updates develops a spurious torque
                // from uniform gas pressure as points rotate but old normals do not.
                state.bodyLoads.add(new BodyLoad(body,new Vector3d(wrench.force),candidate.pose.orientation().transformInverse(new Vector3d(wrench.torque))));
                state.bodyForceY.put(body.getUniqueId(),wrench.force.y);
                ChamberMetrics.count(level,"internal_body_surface_samples",samples.size());
                ChamberMetrics.count(level,"internal_body_pressure_hits",hits);
            }
            ChamberMetrics.count(level,"internal_pressure_bodies",state.bodyForceY.size());
        }finally{ChamberMetrics.end(level,"internal_body_pressure",timing);}
    }
    private record PressureKey(Entry entry,int cell) {}
    /** Live non-carrier EOS loads. Field transport remains at the configured 5/20 Hz. */
    private static void applyLivePressure(ServerLevel level,State state,PhysicsPipeline pipeline,double dt,
            Map<ServerSubLevel,ChamberWrench> totals,Map<ServerSubLevel,Pose3d> poses){
        long timing=ChamberMetrics.begin(level);state.bodyEOSForceY.clear();
        try {
            var entries=state.entries.values().stream().filter(e->e.valid && e.field!=null)
                    .sorted(Comparator.comparing(e->e.group.id())).toList();
            Set<UUID> carriers=new HashSet<>();Map<UUID,ServerSubLevel> candidates=new LinkedHashMap<>();
            Map<Entry,List<BodyPose>> liveBodies=new HashMap<>();
            for(var entry:entries){
                carriers.add(entry.owner.getUniqueId());List<BodyPose> live=new ArrayList<>();
                for(var body:entry.bodies)if(!body.body.isRemoved()){
                    var pose=poses.computeIfAbsent(body.body,k->pipeline.readPose(k,new Pose3d(k.logicalPose())));
                    live.add(new BodyPose(body.body,pose));candidates.putIfAbsent(body.body.getUniqueId(),body.body);
                }
                liveBodies.put(entry,live);
            }
            var collision=collision(state,level);long queriesBefore=collision.queries;
            long hitsBefore=collision.overlaps.hits,missesBefore=collision.overlaps.misses;
            long faceHitsBefore=state.pressureGradients.hits,faceMissesBefore=state.pressureGradients.misses;
            for(var body:candidates.values()){
                if(carriers.contains(body.getUniqueId()) || !ChamberRuntime.isManaged(body) || body.getMassTracker().getCenterOfMass()==null)continue;
                var samples=state.bodySurfaces.get(body.getUniqueId());if(samples==null)continue;
                var pose=poses.get(body);var com=pose.transformPosition(new Vector3d(body.getMassTracker().getCenterOfMass()));
                var gradientKey=new GradientKey(collision.revision,body.getUniqueId(),poseKey(pose),new Vector3d(body.getMassTracker().getCenterOfMass()),
                        entries.stream().map(e->new DomainPoseKey(e,e.geometryKey,liveBodies.get(e).stream()
                                .map(b->new BodyKey(b.body.getUniqueId(),poseKey(b.pose))).toList())).toList());
                var gradients=state.pressureGradients.get(gradientKey,()->{
                    Map<PressureKey,double[]> collected=new LinkedHashMap<>();
                for(var sample:samples){
                    var point=pose.transformPosition(new Vector3d(sample.point()));
                    var normal=pose.orientation().transform(new Vector3d(sample.outward()));
                    for(var entry:entries){
                        var ownerPose=poses.get(entry.owner);var probe=new Vector3d(point).fma(.003,normal);
                        var local=ownerPose.transformPositionInverse(new Vector3d(probe));
                        if(!entry.group.union().contains(local.x,local.y,local.z))continue;
                        if(hit(collision,liveBodies.get(entry),probe).solid)break;
                        int index=entry.fluidCell(local);
                        for(int k=1;index<0 && k<=8;k++){
                            probe.set(point).fma(k*.125,normal);local=ownerPose.transformPositionInverse(new Vector3d(probe));
                            if(!entry.group.union().contains(local.x,local.y,local.z) || hit(collision,liveBodies.get(entry),probe).solid)break;
                            index=entry.fluidCell(local);
                        }
                        if(index>=0){
                            var g=collected.computeIfAbsent(new PressureKey(entry,index),key->new double[6]);
                            var add=ChamberPressureCoupling.gradient(normal,sample.area(),new Vector3d(point).sub(com));
                            for(int j=0;j<6;j++)g[j]+=add[j];
                            break;
                        }
                    }
                }
                    return Collections.unmodifiableMap(collected);
                });
                if(gradients.isEmpty())continue;
                var reference=gradients.keySet().iterator().next().entry.owner;
                if(reference.getMassTracker().getCenterOfMass()==null)continue;
                var referencePose=poses.get(reference);
                var referenceAngular=pipeline.getAngularVelocity(reference,new Vector3d());
                var referenceLinear=pipeline.getLinearVelocity(reference,new Vector3d())
                        .add(new Vector3d(referenceAngular).cross(new Vector3d(com).sub(referencePose.transformPosition(new Vector3d(reference.getMassTracker().getCenterOfMass())))));
                List<ChamberPressureCoupling.Cell> cells=new ArrayList<>();
                for(var item:gradients.entrySet()){
                    var entry=item.getKey().entry;int index=item.getKey().cell;var gradient=item.getValue();
                    double magnitude=0;for(double v:gradient)magnitude+=v*v;if(magnitude<1e-20)continue;
                    var box=entry.grid.cells.get(index).box();
                    var sampled=entry.bodies.stream().filter(bodyPose->bodyPose.body==body).findFirst().orElse(null);if(sampled==null)continue;
                    var sampledOwner=entry.bodies.getFirst().pose;
                    double previous=entry.referenceOccupied.computeIfAbsent(body.getUniqueId(),key->new HashMap<>())
                            .computeIfAbsent(index,key->declaredOverlap(level,box,sampledOwner,sampled.pose,collision));
                    double current=declaredOverlap(level,box,poses.get(entry.owner),pose,collision);
                    double capacity=entry.field.capacity(index)+previous-current;
                    if(capacity<=1e-12)throw new IllegalArgumentException("氣壓移動容量失效："+body.getUniqueId());
                    cells.add(new ChamberPressureCoupling.Cell(capacity,entry.field.water(index),entry.field.gas(index),gradient));
                }
                if(cells.isEmpty())continue;
                Matrix3d rotation=new Matrix3d().rotation(pose.orientation());
                Matrix3d inertia=new Matrix3d(rotation).mul(body.getMassTracker().getInertiaTensor()).mul(new Matrix3d(rotation).transpose());
                var impulse=ChamberPressureCoupling.solve(body.getMassTracker().getMass(),inertia,
                        pipeline.getLinearVelocity(body,new Vector3d()).sub(referenceLinear),
                        pipeline.getAngularVelocity(body,new Vector3d()).sub(referenceAngular),cells,dt);
                var total=totals.computeIfAbsent(body,key->new ChamberWrench());
                total.force.fma(1/dt,impulse.linear());total.torque.fma(1/dt,impulse.angular());
                state.bodyEOSForceY.put(body.getUniqueId(),impulse.linear().y/dt);
                ChamberMetrics.count(level,"live_pressure_cells",cells.size());
            }
            ChamberMetrics.count(level,"live_pressure_collision_queries",collision.queries-queriesBefore);
            ChamberMetrics.count(level,"live_volume_cache_hits",collision.overlaps.hits-hitsBefore);
            ChamberMetrics.count(level,"live_volume_cache_misses",collision.overlaps.misses-missesBefore);
            ChamberMetrics.count(level,"live_face_cache_hits",state.pressureGradients.hits-faceHitsBefore);
            ChamberMetrics.count(level,"live_face_cache_misses",state.pressureGradients.misses-faceMissesBefore);
        }catch(IllegalArgumentException failure){state.failure=failure.getMessage();}
        finally{ChamberMetrics.end(level,"live_body_pressure",timing);}
    }
    private static void saveShares(Entry entry,Map<UUID,ChamberEntity> entities){
        entry.inventory.clear();for(var member:entry.group.members())entry.inventory.put(member.id(),new ArrayList<>());
        int n=entry.grid.cells.size();double[] wet=new double[n],dry=new double[n],assignedW=new double[n],assignedG=new double[n];int[] last=new int[n];Arrays.fill(last,-1);
        for(int k=0;k<entry.free.size();k++){
            var cell=entry.free.get(k);int i=cell.cell;double v=cell.fluid.volume(),f=entry.field.liquid(i)>0?ChamberFluid.fraction(entry.field.surface(i),cell.fluid):0;
            wet[i]+=v*f;dry[i]+=v*(1-f);last[i]=k;
        }
        for(int k=0;k<entry.free.size();k++){
            var cell=entry.free.get(k);int i=cell.cell;double v=cell.fluid.volume(),f=entry.field.liquid(i)>0?ChamberFluid.fraction(entry.field.surface(i),cell.fluid):0;
            double w=k==last[i]?entry.field.water(i)-assignedW[i]:entry.field.water(i)*(wet[i]>1e-12?v*f/wet[i]:v/entry.field.capacity(i));
            double g=k==last[i]?entry.field.gas(i)-assignedG[i]:entry.field.gas(i)*(dry[i]>1e-12?v*(1-f)/dry[i]:v/entry.field.capacity(i));
            entry.inventory.get(cell.member).add(new ChamberGridState.Cell(cellBox(cell.local),Math.max(0,w),Math.max(0,g)));assignedW[i]+=w;assignedG[i]+=g;
        }
        for(var value:entry.parked){UUID id=entry.group.members().getFirst().id();for(var piece:entry.group.union().pieces)if(ChamberUnion.intersectionVolume(piece.box(),value.box())>0){id=piece.member();break;}entry.inventory.get(id).add(value);}
        for(var member:entry.group.members()){
            var marker=entities.get(member.id());if(marker==null)continue;var values=entry.inventory.get(member.id());
            marker.setGridState(values);marker.fluid().restore(values.stream().mapToDouble(ChamberGridState.Cell::water).sum(),values.stream().mapToDouble(ChamberGridState.Cell::gasPV).sum());marker.markFluidShareInitialized();
        }
        entry.initialized=true;
    }
    public static boolean reset(ServerLevel level,UUID id){
        var state=states.get(level);Set<UUID> reset=new HashSet<>();
        for(var group:ChamberTopology.build(ChamberLitePhysics.nodes(level)))
            if(group.members().stream().anyMatch(n->n.id().equals(id)))for(var member:group.members())reset.add(member.id());
        if(reset.isEmpty())reset.add(id);boolean found=false;
        for(var chamber:ChamberRuntime.chambers(level))if(reset.contains(chamber.getUUID())){chamber.fluid().reset();chamber.resetFluidShare();found=true;}
        if(state!=null){state.entries.clear();state.signature=List.of();state.tick=Long.MIN_VALUE;state.loads.clear();state.drag.clear();state.bodyLoads.clear();}return found;
    }
    private static boolean nearby(Entry entry,net.minecraft.server.level.ServerPlayer player){
        var box=entry.group.union().bounds;var c=box.getCenter();var center=entry.owner.logicalPose().transformPosition(new Vector3d(c.x,c.y,c.z));
        double radius=96+Math.sqrt(box.getXsize()*box.getXsize()+box.getYsize()*box.getYsize()+box.getZsize()*box.getZsize())/2;
        return center.distanceSquared(new Vector3d(player.getX(),player.getY(),player.getZ()))<=radius*radius;
    }
    private static List<ChamberWaterStatePacket.Cell> waterCells(Entry entry){
        List<ChamberWaterStatePacket.Cell> cells=new ArrayList<>();var sampled=entry.bodies.getFirst().pose;
        for(int i=0;i<entry.grid.cells.size();i++)if(entry.field.capacity(i)>0){
            var box=entry.grid.cells.get(i).box();var c=box.getCenter();double y=sampled.transformPosition(new Vector3d(c.x,c.y,c.z)).y;
            cells.add(new ChamberWaterStatePacket.Cell(box,entry.field.liquid(i),entry.field.surface(i)-y,entry.field.pressure(i)));
        }return List.copyOf(cells);
    }
    public static long playerWaterRevision(ServerLevel level){var state=states.get(level);return state==null?0:state.waterRevision;}
    /** The same bounded authoritative snapshot used for client prediction; no visual interpolation. */
    public static List<ChamberWaterStatePacket.Group> playerWaterGroups(net.minecraft.server.level.ServerPlayer player){
        var state=states.get(player.serverLevel());if(state==null || player.level().getGameTime()-state.tick>40)return List.of();
        List<ChamberWaterStatePacket.Group> groups=new ArrayList<>();int budget=0;
        for(var entry:state.entries.values())if(entry.valid && entry.field!=null && nearby(entry,player)){
            var cells=entry.interactionCells;
            if(cells.isEmpty() || groups.size()>=ChamberWaterStatePacket.MAX_GROUPS || budget+cells.size()>ChamberWaterStatePacket.MAX_BOXES)continue;
            budget+=cells.size();groups.add(new ChamberWaterStatePacket.Group(entry.group.id(),entry.group.owner(),cells));
        }return List.copyOf(groups);
    }
    private static void sendWaterState(ServerLevel level,State state){
        if(state.visualTick!=Long.MIN_VALUE && level.getGameTime()-state.visualTick<4)return;state.visualTick=level.getGameTime();
        for(var player:level.players()){
            var groups=new ArrayList<ChamberWaterStatePacket.Group>();int budget=0;
            for(var entry:state.entries.values())if(entry.valid && entry.field!=null && nearby(entry,player)){
                var cells=entry.interactionCells;
                if(cells.isEmpty() || groups.size()>=ChamberWaterStatePacket.MAX_GROUPS || budget+cells.size()>ChamberWaterStatePacket.MAX_BOXES)continue;budget+=cells.size();
                groups.add(new ChamberWaterStatePacket.Group(entry.group.id(),entry.group.owner(),List.copyOf(cells)));
            }
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new ChamberWaterStatePacket(List.copyOf(groups)));
        }
    }
    private static void sendWaterDebug(ServerLevel level,State state){
        if(state.debugPlayers.isEmpty() && state.airDebugPlayers.isEmpty() || state.debugTick!=Long.MIN_VALUE && level.getGameTime()-state.debugTick<4)return;state.debugTick=level.getGameTime();
        state.debugPlayers.removeIf(id->level.getPlayerByUUID(id)==null);state.airDebugPlayers.removeIf(id->level.getPlayerByUUID(id)==null);
        Set<UUID> recipients=new LinkedHashSet<>(state.debugPlayers);recipients.addAll(state.airDebugPlayers);
        for(var id:recipients){var player=(net.minecraft.server.level.ServerPlayer)level.getPlayerByUUID(id);var points=new ArrayList<ChamberWaterDebugPacket.Point>();
            for(var entry:state.entries.values())if(entry.valid && entry.field!=null && nearby(entry,player)){
                int stride=Math.max(1,(int)Math.ceil(entry.free.size()/384.));int index=0;
                for(var cell:entry.free){
                    if(index++%stride!=0 || points.size()>=ChamberWaterDebugPacket.LIMIT)continue;
                    double wet=entry.field.liquid(cell.cell)>0?ChamberFluid.fraction(entry.field.surface(cell.cell),cell.fluid):0;
                    var p=cell.local.point();
                    if(state.debugPlayers.contains(id) && wet>1e-5)points.add(new ChamberWaterDebugPacket.Point(p.x,p.y,p.z,0x278FFF,(float)(.035+.105*wet)));
                    double freeAir=Math.max(0,entry.field.capacity(cell.cell)-entry.field.liquid(cell.cell));
                    double air=(1-wet)*(freeAir>1e-12?Math.min(1,entry.field.air(cell.cell)/freeAir):0);
                    if(state.airDebugPlayers.contains(id) && air>1e-5 && points.size()<ChamberWaterDebugPacket.LIMIT)
                        points.add(new ChamberWaterDebugPacket.Point(p.x,p.y,p.z,0xFF77BB,(float)(.035+.105*air)));
                }
                int portStride=Math.max(1,(int)Math.ceil(entry.openings/128.));index=0;
                if(state.debugPlayers.contains(id))for(var patch:entry.patches)if((patch.a<0 || patch.b<0) && !patch.wall.solid){
                    if(index++%portStride!=0 || points.size()>=ChamberWaterDebugPacket.LIMIT)continue;
                    var p=entry.owner.logicalPose().transformPositionInverse(new Vector3d(patch.point));points.add(new ChamberWaterDebugPacket.Point(p.x,p.y,p.z,patch.depth>0?0xFF962F:0x64E572,.085f));
                }
            }
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new ChamberWaterDebugPacket(List.copyOf(points)));
        }
    }
    private static void addDrag(State state,ServerSubLevel body,Vector3d world,double coefficient) {
        if(coefficient<=0)return;
        state.drag.add(new Drag(body,body.logicalPose().transformPositionInverse(new Vector3d(world)),coefficient));
    }
    private static void addLoad(State state,ServerSubLevel body,Vector3d world,Vector3d force) {
        if(force.lengthSquared()<1e-14)return;
        Vector3d local=body.logicalPose().transformPositionInverse(new Vector3d(world));
        state.loads.add(new Load(body,local,force));
    }
    private static Hit hit(CollisionCache query,List<BodyPose> bodies,Vector3d world){
        for(var body:bodies){var local=body.pose.transformPositionInverse(new Vector3d(world));if(query.solid(local))return new Hit(true,body.body);}
        return new Hit(query.solid(world),null);
    }
    /** Declared occupancy with exact OBB/block-envelope clipping, no volume probes. */
    private static double declaredOverlap(ServerLevel level,AABB cell,Pose3d cellPose,Pose3d blockPose,CollisionCache cache){
        return cache.overlaps.get(new VolumeKey(cell,poseKey(cellPose),poseKey(blockPose)),()->computeDeclaredOverlap(level,cell,cellPose,blockPose,cache));
    }
    private static double computeDeclaredOverlap(ServerLevel level,AABB cell,Pose3d cellPose,Pose3d blockPose,CollisionCache cache){
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++){
            var p=blockPose.transformPositionInverse(cellPose.transformPosition(new Vector3d(x==0?cell.minX:cell.maxX,y==0?cell.minY:cell.maxY,z==0?cell.minZ:cell.maxZ)));
            minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);minZ=Math.min(minZ,p.z);maxX=Math.max(maxX,p.x);maxY=Math.max(maxY,p.y);maxZ=Math.max(maxZ,p.z);
        }
        double total=0;int count=0;
        for(int x=(int)Math.floor(minX+1e-8);x<Math.ceil(maxX-1e-8);x++)for(int y=(int)Math.floor(minY+1e-8);y<Math.ceil(maxY-1e-8);y++)for(int z=(int)Math.floor(minZ+1e-8);z<Math.ceil(maxZ-1e-8);z++){
            if(++count>65536)throw new IllegalArgumentException("Declared block-volume clip budget");
            var pos=new BlockPos(x,y,z);
            if(!level.hasChunkAt(pos))throw new IllegalArgumentException("方塊體積查詢區塊未載入");
            double declared=cache.declared(pos);
            if(declared<=0)continue;
            total+=declared*ChamberObbVolume.intersect(cell,cellPose,new AABB(pos),blockPose).volume();
        }
        return total;
    }
    /** Persistent geometry cache. Real relevant block/chunk/config edits invalidate it. */
    private static final class CollisionCache {
        final ServerLevel level;final Map<Long,List<AABB>> boxes=new HashMap<>();final Map<Long,Double> volumes=new HashMap<>();
        final ChamberLazyCache<VolumeKey,Double> overlaps=new ChamberLazyCache<>(8192);
        long queries,builds,revision;List<String> rules=List.of();
        CollisionCache(ServerLevel level){this.level=level;}
        void invalidate(){boxes.clear();volumes.clear();overlaps.clear();revision++;}
        void refreshConfig(){if(!rules.equals(SealantServerConfig.blockVolumes())){rules=List.copyOf(SealantServerConfig.blockVolumes());invalidate();}}
        double declared(BlockPos pos){
            if(volumes.size()>=131072)volumes.clear();
            return volumes.computeIfAbsent(pos.asLong(),k->ChamberBlockVolumes.solid(level.getBlockState(pos),rules));
        }
        boolean solid(Vector3d point){
            queries++;var pos=BlockPos.containing(point.x,point.y,point.z);
            for(var shape:shapes(pos))if(shape.contains(point.x,point.y,point.z))return true;return false;
        }
        List<AABB> shapes(BlockPos pos){
            if(!level.hasChunkAt(pos))return List.of(); // Do not persist a miss from an unloaded chunk.
            if(boxes.size()>=131072)boxes.clear();
            return boxes.computeIfAbsent(pos.asLong(),key->{
                builds++;var block=level.getBlockState(pos);if(block.isAir())return List.of();
                return block.getCollisionShape(level,pos).toAabbs().stream().map(b->b.move(pos)).toList();
            });
        }
    }

    /** Root-world water only; does not inspect/reclassify any internal chamber sample as external water. */
    static final class ExternalWater {
        final ServerLevel level;final ChamberWaterColumns columns;long reads;
        ExternalWater(ServerLevel level){
            this.level=level;
            columns=new ChamberWaterColumns((x,y,z)->{
                var pos=new BlockPos(x,y,z);if(!level.hasChunkAt(pos))return Double.NaN;
                reads++;var fluid=level.getFluidState(pos);
                return fluid.is(FluidTags.WATER)?fluid.getHeight(level,pos):-1;
            },level.getMinBuildHeight(),level.getMaxBuildHeight());
        }
        double depth(Vector3d point){
            var pos=BlockPos.containing(point.x,point.y,point.z);
            if(!level.hasChunkAt(pos))return Double.NaN;
            return columns.depth(pos.getX(),point.y,pos.getZ());
        }
    }
    public static List<String> report(ServerLevel level,UUID id){
        var state=states.get(level);
        if(!SealantServerConfig.simulation().field()){
            var report=new ArrayList<String>();report.add("模擬模式："+SealantServerConfig.simulation().description);
            if(state!=null){if(!state.failure.isEmpty())report.add("輕量浮力暫停："+state.failure);report.addAll(state.lite.report(id));}
            else report.add("尚未計算氣室；確認auto與chamber physics已開啟。");
            report.addAll(ChamberRuntime.volumeReport(level,id));
            return report;
        }
        List<String> lines=new ArrayList<>();lines.add("模擬模式："+SealantServerConfig.simulation().description);if(state!=null && !state.failure.isEmpty())lines.add("局部場暫停："+state.failure);
        if(state!=null)for(var entry:state.entries.values()){
            if(id!=null && entry.group.members().stream().noneMatch(n->n.id().equals(id)))continue;
            var field=entry.field;double water=entry.inventory.values().stream().flatMap(Collection::stream).mapToDouble(ChamberGridState.Cell::water).sum();
            double min=Double.POSITIVE_INFINITY,max=0,lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY,maxFlux=0;int active=0;
            if(field!=null)for(int i=0;i<entry.grid.cells.size();i++)if(field.capacity(i)>0){active++;min=Math.min(min,field.pressure(i));max=Math.max(max,field.pressure(i));if(field.liquid(i)>1e-8){lo=Math.min(lo,field.surface(i));hi=Math.max(hi,field.surface(i));}}
            for(double q:entry.faceFlux)maxFlux=Math.max(maxFlux,Math.abs(q));
            lines.add(String.format(Locale.ROOT,"group %s：%s；%d chamber／%d 網格／%d 有效格；水量基準體積 %.6f，阻擋保留 %.6f；開口／外封閉 %d／%d；壓力水力 Y=%.3f",entry.group.id(),entry.status,entry.group.members().size(),entry.grid.cells.size(),active,water,entry.parked.stream().mapToDouble(ChamberGridState.Cell::water).sum(),entry.openings,entry.blocked,entry.netY));
            lines.add(String.format(Locale.ROOT,"逐格壓力 %.5f～%.5f atm；局部水面 y=%s；壓差面通量 %.6f 格³/s；氣水置換 %.6f 格³/tick；GasPV %.6f；無 room 平均",active==0?0:min/ChamberFluid.AMBIENT,max/ChamberFluid.AMBIENT,lo==Double.POSITIVE_INFINITY?"乾燥":String.format(Locale.ROOT,"%.4f～%.4f",lo,hi),maxFlux,field==null?0:field.displacementPerTick(),field==null?0:field.totalGas()));
            var rest=field==null?null:field.currentRestReport();
            lines.add(String.format(Locale.ROOT,"氣水交換：%s；目前交換殘差 %.8f 格³/tick（容忍 %.8f）",field!=null && field.sleeping()?"休眠（仍計算浮力／即時EOS）":"運行中／等待連續靜止",rest==null?0:rest.waterPerTick()+rest.gasPerTick()+rest.displacementPerTick(),ChamberField.REST_VOLUME_PER_TICK));
            if(rest!=null){lines.add(String.format(Locale.ROOT,"休眠診斷：水流 %.8f／氣流 %.8f／逆向置換 %.8f 格³/tick；外部通路 %d；最大水頭／氣體壓差 %.8g／%.8g；%s（更新前殘差 %.8f）",rest.waterPerTick(),rest.gasPerTick(),rest.displacementPerTick(),rest.exteriorLinks(),rest.waterPressureGap(),rest.gasPressureGap(),field.sleeping()?"已休眠":rest.reason(),field.restResidualPerTick()));
                double totalCapacity=0;for(int i=0;i<entry.grid.cells.size();i++)totalCapacity+=field.capacity(i);
                lines.add(String.format(Locale.ROOT,"容量診斷：carrier自由容量 %.8f；根世界扣除 %.8f；其他body扣除 %.8f；有效容量 %.8f；本次最大單格變化 %.8g",entry.ownerCapacity,entry.rootCapacityLoss,entry.otherCapacityLoss,totalCapacity,entry.maxCapacityChange));}
            lines.add("members "+entry.group.members().stream().map(n->n.id().toString()).toList());if(lines.size()>=24)break;
        }
        if(lines.size()==1)lines.add("沒有已計算的局部場；確認 auto 與 chamber physics 已開啟。");
        if(state!=null){
            state.bodyForceY.forEach((body,y)->{if(lines.size()<36)lines.add(String.format(Locale.ROOT,"內水body %s：水頭力 Y=%.4f；即時EOS壓力力 Y=%.4f",body,y,state.bodyEOSForceY.getOrDefault(body,0.0)));});
            for(var warning:state.bodyWarnings)if(lines.size()<40)lines.add(warning);
        }
        lines.addAll(ChamberRuntime.volumeReport(level,id));
        return lines;
    }
    private ChamberPhysics(){}
}
