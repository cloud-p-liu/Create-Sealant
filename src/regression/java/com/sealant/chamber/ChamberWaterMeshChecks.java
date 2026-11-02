package com.sealant.chamber;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import java.util.*;

public final class ChamberWaterMeshChecks {
    public static void run(){
        var pose=new Pose3d();var left=new AABB(0,0,0,1,1,1);var right=new AABB(1,0,0,2,1,1);
        var flat=ChamberWaterMesh.build(List.of(new ChamberWaterMesh.Cell(left,.4),new ChamberWaterMesh.Cell(right,.4)),pose);
        near(area(flat),2,"Shared-vertex flat mesh keeps surface area");
        for(var t:flat)for(var p:t.points())near(p.y,.4,"Flat interface stays at authoritative level");
        seam(flat);
        var wave=ChamberWaterMesh.build(List.of(new ChamberWaterMesh.Cell(left,.2),new ChamberWaterMesh.Cell(right,.8)),pose);
        seam(wave);require(!wave.isEmpty(),"Different cell states reconstruct a continuous visible interface");
        require(ChamberWaterMesh.build(List.of(new ChamberWaterMesh.Cell(left,0)),pose).isEmpty(),"Dry cell creates no water sheet");
        var column=ChamberWaterMesh.build(List.of(new ChamberWaterMesh.Cell(left,1),new ChamberWaterMesh.Cell(left.move(0,1,0),2)),pose);
        near(area(column),1,"Filled column draws only exposed top");for(var t:column)for(var p:t.points())near(p.y,2,"No internal water-layer sheets");
        var tilted=new Pose3d();tilted.orientation().rotateXYZ(.4,.7,-.2);
        var mesh=ChamberWaterMesh.build(List.of(new ChamberWaterMesh.Cell(left,.4)),tilted);
        require(!mesh.isEmpty(),"Tilted cell has an interface");for(var t:mesh)for(var p:t.points())near(p.y,.4,"Rotated-domain interface uses world height");
        List<Vector3d> polygon=List.of(new Vector3d(-2,-1,-2),new Vector3d(2,1,-2),new Vector3d(2,1,2),new Vector3d(-2,-1,2));
        var obstacle=new AABB(-.5,-.5,-.5,.5,.5,.5);
        var clipped=ChamberWaterMesh.subtractBox(polygon,obstacle,pose);
        near(clipped.stream().mapToDouble(ChamberWaterMesh::area).sum(),15*Math.sqrt(1.25),"Sloped wave mesh subtracts real 3D solid volume");
        var top=List.of(new Vector3d(0,0,0),new Vector3d(1,0,0),new Vector3d(1,0,1),new Vector3d(0,0,1));
        require(ChamberWaterMesh.subtractBox(top,new AABB(0,-1,0,1,0,1),pose).isEmpty(),"Coplanar slab top is not painted with water");
        System.out.println("Chamber shared-vertex water interface, seam and 3D collision clipping checks passed.");
    }
    private static void seam(List<ChamberWaterMesh.Triangle> mesh){
        Map<String,Integer> count=new HashMap<>();int seamEdges=0;
        for(var t:mesh){var points=t.points();for(int i=0;i<3;i++){
            var a=points.get(i);var b=points.get((i+1)%3);if(Math.abs(a.x-1)>1e-8||Math.abs(b.x-1)>1e-8||a.distanceSquared(b)<1e-16)continue;
            String A=key(a),B=key(b),edge=A.compareTo(B)<0?A+"/"+B:B+"/"+A;count.merge(edge,1,Integer::sum);
        }}
        for(int n:count.values()){require(n==2,"Every shared-face edge must be paired, with no cell-boundary crack");seamEdges++;}
        require(seamEdges>0,"Seam check must cover an actual shared edge");
    }
    private static String key(Vector3d p){return Math.round(p.x*1e7)+","+Math.round(p.y*1e7)+","+Math.round(p.z*1e7);}
    private static double area(List<ChamberWaterMesh.Triangle> mesh){return mesh.stream().mapToDouble(t->ChamberWaterMesh.area(t.points())).sum();}
    private static void near(double a,double b,String m){if(!Double.isFinite(a)||Math.abs(a-b)>1e-7)throw new AssertionError(m+": "+a+" != "+b);}
    private static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
}
