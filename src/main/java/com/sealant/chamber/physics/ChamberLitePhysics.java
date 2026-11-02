package com.sealant.chamber.physics;

import com.sealant.chamber.ChamberEntity;
import com.sealant.chamber.runtime.ChamberRuntime;
import com.sealant.chamber.runtime.ChamberMetrics;
import com.sealant.chamber.topology.*;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import java.util.*;

/** Sealed geometric displacement, deliberately independent from Field V3 inventories.
 * Chamber solids/air together displace their union volume. Loads go to the carrier;
 * contained bodies exchange load with it through normal collision, not duplicate buoyancy.
 */
final class ChamberLitePhysics {
    record Force(ServerSubLevel body,Vector3d point,double volume,double gravity,double drag) {}
    static final class Domain {
        final ChamberTopology.Group group;final List<AABB> samples;
        ServerSubLevel owner;double wet,force;String status="等待計算";
        Domain(ChamberTopology.Group group){this.group=group;this.samples=subdivide(group.union().pieces);}
    }
    private List<ChamberTopology.Node> signature=List.of();
    final List<Domain> domains=new ArrayList<>();final List<Force> forces=new ArrayList<>();
    static List<ChamberTopology.Node> nodes(ServerLevel level){
        var result=new ArrayList<ChamberTopology.Node>();
        for(ChamberEntity marker:ChamberRuntime.chambers(level))
            if(marker.isAlive() && Sable.HELPER.getContaining(marker) instanceof ServerSubLevel owner && !owner.isRemoved())
                result.add(new ChamberTopology.Node(marker.getUUID(),owner.getUniqueId(),marker.getBoundingBox()));
        result.sort(Comparator.comparing(ChamberTopology.Node::id));return result;
    }
    void prepare(ServerLevel level){
        long timing=ChamberMetrics.begin(level);forces.clear();
        try {
            var nodes=nodes(level);
            if(!nodes.equals(signature)){
                domains.clear();for(var group:ChamberTopology.build(nodes))domains.add(new Domain(group));signature=List.copyOf(nodes);
                ChamberMetrics.count(level,"lite_topology_rebuilds",1);
            }
            var external=ChamberPhysics.externalWater(level);long readsBefore=external.reads;
            for(var domain:domains){
                domain.wet=0;domain.force=0;domain.owner=null;
                // Sable's broad phase is not needed: the marker retains its exact carrier.
                for(var marker:ChamberRuntime.chambers(level))if(marker.getUUID().equals(domain.group.members().getFirst().id())
                        && Sable.HELPER.getContaining(marker) instanceof ServerSubLevel body){domain.owner=body;break;}
                var owner=domain.owner;if(owner==null || owner.isRemoved()){domain.status="owner未載入";continue;}
                var pose=new Pose3d(owner.logicalPose());var gravity=DimensionPhysicsData.getGravity(level,pose.position());
                if(gravity.y>=-1e-6 || Math.abs(gravity.x)+Math.abs(gravity.z)>1e-6 || pose.scale().distanceSquared(new Vector3d(1,1,1))>1e-10){domain.status="僅支援垂直重力／單位縮放";continue;}
                var pending=new ArrayList<Force>();boolean valid=true;
                for(var box:domain.samples){
                    var c=box.getCenter();var world=pose.transformPosition(new Vector3d(c.x,c.y,c.z));
                    double bottom=world.y-ChamberBuoyancyVolume.halfHeight(box,pose);
                    // Probe just inside the geometric bottom; a dry center must not hide partial immersion.
                    double probe=bottom+1e-7;double depth=external.depth(new Vector3d(world.x,probe,world.z));
                    if(!Double.isFinite(depth)){valid=false;break;}
                    if(depth<=0)continue;
                    var wet=ChamberBuoyancyVolume.below(box,pose,probe+depth);
                    if(wet.volume()<=1e-12)continue;
                    double length=Math.max(.5,Math.cbrt(ChamberUnion.volume(box)));
                    pending.add(new Force(owner,wet.center(),wet.volume(),-gravity.y,.85*wet.volume()/length));
                }
                if(!valid){domain.status="外水區塊未載入；暫停施力";continue;}
                forces.addAll(pending);for(var f:pending){domain.wet+=f.volume;domain.force+=ChamberFluid.DENSITY*f.gravity*f.volume;}
                domain.status="輕量浮力運行中（視為密封／不計漏水）";
                ChamberMetrics.count(level,"lite_samples",domain.samples.size());
            }
            ChamberMetrics.count(level,"lite_world_reads",external.reads-readsBefore);
        }finally{ChamberMetrics.end(level,"chamber_lite",timing);}
    }
    /** At most 4096 boxes per geometric group; never omit a union piece or count overlap twice. */
    static List<AABB> subdivide(List<ChamberUnion.Piece> pieces){
        int limit=Math.clamp((int)Math.floor(Math.cbrt(4096./Math.max(1,pieces.size()))),1,4);
        List<AABB> result=new ArrayList<>();
        for(var piece:pieces){var b=piece.box();
            int minimum=Math.min(2,limit);
            int nx=Math.clamp((int)Math.ceil(b.getXsize()/2),minimum,limit),ny=Math.clamp((int)Math.ceil(b.getYsize()/2),minimum,limit),nz=Math.clamp((int)Math.ceil(b.getZsize()/2),minimum,limit);
            double dx=b.getXsize()/nx,dy=b.getYsize()/ny,dz=b.getZsize()/nz;
            for(int x=0;x<nx;x++)for(int y=0;y<ny;y++)for(int z=0;z<nz;z++)result.add(new AABB(
                    b.minX+x*dx,b.minY+y*dy,b.minZ+z*dz,b.minX+(x+1)*dx,b.minY+(y+1)*dy,b.minZ+(z+1)*dz));
        }return List.copyOf(result);
    }
    List<String> report(UUID id){
        List<String> lines=new ArrayList<>();
        for(var domain:domains){
            if(id!=null && domain.group.members().stream().noneMatch(n->n.id().equals(id)))continue;
            lines.add(String.format(Locale.ROOT,"group %s：%s；%d chamber／%d 排水取樣；聯集 %.4f，浸水 %.4f 格³；浮力 Y=%.3f",
                    domain.group.id(),domain.status,domain.group.members().size(),domain.samples.size(),domain.group.union().volume,domain.wet,domain.force));
            if(lines.size()>=24)break;
        }
        if(lines.isEmpty())lines.add("輕量浮力：尚無已計算氣室；確認auto與chamber physics已開啟。");
        lines.add("不計算漏水／氣壓／逐格交換；完整流體存檔暫存保留，輕量模式內部視為乾燥。");return lines;
    }
}
