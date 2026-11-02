package com.sealant.chamber.runtime;

import com.sealant.chamber.*;
import com.sealant.chamber.physics.*;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import java.util.*;

/** Player-only WATER overlay. Server authoritative, client uses non-interpolated snapshots.
 * Physical state is independent of all client rendering switches; other fluids are not replaced. */
public final class ChamberPlayerWater {
    public record Contact(boolean controlled,double height,dev.ryanhcode.sable.sublevel.SubLevel waterOwner) {}
    private static final Contact NONE=new Contact(false,0,null);
    private record Snapshot(List<ChamberWaterStatePacket.Group> groups,long received,long revision) {}
    private record Cell(ChamberFluidContact.Domain domain,double surface,boolean wet,AABB world,UUID owner) {}
    private record Frame(long tick,long revision,UUID observer,boolean lightweight,List<ChamberFluidContact.Domain> domains,ChamberGrid<Cell> cells,Map<UUID,dev.ryanhcode.sable.sublevel.SubLevel> owners) {}
    private record BodyCache(Level level,long tick,long revision,boolean lightweight,AABB box,Vector3d position,org.joml.Quaterniond rotation,Vector3d scale,Contact contact) {}
    private static final Map<Level,Snapshot> client=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Level,Frame> frames=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Entity,BodyCache> bodies=Collections.synchronizedMap(new WeakHashMap<>());
    public static void accept(Level level,ChamberWaterStatePacket packet){
        if(!level.isClientSide)return;
        var old=client.get(level);client.put(level,new Snapshot(packet.groups(),level.getGameTime(),old==null?1:old.revision+1));frames.remove(level);
    }
    public static void clear(Level level){client.remove(level);frames.remove(level);synchronized(bodies){bodies.keySet().removeIf(e->e.level()==level);}}
    private static Frame frame(Entity entity){
        var level=entity.level();long now=level.getGameTime(),revision;
        List<ChamberWaterStatePacket.Group> groups;
        if(level.isClientSide){var state=client.get(level);revision=state==null?0:state.revision;
            groups=state==null || now-state.received>40?List.of():state.groups;
        }else if(entity instanceof ServerPlayer player){revision=ChamberPhysics.playerWaterRevision(player.serverLevel());groups=null;}
        else return null;
        // Per-player server packet budgets/nearby groups must not be shared with another player.
        UUID observer=level.isClientSide?null:entity.getUUID();boolean lightweight=ChamberRuntime.lightweight(level);
        var cached=frames.get(level);if(cached!=null && cached.tick==now && cached.revision==revision && Objects.equals(cached.observer,observer) && cached.lightweight==lightweight)return cached;
        if(groups==null)groups=ChamberPhysics.playerWaterGroups((ServerPlayer)entity);
        Map<UUID,Pose3d> owners=new HashMap<>();Map<UUID,dev.ryanhcode.sable.sublevel.SubLevel> ownerBodies=new HashMap<>();List<ChamberFluidContact.Domain> domains=new ArrayList<>();
        for(var marker:ChamberRuntime.chambers(level))if(marker.isAlive()){
            var owner=Sable.HELPER.getContaining(marker);if(owner==null || owner.isRemoved())continue;
            ownerBodies.put(owner.getUniqueId(),owner);
            var pose=owners.computeIfAbsent(owner.getUniqueId(),id->new Pose3d(owner.logicalPose()));
            domains.add(new ChamberFluidContact.Domain(marker.getBoundingBox(),pose));
        }
        List<Cell> cells=new ArrayList<>();
        if(!ChamberRuntime.lightweight(level))for(var group:groups){
            var pose=owners.get(group.owner());if(pose==null)continue;
            for(var cell:group.cells()){
                var c=cell.box().getCenter();double y=pose.transformPosition(new Vector3d(c.x,c.y,c.z)).y+cell.relativeSurface();
                cells.add(new Cell(new ChamberFluidContact.Domain(cell.box(),pose),y,cell.water()>1e-8,ChamberIntersection.worldSearchBounds(cell.box(),pose).toMojang(),group.owner()));
            }
        }
        var spatial=new ChamberGrid<Cell>();spatial.rebuild(cells,Cell::world);
        var result=new Frame(now,revision,observer,lightweight,List.copyOf(domains),spatial,Map.copyOf(ownerBodies));frames.put(level,result);return result;
    }
    public static Pose3d entityPose(Entity entity){var owner=Sable.HELPER.getContaining(entity);return owner==null?new Pose3d():new Pose3d(owner.logicalPose());}
    /** Shared by Entity and LocalPlayer's override: movement never samples the render camera. */
    public static Optional<Boolean> submerged(Entity entity){
        var type=entity.getEyeInFluidType();
        if(type!=net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value() && !type.isAir())return Optional.empty();
        return eyes(entity).map(wet->wet && body(entity).height()>0);
    }
    public static Optional<Boolean> eyes(Entity entity){
        if(!(entity instanceof Player) || !ChamberRuntime.enabled(entity.level()))return Optional.empty();
        var frame=frame(entity);if(frame==null)return Optional.empty();
        var eye=entityPose(entity).transformPosition(entity.getEyePosition());var point=new Vector3d(eye.x,eye.y,eye.z);
        if(frame.domains.stream().noneMatch(d->ChamberFluidContact.contains(d,point)))return Optional.empty();
        return Optional.of(frame.cells.anyMatch(new AABB(eye,eye).inflate(1e-7),c->c.wet && point.y<c.surface && ChamberFluidContact.contains(c.domain,point)));
    }
    public static Contact body(Entity entity){
        if(!(entity instanceof Player) || entity.isPassenger() || !ChamberRuntime.enabled(entity.level()))return NONE;
        var frame=frame(entity);if(frame==null)return NONE;
        var box=entity.getBoundingBox().deflate(.001);var pose=entityPose(entity);var old=bodies.get(entity);
        if(old!=null && old.level==entity.level() && old.tick==frame.tick && old.revision==frame.revision && old.lightweight==frame.lightweight && old.box.equals(box)
                && old.position.equals(pose.position()) && old.rotation.equals(pose.orientation()) && old.scale.equals(pose.scale()))return old.contact;
        List<ChamberFluidContact.Domain> overlap=new ArrayList<>();
        for(var d:frame.domains)if(ChamberObbVolume.intersect(box,pose,d.box(),d.pose()).volume()>1e-12)overlap.add(d);
        double height=0;dev.ryanhcode.sable.sublevel.SubLevel waterOwner=null;
        if(!overlap.isEmpty()){
            var world=ChamberIntersection.worldSearchBounds(box,pose).toMojang();
            for(var cell:frame.cells.query(world))if(cell.wet && cell.world.intersects(world)){
                double wet=ChamberFluidContact.height(box,pose,cell.domain,cell.surface,List.of());
                if(wet>height){height=wet;waterOwner=frame.owners.get(cell.owner);}
            }
            // At the boundary, root water OUTSIDE the exact chamber union still counts.
            if(overlap.stream().noneMatch(d->ChamberFluidContact.covers(d,box,pose))){
                int reads=0;
                outside:for(int x=(int)Math.floor(world.minX);x<Math.ceil(world.maxX);x++)for(int y=(int)Math.floor(world.minY);y<Math.ceil(world.maxY);y++)for(int z=(int)Math.floor(world.minZ);z<Math.ceil(world.maxZ);z++){
                    if(++reads>256)break outside;
                    var pos=new BlockPos(x,y,z);if(!entity.level().hasChunkAt(pos))continue;
                    var fluid=entity.level().getFluidState(pos);if(!fluid.is(FluidTags.WATER))continue;
                    double outsideHeight=ChamberFluidContact.height(box,pose,new ChamberFluidContact.Domain(new AABB(pos),new Pose3d()),y+fluid.getHeight(entity.level(),pos),overlap);
                    if(outsideHeight>height){height=outsideHeight;waterOwner=null;}
                }
            }
        }
        var result=new Contact(!overlap.isEmpty(),height,waterOwner);
        bodies.put(entity,new BodyCache(entity.level(),frame.tick,frame.revision,frame.lightweight,box,new Vector3d(pose.position()),new org.joml.Quaterniond(pose.orientation()),new Vector3d(pose.scale()),result));return result;
    }
    private ChamberPlayerWater(){}
}
