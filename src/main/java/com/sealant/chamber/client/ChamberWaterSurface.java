package com.sealant.chamber.client;

import com.sealant.chamber.*;
import com.sealant.chamber.physics.ChamberWaterStatePacket;
import com.sealant.chamber.runtime.ChamberRuntime;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import org.joml.Vector3d;
import java.util.*;

/** Independent surface pass: no vanilla/Sodium chunk rebuilds or global world-fluid mutations. */
public final class ChamberWaterSurface {
    private record State(UUID group,UUID owner,ChamberWaterStatePacket.Cell cell,double from,double to,long changed,AABB bounds) {}
    private record Key(UUID group,AABB box) {}
    private record Volume(State state,Pose3d pose,double height,AABB world) {}
    private static final List<Volume> frame=new ArrayList<>();
    private static final Map<UUID,AABB> domainWorld=new HashMap<>();
    private record Interface(ChamberWaterMesh.Surface surface,Pose3d pose,boolean water) {}
    private static final Map<UUID,Interface> interfaces=new LinkedHashMap<>();
    private static final com.sealant.chamber.runtime.ChamberGrid<Volume> spatial=new com.sealant.chamber.runtime.ChamberGrid<>();
    private static long frameTick=Long.MIN_VALUE,ambientTick=Long.MIN_VALUE;
    private record Polygon(ChamberWaterTiles.Tile tile,double distance,int tint,int light) {}
    private static ClientLevel level;
    private static long received;
    private static final Map<Key,State> states=new LinkedHashMap<>();
    public static boolean visible(){return com.sealant.config.SealantClientConfig.value(com.sealant.config.SealantClientConfig.WATER,true);}
    public static void setVisible(boolean value){com.sealant.config.SealantClientConfig.set(com.sealant.config.SealantClientConfig.WATER,value);}
    public static void clear(){ambientTick=Long.MIN_VALUE;level=null;states.clear();frame.clear();domainWorld.clear();interfaces.clear();spatial.rebuild(frame,v->v.world);frameTick=Long.MIN_VALUE;ChamberWaterSolids.clear();}
    public static void accept(ChamberWaterStatePacket packet){
        var current=Minecraft.getInstance().level;if(current==null)return;
        if(level!=current){clear();level=current;}
        long now=current.getGameTime();var previous=new HashMap<>(states);states.clear();
        Set<UUID> groups=new HashSet<>();
        for(var group:packet.groups())for(var cell:group.cells()){
            var key=new Key(group.id(),cell.box());var old=previous.get(key);
            double from=old==null?cell.relativeSurface():relative(old,now);
            states.put(key,new State(group.id(),group.owner(),cell,from,cell.relativeSurface(),now,cell.box()));groups.add(group.id());
        }
        received=now;frameTick=Long.MIN_VALUE;ChamberWaterSolids.retain(groups);
    }
    private static double relative(State s,double now){return s.from+(s.to-s.from)*Math.clamp((now-s.changed)/4,0,1);}
    private static boolean ready(){
        var mc=Minecraft.getInstance();
        if(mc.level!=level){clear();return false;}
        return level!=null && level.getGameTime()-received<=40 && ChamberRuntime.enabled(level);
    }
    private static Map<UUID,ClientSubLevel> owners(){
        Map<UUID,ClientSubLevel> result=new HashMap<>();
        for(var chamber:ChamberRuntime.chambers(level))if(chamber.isAlive() && Sable.HELPER.getContainingClient(chamber) instanceof ClientSubLevel owner && !owner.isRemoved())result.put(owner.getUniqueId(),owner);
        return result;
    }
    private static double surface(State s,Pose3d pose,double now){
        var c=s.bounds.getCenter();return pose.transformPosition(new Vector3d(c.x,c.y,c.z)).y+relative(s,now);
    }
    /** One pose/surface snapshot per rendered frame, reusable by every camera/particle query. */
    public static void beginFrame(){
        frame.clear();domainWorld.clear();interfaces.clear();if(!ready() || !ChamberVisuals.enabled()){spatial.rebuild(frame,v->v.world);frameTick=Long.MIN_VALUE;return;}
        frameTick=level.getGameTime();var owners=owners();
        double now=level.getGameTime()+Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        for(var s:states.values()){
            var owner=owners.get(s.owner);if(owner==null)continue;
            var pose=new Pose3d(owner.renderPose());
            var bounds=ChamberIntersection.worldSearchBounds(s.bounds,pose).toMojang();
            frame.add(new Volume(s,pose,surface(s,pose,now),bounds));domainWorld.merge(s.group,bounds,AABB::minmax);
        }
        spatial.rebuild(frame,v->v.world);
        Map<UUID,List<Volume>> groups=new LinkedHashMap<>();for(var v:frame)groups.computeIfAbsent(v.state.group,k->new ArrayList<>()).add(v);
        for(var e:groups.entrySet()){
            var pose=e.getValue().getFirst().pose;boolean water=e.getValue().stream().anyMatch(v->v.state.cell.water()>1e-8);
            var cells=e.getValue().stream().map(v->new ChamberWaterMesh.Cell(v.state.bounds,v.height)).toList();
            interfaces.put(e.getKey(),new Interface(ChamberWaterMesh.prepare(cells,pose),pose,water));
        }
    }
    /** Empty means no authoritative snapshot: preserve the old chamber-only visual fallback. */
    public static Optional<Boolean> wet(Vec3 point){
        if(!ready() || !ChamberVisuals.enabled())return Optional.empty();
        if(frameTick!=level.getGameTime())beginFrame();
        boolean found=false;Set<UUID> checked=new HashSet<>();
        for(var v:spatial.query(new AABB(point,point).inflate(1e-7))){
            if(!checked.add(v.state.group))continue;var field=interfaces.get(v.state.group);if(field==null)continue;
            var wet=field.surface.wet(new Vector3d(point.x,point.y,point.z));
            if(wet.isPresent()){found=true;if(field.water && wet.get())return Optional.of(true);}
        }
        return found?Optional.of(false):Optional.empty();
    }
    /** Clip hull polygons by the same visible wet field, sampled across the transparent wall. */
    public static List<List<Vector3d>> clipWet(List<Vector3d> polygon,Vector3d sampleOffset){
        if(!ready() || !ChamberVisuals.enabled() || polygon.size()<3)return List.of();
        if(frameTick!=level.getGameTime())beginFrame();
        var samples=polygon.stream().map(p->new Vector3d(p).add(sampleOffset)).toList();
        var min=new Vector3d(Double.POSITIVE_INFINITY);var max=new Vector3d(Double.NEGATIVE_INFINITY);
        for(var p:samples){min.min(p);max.max(p);}
        var bounds=new AABB(min.x,min.y,min.z,max.x,max.y,max.z).inflate(1e-7);
        List<List<Vector3d>> result=new ArrayList<>();Set<UUID> checked=new HashSet<>();
        for(var volume:spatial.query(bounds)){
            if(!checked.add(volume.state.group))continue;var field=interfaces.get(volume.state.group);
            if(field==null || !field.water)continue;
            for(var cut:field.surface.clipWet(samples))result.add(cut.stream().map(p->new Vector3d(p).sub(sampleOffset)).toList());
        }return result;
    }
    /** Sparse native suspended-water sprites only where virtual water has no world fluid. */
    public static void ambient(Minecraft mc){
        if(mc.level==null || mc.player==null || mc.isPaused() || !ChamberVisuals.enabled())return;
        long tick=mc.level.getGameTime();if(tick==ambientTick || tick%4!=0)return;ambientTick=tick;
        var camera=mc.gameRenderer.getMainCamera();
        if(camera.getFluidInCamera()!=net.minecraft.world.level.material.FogType.WATER || !wet(camera.getPosition()).orElse(false))return;
        var center=camera.getPosition();
        for(int i=0;i<3;i++){
            var point=center.add((mc.level.random.nextDouble()-.5)*4,(mc.level.random.nextDouble()-.5)*3,(mc.level.random.nextDouble()-.5)*4);
            var pos=net.minecraft.core.BlockPos.containing(point);
            if(mc.level.hasChunkAt(pos) && mc.level.getFluidState(pos).isEmpty() && wet(point).orElse(false))
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.UNDERWATER,point.x,point.y,point.z,0,0,0);
        }
    }
    public static void render(RenderLevelStageEvent event){
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_PARTICLES)ChamberWaterMaterial.beginFrame();
        // AFTER_PARTICLES is outside chunk renderer internals; works with both dispatcher choices.
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES || !visible() || !ChamberVisuals.enabled() || !ready())return;
        if(frameTick!=level.getGameTime())beginFrame();
        var camera=event.getCamera().getPosition();
        List<Polygon> polygons=new ArrayList<>();
        for(var domain:interfaces.entrySet()){
            if(!domain.getValue().water || !event.getFrustum().isVisible(domainWorld.get(domain.getKey())))continue;
            var pose=domain.getValue().pose;
            for(var triangle:domain.getValue().surface.mesh()){
                var clipped=ChamberWaterSolids.clip(level,domain.getKey(),domainWorld.get(domain.getKey()),triangle.points());
                if(!clipped.complete())continue;
                for(var patch:clipped.polygons()){
                    for(var tile:ChamberWaterTiles.tiles(patch)){
                        Vector3d center=new Vector3d();for(var p:tile.polygon())center.add(p);center.div(tile.polygon().size());
                        var local=pose.transformPositionInverse(new Vector3d(center));
                        var normal=new Vector3d();var polygon=tile.polygon();
                        for(int k=1;k+1<polygon.size();k++)normal.add(new Vector3d(polygon.get(k)).sub(polygon.getFirst()).cross(new Vector3d(polygon.get(k+1)).sub(polygon.getFirst())));
                        double shade=normal.lengthSquared()>1e-20?.6+.4*Math.abs(normal.y)/normal.length():1;
                        int tint=ChamberWaterMaterial.shade(ChamberWaterMaterial.tint(level,center),shade);
                        polygons.add(new Polygon(tile,center.distanceSquared(camera.x,camera.y,camera.z),tint,ChamberWaterMaterial.light(level,center,local)));
                    }
                }
            }
        }
        if(polygons.isEmpty())return;
        polygons.sort(Comparator.comparingDouble(Polygon::distance).reversed());
        var matrix=event.getPoseStack().last().pose();
        // Private buffer: never flush another mod's batches. RenderType restores all GPU state.
        try(var memory=new ByteBufferBuilder(65536)){
            var buffers=MultiBufferSource.immediate(memory);var vertices=buffers.getBuffer(ChamberWaterMaterial.SURFACE);
            var sprite=ChamberWaterMaterial.sprite(false);
            for(var polygon:polygons){var tile=polygon.tile;var points=tile.polygon();
                int color=polygon.tint,alpha=com.sealant.config.SealantClientConfig.value(com.sealant.config.SealantClientConfig.WATER_ALPHA,145);
                for(int i=1;i+1<points.size();i++)for(var p:List.of(points.getFirst(),points.get(i),points.get(i+1))){
                    float u=(float)Math.clamp(p.get(tile.u())-tile.originU(),0,1),v=(float)Math.clamp(p.get(tile.v())-tile.originV(),0,1);
                    vertices.addVertex(matrix,(float)(p.x-camera.x),(float)(p.y-camera.y),(float)(p.z-camera.z))
                            .setUv(sprite.getU(u),sprite.getV(v)).setColor((color>>16)&255,(color>>8)&255,color&255,alpha).setLight(polygon.light);
                }
            }
            buffers.endBatch(ChamberWaterMaterial.SURFACE);
        }
    }
    private ChamberWaterSurface(){}
}
