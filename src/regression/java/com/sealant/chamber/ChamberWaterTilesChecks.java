package com.sealant.chamber;

import net.minecraft.world.phys.AABB;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.*;

final class ChamberWaterTilesChecks {
    static void run(){
        var cube=new AABB(0,0,0,1,1,1);
        var faces=ChamberWaterTiles.exposed(cube,List.of(cube));
        check(faces.size()==6 && Math.abs(area(faces)-6)<1e-10,"All six isolated exterior faces retained");
        for(var face:faces){
            var points=ChamberWaterTiles.quad(face.rectangle(),face.axis(),face.sign());
            var normal=new Vector3d(points.get(1)).sub(points.getFirst()).cross(new Vector3d(points.get(2)).sub(points.getFirst()));
            check(normal.get(face.axis())*face.sign()>0,"Outward winding must survive GPU culling");
        }
        var neighbor=new AABB(1,0,0,2,1,1);
        check(Math.abs(area(ChamberWaterTiles.exposed(cube,List.of(cube,neighbor)))-5)<1e-10,"Adjacent hull blocks have no internal overlay");
        var half=new AABB(1,0,0,2,.5,1);
        check(Math.abs(area(ChamberWaterTiles.exposed(cube,List.of(cube,half)))-5.5)<1e-10,"Partial neighboring shape removes only its covered region");
        var pane=new AABB(.4375,0,0,.5625,1,1);
        check(Math.abs(area(ChamberWaterTiles.exposed(pane,List.of(pane)))-2.5)<1e-10,"Thin panes retain their actual visual thickness");
        var polygon=List.of(new Vector3d(-1.7,.2,-.4),new Vector3d(2.3,.6,-.4),new Vector3d(2.3,1.1,2.7),new Vector3d(-1.7,.7,2.7));
        var tiles=ChamberWaterTiles.tiles(polygon);
        check(Math.abs(tiles.stream().mapToDouble(t->ChamberWaterMesh.area(t.polygon())).sum()-ChamberWaterMesh.area(polygon))<1e-8,"Atlas tiling conserves sloped surface area across negative coordinates");
        for(var tile:tiles)for(var p:tile.polygon()){
            check(p.get(tile.u())-tile.originU()>=-1e-9 && p.get(tile.u())-tile.originU()<=1+1e-9,"U remains inside water sprite");
            check(p.get(tile.v())-tile.originV()>=-1e-9 && p.get(tile.v())-tile.originV()<=1+1e-9,"V remains inside water sprite");
        }
        var rotation=new Quaterniond().rotateXYZ(.37,.53,.19);
        var quad=ChamberWaterTiles.quad(new AABB(-1,-1,0,1,1,0),2,1).stream().map(p->rotation.transform(new Vector3d(p))).toList();
        var upper=ChamberWaterTiles.clip(quad,1,0,true);var lower=ChamberWaterTiles.clip(quad,1,0,false);
        check(Math.abs(ChamberWaterMesh.area(upper)-2)<1e-9 && Math.abs(ChamberWaterMesh.area(lower)-2)<1e-9,"World waterline bisects rotated hull face without changing area");
        var degenerate=List.of(new Vector3d(0,0,0),new Vector3d(0,0,0),new Vector3d(1,0,0),new Vector3d(1,0,1),new Vector3d(0,0,1));
        check(Math.abs(ChamberWaterTiles.tiles(degenerate).stream().mapToDouble(t->ChamberWaterMesh.area(t.polygon())).sum()-1)<1e-9,"Duplicate clipped vertices cannot choose the wrong UV projection");
        wetHull();
        contactSides();
        System.out.println("Water atlas tiling, exterior hull occlusion/winding and rotated waterline clipping checks passed.");
    }
    private static void contactSides(){
        for(var shape:List.of(new AABB(0,0,0,1,1,1),new AABB(.4375,0,0,.5625,1,1))){
            for(var face:ChamberWaterTiles.exposed(shape,List.of(shape))){
                var contact=ChamberWaterTiles.contact(face,ChamberWaterTiles.quad(face.rectangle(),face.axis(),face.sign()));
                double surface=ChamberWaterTiles.min(face.rectangle(),face.axis());
                near(contact.probe().get(face.axis()),surface+.004*face.sign(),"Water probe is directly adjacent to its own glass face");
                for(var p:contact.polygon())near(p.get(face.axis()),surface+.002*face.sign(),"Overlay stays on its own glass face, including thin panes");
                check(contact.visibleFrom(new Vector3d(contact.probe()).fma(-2,contact.normal())),"Overlay visible through glass from opposite side of water");
                check(!contact.visibleFrom(new Vector3d(contact.probe()).fma(2,contact.normal())),"Water-side viewer does not see back-through-glass overlay");
            }
        }
        var glass=new AABB(0,0,0,1,1,1);var domain=new AABB(1,0,0,3,1,1);
        var inner=ChamberWaterTiles.exposed(glass,List.of(glass)).stream().filter(f->f.axis()==0 && f.sign()==1).findFirst().orElseThrow();
        var contact=ChamberWaterTiles.contact(inner,ChamberWaterTiles.quad(inner.rectangle(),0,1));
        check(domain.contains(contact.probe().x,contact.probe().y,contact.probe().z),"Inner-face water sample belongs to chamber");
        var samples=contact.polygon().stream().map(p->new Vector3d(p).fma(.002,contact.normal())).toList();
        for(int i=0;i<12;i++){
            var pose=new dev.ryanhcode.sable.companion.math.Pose3d();pose.orientation().rotateXYZ(.03*i,.05*i,-.02*i);
            var flat=ChamberWaterMesh.prepare(List.of(new ChamberWaterMesh.Cell(domain,.5)),pose);
            var sampleWorld=samples.stream().map(p->pose.transformPosition(new Vector3d(p))).toList();
            var offset=new Vector3d(sampleWorld.getFirst()).sub(pose.transformPosition(new Vector3d(contact.polygon().getFirst())));
            var cuts=flat.clipWet(sampleWorld).stream().map(poly->poly.stream().map(p->new Vector3d(p).sub(offset)).toList()).toList();
            check(!cuts.isEmpty(),"Wet inner glass has overlay after rotation");
            for(var cut:cuts)for(var world:cut){
                var local=pose.transformPositionInverse(new Vector3d(world));
                near(local.x,1.002,"Interior-water overlay remains at inner glass plane, never the exterior plane -0.002");
            }
        }
    }
    private static void wetHull(){
        var pose=new dev.ryanhcode.sable.companion.math.Pose3d();
        var cells=List.of(new ChamberWaterMesh.Cell(new AABB(0,0,0,1,1,1),.2),new ChamberWaterMesh.Cell(new AABB(1,0,0,2,1,1),.8));
        var surface=ChamberWaterMesh.prepare(cells,pose);
        var wall=List.of(new Vector3d(0,0,.25),new Vector3d(2,0,.25),new Vector3d(2,1,.25),new Vector3d(0,1,.25));
        var cuts=surface.clipWet(wall);
        near(wetArea(cuts),1,"Overlay covers only the interior water below shared sloping surface");
        for(var cut:cuts){
            var center=new Vector3d();for(var p:cut)center.add(p);center.div(cut.size());
            check(surface.wet(center).orElse(false),"Overlay and visible camera water agree on wet polygon interiors");
            for(var p:cut)check(p.y<=.2+.3*p.x+1e-8,"Overlay follows sloping interface, not a horizontal cell waterline");
        }
        var horizontal=List.of(new Vector3d(0,.1,0),new Vector3d(2,.1,0),new Vector3d(2,.1,1),new Vector3d(0,.1,1));
        near(wetArea(surface.clipWet(horizontal)),2,"Fully wet floor coverage does not double at shared tet boundaries");
        var diagonal=List.of(new Vector3d(0,0,0),new Vector3d(1,0,1),new Vector3d(1,1,1),new Vector3d(0,1,0));
        var half=ChamberWaterMesh.prepare(List.of(new ChamberWaterMesh.Cell(new AABB(0,0,0,1,1,1),.5)),pose);
        near(wetArea(half.clipWet(diagonal)),Math.sqrt(2)/2,"Hull coplanar with shared tet face must not double overlay alpha");
        var boundary=List.of(new Vector3d(1,0,0),new Vector3d(1,0,1),new Vector3d(1,1,1),new Vector3d(1,1,0));
        near(wetArea(surface.clipWet(boundary)),.5,"Hull coplanar with shared cell face must not double overlay alpha");
        near(wetArea(ChamberWaterMesh.prepare(List.of(new ChamberWaterMesh.Cell(new AABB(0,0,0,2,1,1),-1)),pose).clipWet(wall)),0,"Dry chamber has no inward-looking water overlay");
        for(int i=0;i<12;i++){
            var tilted=new dev.ryanhcode.sable.companion.math.Pose3d();tilted.orientation().rotateXYZ(.11*i,-.07*i,.13*i);tilted.position().set(12,-4,23);
            var full=ChamberWaterMesh.prepare(List.of(new ChamberWaterMesh.Cell(new AABB(0,0,0,2,1,1),100)),tilted);
            var rotated=wall.stream().map(p->tilted.transformPosition(new Vector3d(p))).toList();
            near(wetArea(full.clipWet(rotated)),2,"Rotated hull wet clipping conserves polygon area");
        }
    }
    private static double wetArea(List<List<Vector3d>> polygons){return polygons.stream().mapToDouble(ChamberWaterMesh::area).sum();}
    private static void near(double actual,double expected,String message){check(Math.abs(actual-expected)<1e-7,message+": "+actual+" != "+expected);}
    private static double area(List<ChamberWaterTiles.Face> faces){return faces.stream().mapToDouble(f->ChamberWaterMesh.area(ChamberWaterTiles.quad(f.rectangle(),f.axis(),f.sign()))).sum();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
