package com.sealant.chamber;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import java.util.*;

/** Horizontal plane / transformed fixed-box intersection. Does not mutate boxes or poses. */
public final class ChamberWaterGeometry {
    public static List<Vector3d> slice(AABB box,Pose3dc pose,double height){return slice(box,pose,height,false);}
    /** Solid boundaries are closed: a water plane coincident with a slab top must be removed too. */
    public static List<Vector3d> solidSlice(AABB box,Pose3dc pose,double height){return slice(box,pose,height,true);}
    private static List<Vector3d> slice(AABB box,Pose3dc pose,double height,boolean closed){
        Vector3d[] corners=new Vector3d[8];double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(int i=0;i<8;i++){
            corners[i]=pose.transformPosition(new Vector3d((i&1)==0?box.minX:box.maxX,(i&2)==0?box.minY:box.maxY,(i&4)==0?box.minZ:box.maxZ));
            min=Math.min(min,corners[i].y);max=Math.max(max,corners[i].y);
        }
        // Upper face excluded so two vertically adjacent pieces never draw the same plane twice.
        if(height<min-1e-8 || (closed?height>max+1e-8:height>=max-1e-8))return List.of();
        List<Vector3d> result=new ArrayList<>(6);
        for(int i=0;i<8;i++)for(int bit:new int[]{1,2,4})if((i&bit)==0){
            var a=corners[i];var b=corners[i|bit];double dy=b.y-a.y;
            if(Math.abs(dy)<1e-12){if(Math.abs(a.y-height)<1e-8){add(result,new Vector3d(a.x,height,a.z));add(result,new Vector3d(b.x,height,b.z));}continue;}
            double t=(height-a.y)/dy;
            if(t>=-1e-9 && t<=1+1e-9)add(result,new Vector3d(a).lerp(b,Math.clamp(t,0,1)).setComponent(1,height));
        }
        if(result.size()<3)return List.of();
        double x=0,z=0;for(var p:result){x+=p.x;z+=p.z;}final double cx=x/result.size(),cz=z/result.size();
        result.sort(Comparator.comparingDouble(p->Math.atan2(p.z-cz,p.x-cx)));
        return result;
    }
    /** Convex polygon difference in world XZ. Output pieces are convex and non-overlapping;
     * no triangulation covers the removed hole (unlike simply deleting interior vertices). */
    public static List<List<Vector3d>> subtract(List<Vector3d> water,List<Vector3d> solid){
        if(water.size()<3)return List.of();
        if(solid.size()<3)return List.of(water);
        List<List<Vector3d>> outside=new ArrayList<>();List<Vector3d> remainder=water;
        for(int i=0;i<solid.size() && remainder.size()>=3;i++){
            var a=solid.get(i);var b=solid.get((i+1)%solid.size());
            var part=halfPlane(remainder,a,b,false);
            if(area(part)>1e-10)outside.add(part);
            remainder=halfPlane(remainder,a,b,true);
        }
        return outside;
    }
    private static List<Vector3d> halfPlane(List<Vector3d> polygon,Vector3d a,Vector3d b,boolean inside){
        List<Vector3d> result=new ArrayList<>();
        for(int i=0;i<polygon.size();i++){
            var p=polygon.get(i);var q=polygon.get((i+1)%polygon.size());
            double d=(b.x-a.x)*(p.z-a.z)-(b.z-a.z)*(p.x-a.x);
            double e=(b.x-a.x)*(q.z-a.z)-(b.z-a.z)*(q.x-a.x);
            boolean pin=inside?d>=0:d<=0,qin=inside?e>=0:e<=0;
            if(pin)add(result,new Vector3d(p));
            if(pin!=qin && Math.abs(d-e)>1e-15)add(result,new Vector3d(p).lerp(q,d/(d-e)));
        }
        return result;
    }
    public static double area(List<Vector3d> polygon){
        if(polygon.size()<3)return 0;double area=0;var origin=polygon.getFirst();
        for(int i=1;i+1<polygon.size();i++){
            var a=polygon.get(i);var b=polygon.get(i+1);
            area+=(a.x-origin.x)*(b.z-origin.z)-(a.z-origin.z)*(b.x-origin.x);
        }
        return Math.abs(area)*.5;
    }
    private static void add(List<Vector3d> points,Vector3d p){for(var q:points)if(q.distanceSquared(p)<1e-14)return;points.add(p);}
    private ChamberWaterGeometry(){}
}
