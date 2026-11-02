package com.sealant.chamber;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import java.util.*;

/** Exact convex entity/water contact. This never estimates chamber capacity or writes W/G.
 * Work in the entity's centered orthonormal frame, even at far Sable plot coordinates. */
public final class ChamberFluidContact {
    public record Domain(AABB box,Pose3dc pose) {}
    private record Plane(Vector3d normal,double offset) {}
    private static final int[][] FACES={{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}};
    private static List<List<Vector3d>> clip(List<List<Vector3d>> faces,Plane plane){
        List<List<Vector3d>> out=new ArrayList<>();List<Vector3d> cap=new ArrayList<>();
        for(var face:faces){var polygon=new ArrayList<Vector3d>();
            for(int i=0;i<face.size();i++){
                var a=face.get(i);var b=face.get((i+1)%face.size());double da=plane.normal.dot(a)-plane.offset,db=plane.normal.dot(b)-plane.offset;
                if(da<=0)polygon.add(a);
                if((da<=0)!=(db<=0)){
                    var p=new Vector3d(a).lerp(b,Math.clamp(da/(da-db),0,1));polygon.add(p);
                    if(cap.stream().noneMatch(q->q.distanceSquared(p)<1e-20))cap.add(p);
                }
            }if(polygon.size()>=3)out.add(polygon);
        }
        if(cap.size()>=3){var center=new Vector3d();for(var p:cap)center.add(p);center.div(cap.size());
            var n=plane.normal;var axis=Math.abs(n.x)<.8?new Vector3d(1,0,0):new Vector3d(0,1,0);
            var u=new Vector3d(n).cross(axis).normalize();var v=new Vector3d(n).cross(u);
            cap.sort(Comparator.comparingDouble(p->Math.atan2(new Vector3d(p).sub(center).dot(v),new Vector3d(p).sub(center).dot(u))));out.add(cap);
        }return out;
    }
    private static List<Plane> planes(Domain domain,Pose3dc body,Vector3d origin){
        var center=domain.box.getCenter();var offset=domain.pose.transformPosition(new Vector3d(center.x,center.y,center.z)).sub(origin);
        body.orientation().transformInverse(offset);
        var half=new Vector3d(domain.box.getXsize(),domain.box.getYsize(),domain.box.getZsize()).mul(domain.pose.scale()).absolute().mul(.5);
        List<Plane> planes=new ArrayList<>();
        for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1}){
            var n=domain.pose.orientation().transform(new Vector3d().setComponent(axis,sign));body.orientation().transformInverse(n);
            planes.add(new Plane(n,n.dot(offset)+half.get(axis)));
        }return planes;
    }
    private static boolean positive(List<List<Vector3d>> faces){
        var center=new Vector3d();int count=0;for(var f:faces)for(var p:f){center.add(p);count++;}
        if(count==0)return false;center.div(count);double volume=0;
        for(var f:faces)for(int i=1;i+1<f.size();i++)volume+=Math.abs(new Vector3d(f.getFirst()).sub(center).dot(new Vector3d(f.get(i)).sub(center).cross(new Vector3d(f.get(i+1)).sub(center))))/6;
        return volume>1e-12;
    }
    /** Highest contact above entity-local feet. Excluded domains remove root external water. */
    public static double height(AABB bodyBox,Pose3dc body,Domain water,double surfaceWorldY,List<Domain> excluded){
        var c=bodyBox.getCenter();var origin=body.transformPosition(new Vector3d(c.x,c.y,c.z));
        var half=new Vector3d(bodyBox.getXsize(),bodyBox.getYsize(),bodyBox.getZsize()).mul(body.scale()).absolute().mul(.5);
        Vector3d[] vertices=new Vector3d[8];for(int i=0;i<8;i++)vertices[i]=new Vector3d((i&4)==0?-half.x:half.x,(i&2)==0?-half.y:half.y,(i&1)==0?-half.z:half.z);
        List<List<Vector3d>> shape=new ArrayList<>();for(var f:FACES){var polygon=new ArrayList<Vector3d>();for(int i:f)polygon.add(vertices[i]);shape.add(polygon);}
        for(var plane:planes(water,body,origin)){shape=clip(shape,plane);if(shape.isEmpty())return 0;}
        var up=body.orientation().transformInverse(new Vector3d(0,1,0));shape=clip(shape,new Plane(up,surfaceWorldY-origin.y));
        if(!positive(shape))return 0;
        List<List<List<Vector3d>>> pieces=new ArrayList<>();pieces.add(shape);
        for(var domain:excluded){
            List<List<List<Vector3d>>> next=new ArrayList<>();var planes=planes(domain,body,origin);
            for(var piece:pieces){var remaining=piece;
                for(var plane:planes){
                    var outside=clip(remaining,new Plane(new Vector3d(plane.normal).negate(),-plane.offset));
                    if(positive(outside))next.add(outside);
                    remaining=clip(remaining,plane);if(!positive(remaining))break;
                }
            }pieces=next;if(pieces.isEmpty())return 0;
            if(pieces.size()>256)return 0; // Never invent a bounding-box water region on budget failure.
        }
        double highest=-half.y;for(var piece:pieces)for(var f:piece)for(var p:f)highest=Math.max(highest,p.y);
        return Math.clamp((highest+half.y)/Math.abs(body.scale().y()),0,bodyBox.getYsize());
    }
    public static boolean contains(Domain domain,Vector3d point){var p=domain.pose.transformPositionInverse(new Vector3d(point));return domain.box.contains(p.x,p.y,p.z);}
    public static boolean covers(Domain domain,AABB bodyBox,Pose3dc body){
        for(int i=0;i<8;i++){
            var p=body.transformPosition(new Vector3d((i&4)==0?bodyBox.minX:bodyBox.maxX,(i&2)==0?bodyBox.minY:bodyBox.maxY,(i&1)==0?bodyBox.minZ:bodyBox.maxZ));
            var local=domain.pose.transformPositionInverse(p);var b=domain.box;
            if(local.x<b.minX-1e-9||local.x>b.maxX+1e-9||local.y<b.minY-1e-9||local.y>b.maxY+1e-9||local.z<b.minZ-1e-9||local.z>b.maxZ+1e-9)return false;
        }return true;
    }
    private ChamberFluidContact(){}
}
