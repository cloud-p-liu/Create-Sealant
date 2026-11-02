package com.sealant.chamber.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.sealant.chamber.*;
import com.sealant.chamber.runtime.ChamberRuntime;
import com.sealant.config.SealantClientConfig;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.Tags;
import org.joml.Vector3d;
import java.util.*;

/** Visual skin on actual transparent hull shapes, never a world fluid/occlusion mutation.
 * Local geometry is cached independently of renderPose. Each actual exposed glass face
 * samples its own adjacent water: interior water stays on the interior glass surface.
 * Half-block panels bound coastline sampling error; water height clips each rotated panel.
 */
public final class ChamberGlassOverlay {
    private static final int MAX_BLOCKS=32768,MAX_PANELS=16384;
    private record Panel(ChamberWaterTiles.Tile tile,ChamberWaterTiles.Contact contact,boolean internal) {}
    private record Skin(List<AABB> domains,AABB bounds,List<Panel> panels,long expires) {}
    private record Patch(List<Vector3d> points,ChamberWaterTiles.Tile tile,Pose3d pose,double distance,int color,int light) {}
    private static final Map<UUID,Skin> cache=new HashMap<>();
    private static ClientLevel level;
    public static void clear(){cache.clear();level=null;}
    /** Block state updates invalidate only nearby owner geometry, not moving poses. */
    public static void blockChanged(BlockPos pos){cache.values().removeIf(s->s.bounds.inflate(1).contains(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5));}
    public static boolean visible(){return SealantClientConfig.value(SealantClientConfig.WATER_OVERLAY,true);}
    public static void setVisible(boolean value){SealantClientConfig.set(SealantClientConfig.WATER_OVERLAY,value);}
    public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var current=Minecraft.getInstance().level;
        if(level!=current){clear();level=current;}
        if(level==null || !visible() || !ChamberVisuals.enabled() || !ChamberRuntime.enabled(level))return;
        Map<UUID,ClientSubLevel> owners=new LinkedHashMap<>();Map<UUID,List<AABB>> domains=new LinkedHashMap<>();
        for(var c:ChamberRuntime.chambers(level))if(c.isAlive() && Sable.HELPER.getContainingClient(c) instanceof ClientSubLevel owner && !owner.isRemoved()){
            owners.put(owner.getUniqueId(),owner);domains.computeIfAbsent(owner.getUniqueId(),k->new ArrayList<>()).add(c.getBoundingBox());
        }
        cache.keySet().retainAll(owners.keySet());var camera=event.getCamera().getPosition();List<Patch> patches=new ArrayList<>();
        // Per-frame external-water probes are shared by all panels. Never load chunks for a visual.
        Map<Long,Double> water=new HashMap<>();
        for(var entry:owners.entrySet()){
            var boxes=domains.get(entry.getKey());boxes.sort(Comparator.comparingDouble((AABB b)->b.minX).thenComparingDouble(b->b.minY).thenComparingDouble(b->b.minZ));
            AABB bounds=boxes.getFirst();for(var b:boxes)bounds=bounds.minmax(b);
            var pose=new Pose3d(entry.getValue().renderPose());
            if(!event.getFrustum().isVisible(ChamberIntersection.worldSearchBounds(bounds.inflate(1),pose).toMojang()))continue;
            var skin=cache.get(entry.getKey());
            // Periodic fallback also catches chunk arrivals and upstream plot rebuilds without a block event.
            if(skin==null || !skin.domains.equals(boxes) || level.getGameTime()>=skin.expires){skin=build(boxes,bounds);cache.put(entry.getKey(),skin);}
            var localCamera=pose.transformPositionInverse(new Vector3d(camera.x,camera.y,camera.z));
            for(var panel:skin.panels){
                if(!panel.contact.visibleFrom(localCamera))continue;
                var waterPoint=pose.transformPosition(new Vector3d(panel.contact.probe()));
                var points=panel.tile.polygon().stream().map(p->pose.transformPosition(new Vector3d(p))).toList();
                if(panel.internal){
                    // Sample just ahead of THIS inner face, not across the glass thickness.
                    // The resulting geometry remains on the actual inner glass surface.
                    var normal=panel.contact.normal();
                    var shift=new Vector3d(normal).mul(new Vector3d(panel.contact.probe()).sub(panel.tile.polygon().getFirst()).dot(normal));
                    var sample=pose.transformPosition(new Vector3d(panel.tile.polygon().getFirst()).add(shift));
                    var offset=sample.sub(points.getFirst());
                    for(var cut:ChamberWaterSurface.clipWet(points,offset)){
                        patches.add(patch(cut,panel,pose,camera,waterPoint));
                        if(patches.size()>=MAX_PANELS)break;
                    }
                    if(patches.size()>=MAX_PANELS)break;
                    continue;
                }
                // This outer face samples only the root reservoir. A joined chamber is not sea.
                if(ChamberWaterSurface.wet(vec(waterPoint)).isPresent() || ChamberVisuals.masks(vec(waterPoint)))continue;
                double lo=points.stream().mapToDouble(p->p.y).min().orElse(0),hi=points.stream().mapToDouble(p->p.y).max().orElse(0);
                for(int y=(int)Math.floor(lo);y<=Math.floor(hi);y++){
                    var block=BlockPos.containing(waterPoint.x,y,waterPoint.z);
                    double height=water.computeIfAbsent(block.asLong(),key->{
                        if(!level.hasChunkAt(block))return Double.NaN;
                        var fluid=level.getFluidState(block);return fluid.is(FluidTags.WATER)?block.getY()+(double)fluid.getHeight(level,block):Double.NaN;
                    });
                    if(!Double.isFinite(height))continue;
                    var cut=ChamberWaterTiles.clip(ChamberWaterTiles.clip(points,1,y,true),1,height,false);
                    if(cut.size()<3 || ChamberWaterMesh.area(cut)<1e-10)continue;
                    patches.add(patch(cut,panel,pose,camera,waterPoint));
                    if(patches.size()>=MAX_PANELS)break;
                }
                if(patches.size()>=MAX_PANELS)break;
            }
            if(patches.size()>=MAX_PANELS)break;
        }
        if(patches.isEmpty())return;
        patches.sort(Comparator.comparingDouble(Patch::distance).reversed());var matrix=event.getPoseStack().last().pose();
        var sprite=ChamberWaterMaterial.sprite(true);
        try(var memory=new ByteBufferBuilder(65536)){
            var buffers=MultiBufferSource.immediate(memory);var vertices=buffers.getBuffer(ChamberWaterMaterial.OVERLAY);
            for(var patch:patches)for(int i=1;i+1<patch.points.size();i++)for(var p:List.of(patch.points.getFirst(),patch.points.get(i),patch.points.get(i+1))){
                var local=patch.pose.transformPositionInverse(new Vector3d(p));var tile=patch.tile;
                float u=(float)Math.clamp(local.get(tile.u())-tile.originU(),0,1),v=(float)Math.clamp(local.get(tile.v())-tile.originV(),0,1);
                vertices.addVertex(matrix,(float)(p.x-camera.x),(float)(p.y-camera.y),(float)(p.z-camera.z))
                        .setUv(sprite.getU(u),sprite.getV(v)).setColor((patch.color>>16)&255,(patch.color>>8)&255,patch.color&255,255).setLight(patch.light);
            }
            buffers.endBatch(ChamberWaterMaterial.OVERLAY);
        }
    }
    private static Patch patch(List<Vector3d> cut,Panel panel,Pose3d pose,Vec3 camera,Vector3d water){
        var center=new Vector3d();for(var p:cut)center.add(p);center.div(cut.size());
        return new Patch(cut,panel.tile,pose,center.distanceSquared(camera.x,camera.y,camera.z),
                ChamberWaterMaterial.tint(level,center),ChamberWaterMaterial.light(level,water,panel.contact.probe()));
    }
    private static Skin build(List<AABB> domains,AABB bounds){
        var scan=bounds.inflate(1);long count=(long)Math.ceil(scan.getXsize())*(long)Math.ceil(scan.getYsize())*(long)Math.ceil(scan.getZsize());
        if(count>MAX_BLOCKS)return new Skin(List.copyOf(domains),bounds,List.of(),level.getGameTime()+60);
        List<Panel> panels=new ArrayList<>();Map<Long,List<AABB>> shapes=new HashMap<>();boolean complete=true;
        for(var pos:BlockPos.betweenClosed(BlockPos.containing(scan.minX,scan.minY,scan.minZ),BlockPos.containing(scan.maxX,scan.maxY,scan.maxZ))){
            if(!level.hasChunkAt(pos)){complete=false;continue;}
            var state=level.getBlockState(pos);
            if(!(state.shouldDisplayFluidOverlay(level,pos,Fluids.WATER.defaultFluidState()) || state.is(Tags.Blocks.GLASS_PANES) || state.is(Tags.Blocks.GLASS_BLOCKS)))continue;
            if(domains.stream().noneMatch(b->b.inflate(1e-6).intersects(new AABB(pos))))continue;
            List<AABB> neighbors=new ArrayList<>();
            for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)neighbors.addAll(shapes(shapes,pos.offset(x,y,z)));
            for(var shape:shapes(shapes,pos))for(var face:ChamberWaterTiles.exposed(shape,neighbors)){
                var quad=ChamberWaterTiles.quad(face.rectangle(),face.axis(),face.sign());
                int u=(face.axis()+1)%3,v=(face.axis()+2)%3;
                // Small panels resolve shores and dry/wet transitions without a per-pixel world lookup.
                for(int a=0;a<2;a++)for(int b=0;b<2;b++){
                    double midU=(ChamberWaterTiles.min(face.rectangle(),u)+ChamberWaterTiles.max(face.rectangle(),u))*.5;
                    double midV=(ChamberWaterTiles.min(face.rectangle(),v)+ChamberWaterTiles.max(face.rectangle(),v))*.5;
                    var part=ChamberWaterTiles.clip(ChamberWaterTiles.clip(quad,u,midU,a==1),v,midV,b==1);
                    if(part.size()<3 || ChamberWaterMesh.area(part)<1e-10)continue;
                    var contact=ChamberWaterTiles.contact(face,part);var probe=contact.probe();
                    if(occupied(shapes,probe))continue;
                    boolean internal=domains.stream().anyMatch(d->d.contains(probe.x,probe.y,probe.z));
                    if(!internal){
                        // Exterior hull membership only. Never use this across-wall point
                        // to position or clip the internal-water overlay.
                        var behind=new Vector3d(probe).fma(-face.thickness()-.008,contact.normal());
                        for(int step=0;step<32 && occupied(shapes,behind);step++)behind.fma(-.25,contact.normal());
                        if(occupied(shapes,behind) || domains.stream().noneMatch(d->d.contains(behind.x,behind.y,behind.z)))continue;
                    }
                    for(var tile:ChamberWaterTiles.tiles(contact.polygon()))panels.add(new Panel(tile,contact,internal));
                    if(panels.size()>MAX_PANELS)return new Skin(List.copyOf(domains),bounds,List.of(),level.getGameTime()+60);
                }
            }
        }
        return new Skin(List.copyOf(domains),bounds,List.copyOf(panels),level.getGameTime()+(complete?60:5));
    }
    private static List<AABB> shapes(Map<Long,List<AABB>> cache,BlockPos pos){
        return cache.computeIfAbsent(pos.asLong(),key->{
            if(!level.hasChunkAt(pos))return List.of();
            return level.getBlockState(pos).getShape(level,pos).toAabbs().stream().map(b->b.move(pos)).toList();
        });
    }
    private static boolean occupied(Map<Long,List<AABB>> shapes,Vector3d p){return shapes(shapes,BlockPos.containing(p.x,p.y,p.z)).stream().anyMatch(b->b.contains(p.x,p.y,p.z));}
    private static Vec3 vec(Vector3d p){return new Vec3(p.x,p.y,p.z);}
    private ChamberGlassOverlay(){}
}
