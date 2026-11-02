package com.sealant.chamber.physics;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import java.util.*;

/** Exact volume/centroid of an oriented box clipped below one horizontal water plane.
 * Only a cheap boundary input is sampled; no internal water state or leak solver exists here.
 * Integrate around the box center to avoid cancellation at distant Sable plot coordinates.
 */
public final class ChamberBuoyancyVolume {
    public record Submerged(double volume,Vector3d center) {}
    private static final int[][] FACES={{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}};
    public static double halfHeight(AABB box,Pose3dc pose){
        var up=pose.orientation().transformInverse(new Vector3d(0,1,0));
        return (Math.abs(up.x)*box.getXsize()+Math.abs(up.y)*box.getYsize()+Math.abs(up.z)*box.getZsize())/2;
    }
    public static Submerged below(AABB box,Pose3dc pose,double waterY){
        var c=box.getCenter();var center=pose.transformPosition(new Vector3d(c.x,c.y,c.z));
        double half=halfHeight(box,pose),h=waterY-center.y;
        double whole=box.getXsize()*box.getYsize()*box.getZsize();
        if(h<=-half)return new Submerged(0,center);
        if(h>=half)return new Submerged(whole,center);
        Vector3d[] vertices=new Vector3d[8];
        for(int i=0;i<8;i++)vertices[i]=pose.orientation().transform(new Vector3d(
                (i&4)==0?-box.getXsize()/2:box.getXsize()/2,
                (i&2)==0?-box.getYsize()/2:box.getYsize()/2,
                (i&1)==0?-box.getZsize()/2:box.getZsize()/2));
        List<List<Vector3d>> faces=new ArrayList<>();List<Vector3d> cap=new ArrayList<>();
        for(int[] face:FACES){
            List<Vector3d> out=new ArrayList<>();
            for(int i=0;i<4;i++){
                var a=vertices[face[i]];var b=vertices[face[(i+1)%4]];
                boolean inA=a.y<=h,inB=b.y<=h;
                if(inA)out.add(a);
                if(inA!=inB){
                    var p=new Vector3d(a).lerp(b,(h-a.y)/(b.y-a.y));out.add(p);
                    if(cap.stream().noneMatch(old->old.distanceSquared(p)<1e-20))cap.add(p);
                }
            }
            if(out.size()>=3)faces.add(out);
        }
        if(cap.size()>=3){
            var average=new Vector3d();for(var p:cap)average.add(p);average.div(cap.size());
            cap.sort(Comparator.comparingDouble(p->Math.atan2(p.z-average.z,p.x-average.x)));faces.add(cap);
        }
        var interior=new Vector3d();int count=0;
        for(var face:faces)for(var p:face){interior.add(p);count++;}
        if(count==0)return new Submerged(0,center);
        interior.div(count);double volume=0;var moment=new Vector3d();
        for(var face:faces){var a=face.getFirst();for(int i=1;i+1<face.size();i++){
            var b=face.get(i);var d=face.get(i+1);
            double v=Math.abs(new Vector3d(a).sub(interior).dot(new Vector3d(b).sub(interior).cross(new Vector3d(d).sub(interior))))/6;
            volume+=v;moment.fma(v/4,new Vector3d(interior).add(a).add(b).add(d));
        }}
        if(volume<1e-14)return new Submerged(0,center);
        return new Submerged(Math.min(whole,volume),moment.div(volume).add(center));
    }
    private ChamberBuoyancyVolume(){}
}
