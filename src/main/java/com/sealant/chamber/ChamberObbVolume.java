package com.sealant.chamber;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import java.util.*;

/** Convex clipping of two immutable plot-space OBBs. No SAT displacement, forces, or resize.
 * Work in the first box's centered orthonormal frame to avoid far-plot cancellation.
 * Face winding is immaterial: integrate positive tetrahedra around an interior point.
 */
public final class ChamberObbVolume {
    public record Result(double volume,Vector3d centroid) {} // No centroid for zero-volume contact.
    private static final int[][] FACES={{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}};
    public static double boxVolume(AABB box,Pose3dc pose){
        return box.getXsize()*box.getYsize()*box.getZsize()*Math.abs(pose.scale().x()*pose.scale().y()*pose.scale().z());
    }
    public static Result intersect(AABB first,Pose3dc a,AABB second,Pose3dc b){
        double va=boxVolume(first,a),vb=boxVolume(second,b);
        if(!Double.isFinite(va+vb) || va<=0 || vb<=0)return new Result(0,null);
        var ac=first.getCenter();var bc=second.getCenter();
        var origin=a.transformPosition(new Vector3d(ac.x,ac.y,ac.z));
        var offset=b.transformPosition(new Vector3d(bc.x,bc.y,bc.z)).sub(origin);
        var ah=new Vector3d(first.getXsize(),first.getYsize(),first.getZsize()).mul(a.scale()).absolute().mul(.5);
        var bh=new Vector3d(second.getXsize(),second.getYsize(),second.getZsize()).mul(b.scale()).absolute().mul(.5);
        if(!origin.isFinite() || !offset.isFinite())return new Result(0,null);
        double scale=Math.max(1,Math.max(ah.length(),bh.length())),eps=1e-10*scale;
        Vector3d[] vertices=new Vector3d[8];
        for(int i=0;i<8;i++){
            var v=b.orientation().transform(new Vector3d((i&4)==0?-bh.x:bh.x,(i&2)==0?-bh.y:bh.y,(i&1)==0?-bh.z:bh.z)).add(offset);
            vertices[i]=a.orientation().transformInverse(v);
        }
        List<List<Vector3d>> faces=new ArrayList<>();
        for(var face:FACES){var polygon=new ArrayList<Vector3d>();for(int index:face)polygon.add(vertices[index]);faces.add(polygon);}
        for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1}){
            List<List<Vector3d>> clipped=new ArrayList<>();List<Vector3d> cap=new ArrayList<>();double plane=ah.get(axis);
            for(var face:faces){
                List<Vector3d> polygon=new ArrayList<>();
                for(int i=0;i<face.size();i++){
                    var p=face.get(i);var q=face.get((i+1)%face.size());
                    double dp=sign*p.get(axis)-plane,dq=sign*q.get(axis)-plane;
                    boolean insideP=dp<=0,insideQ=dq<=0;
                    if(insideP)polygon.add(p);
                    if(insideP!=insideQ){
                        var cut=new Vector3d(p).lerp(q,Math.clamp(dp/(dp-dq),0,1));cut.setComponent(axis,sign*plane);
                        polygon.add(cut);
                        if(cap.stream().noneMatch(old->old.distanceSquared(cut)<eps*eps))cap.add(cut);
                    }
                }
                if(polygon.size()>=3)clipped.add(polygon);
            }
            if(cap.size()>=3){
                int u=(axis+1)%3,v=(axis+2)%3;var center=new Vector3d();for(var p:cap)center.add(p);center.div(cap.size());
                cap.sort(Comparator.comparingDouble(p->Math.atan2(p.get(v)-center.get(v),p.get(u)-center.get(u))));clipped.add(cap);
            }
            faces=clipped;if(faces.isEmpty())return new Result(0,null);
        }
        var interior=new Vector3d();int count=0;
        for(var face:faces)for(var p:face){interior.add(p);count++;}
        if(count==0)return new Result(0,null);interior.div(count);
        double volume=0;var moment=new Vector3d();
        for(var face:faces){var p=face.getFirst();for(int i=1;i+1<face.size();i++){
            var q=face.get(i);var r=face.get(i+1);
            double tetra=Math.abs(new Vector3d(p).sub(interior).dot(new Vector3d(q).sub(interior).cross(new Vector3d(r).sub(interior))))/6;
            volume+=tetra;moment.fma(tetra/4,new Vector3d(interior).add(p).add(q).add(r));
        }}
        double bound=Math.min(va,vb);
        if(!Double.isFinite(volume) || volume<=bound*1e-12)return new Result(0,null);
        var centroid=a.orientation().transform(moment.div(volume)).add(origin);
        return new Result(Math.min(bound,volume),centroid);
    }
    public static double fraction(double intersection,double bodyVolume){
        return Double.isFinite(intersection) && Double.isFinite(bodyVolume) && bodyVolume>0?Math.clamp(intersection/bodyVolume,0,1):0;
    }
    /** Full entry / full exit hysteresis, with fixed one-percent volume tolerance.
     * Intermediate overlap preserves the previous takeover state. */
    public static boolean takeover(double volume,double bodyVolume,boolean held){
        if(!Double.isFinite(volume) || !Double.isFinite(bodyVolume) || volume<=0 || bodyVolume<=0)return false;
        double overlap=fraction(volume,bodyVolume);
        return held?overlap>.01+1e-9:overlap+1e-9>=.99;
    }
    private ChamberObbVolume(){}
}
