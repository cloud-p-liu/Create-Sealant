package com.sealant.chamber.client;

import com.sealant.chamber.ChamberIntersection;
import com.sealant.chamber.ChamberWaterGeometry;
import com.sealant.chamber.ChamberWaterMesh;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import java.util.*;

/** Real collision boxes, including transparent glass and partial blocks. Client rendering only. */
final class ChamberWaterSolids {
    private record Solid(AABB box,ClientSubLevel body) {}
    private record Cache(long tick,List<Solid> solids,boolean complete) {}
    record Result(List<List<Vector3d>> polygons,boolean complete) {}
    private static final int MAX_BLOCKS=32768,MAX_SOLIDS=32768,MAX_POLYGONS=8192;
    private static final Map<UUID,Cache> cache=new HashMap<>();
    private static ClientLevel cachedLevel;
    static void clear(){cache.clear();cachedLevel=null;}
    static void retain(Set<UUID> groups){cache.keySet().retainAll(groups);}
    static Result clip(ClientLevel level,UUID group,AABB world,List<Vector3d> water){
        if(level!=cachedLevel){clear();cachedLevel=level;}
        var data=cache.get(group);
        // Cache block shapes for one tick, but transform moving bodies at the current render pose.
        if(data==null || data.tick!=level.getGameTime()){data=collect(level,world.inflate(1));cache.put(group,data);}
        if(!data.complete)return new Result(List.of(),false);
        List<List<Vector3d>> polygons=new ArrayList<>(List.of(water));
        Map<ClientSubLevel,Pose3d> poses=new IdentityHashMap<>();var identity=new Pose3d();
        for(var solid:data.solids){
            var pose=solid.body==null?identity:poses.computeIfAbsent(solid.body,b->new Pose3d(b.renderPose()));
            var bounds=ChamberIntersection.worldSearchBounds(solid.box,pose).toMojang();
            List<List<Vector3d>> next=new ArrayList<>();
            for(var polygon:polygons){
                if(disjoint(polygon,bounds)){next.add(polygon);continue;}
                next.addAll(ChamberWaterMesh.subtractBox(polygon,solid.box,pose));
                if(next.size()>MAX_POLYGONS)return new Result(List.of(),false);
            }
            polygons=next;if(polygons.isEmpty())break;
        }
        return new Result(polygons,true);
    }
    private static boolean disjoint(List<Vector3d> points,AABB bounds){
        double x=Double.POSITIVE_INFINITY,y=x,z=x,X=Double.NEGATIVE_INFINITY,Y=X,Z=X;
        for(var p:points){x=Math.min(x,p.x);y=Math.min(y,p.y);z=Math.min(z,p.z);X=Math.max(X,p.x);Y=Math.max(Y,p.y);Z=Math.max(Z,p.z);}
        return X<bounds.minX-1e-8 || x>bounds.maxX+1e-8 || Y<bounds.minY-1e-8 || y>bounds.maxY+1e-8 || Z<bounds.minZ-1e-8 || z>bounds.maxZ+1e-8;
    }
    private static Cache collect(ClientLevel level,AABB world){
        List<Solid> result=new ArrayList<>();int[] budget={MAX_BLOCKS};
        if(!read(level,world,null,result,budget))return new Cache(level.getGameTime(),List.of(),false);
        for(var sub:Sable.HELPER.getAllIntersecting(level,new dev.ryanhcode.sable.companion.math.BoundingBox3d(world.minX,world.minY,world.minZ,world.maxX,world.maxY,world.maxZ)))
            if(sub instanceof ClientSubLevel body && !body.isRemoved()){
                var pose=new Pose3d(body.renderPose());
                // Transform eight search corners to the other body's plot frame; final clipping is exact.
                double x=Double.POSITIVE_INFINITY,y=x,z=x,X=Double.NEGATIVE_INFINITY,Y=X,Z=X;
                for(int i=0;i<8;i++){
                    var p=pose.transformPositionInverse(new Vector3d((i&1)==0?world.minX:world.maxX,(i&2)==0?world.minY:world.maxY,(i&4)==0?world.minZ:world.maxZ));
                    x=Math.min(x,p.x);y=Math.min(y,p.y);z=Math.min(z,p.z);X=Math.max(X,p.x);Y=Math.max(Y,p.y);Z=Math.max(Z,p.z);
                }
                var plot=body.getPlot().getBoundingBox();if(plot==null)continue;
                x=Math.max(x,plot.minX());y=Math.max(y,plot.minY());z=Math.max(z,plot.minZ());
                X=Math.min(X,plot.maxX()+1.);Y=Math.min(Y,plot.maxY()+1.);Z=Math.min(Z,plot.maxZ()+1.);
                if(x>=X || y>=Y || z>=Z)continue;
                AABB search=new AABB(x,y,z,X,Y,Z);
                if(!read(level,search,body,result,budget))return new Cache(level.getGameTime(),List.of(),false);
            }
        return new Cache(level.getGameTime(),List.copyOf(result),true);
    }
    private static boolean read(ClientLevel level,AABB search,ClientSubLevel body,List<Solid> result,int[] budget){
        int minX=(int)Math.floor(search.minX),minY=Math.max(level.getMinBuildHeight(),(int)Math.floor(search.minY)),minZ=(int)Math.floor(search.minZ);
        int maxX=(int)Math.floor(search.maxX),maxY=Math.min(level.getMaxBuildHeight()-1,(int)Math.floor(search.maxY)),maxZ=(int)Math.floor(search.maxZ);
        if(maxX<minX || maxY<minY || maxZ<minZ)return true;
        long count=(long)(maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1);
        if(count<0 || count>budget[0])return false;budget[0]-=(int)count;
        var pos=new BlockPos.MutableBlockPos();
        for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++){
            pos.set(x,y,z);if(!level.hasChunkAt(pos))return false;
            var state=level.getBlockState(pos);if(state.isAir())continue;
            for(var box:state.getCollisionShape(level,pos).toAabbs()){
                result.add(new Solid(box.move(x,y,z),body));if(result.size()>MAX_SOLIDS)return false;
            }
        }
        return true;
    }
    private ChamberWaterSolids(){}
}
