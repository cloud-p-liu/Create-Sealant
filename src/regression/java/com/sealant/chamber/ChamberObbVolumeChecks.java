package com.sealant.chamber;

import com.sealant.chamber.topology.*;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.*;

public final class ChamberObbVolumeChecks {
    public static void run(){
        var box=new AABB(-1,-1,-1,1,1,1);var identity=new Pose3d();
        near(ChamberObbVolume.intersect(box,identity,box,identity).volume(),8,1e-10,"Identical boxes");
        var cut=ChamberObbVolume.intersect(box,identity,new AABB(0,-1,-1,2,1,1),identity);
        near(cut.volume(),4,1e-10,"Known axis-aligned intersection volume");
        near(cut.centroid().distance(new Vector3d(.5,0,0)),0,1e-10,"Intersection first moment");
        near(ChamberObbVolume.intersect(box,identity,new AABB(-.5,-.5,-.5,.5,.5,.5),identity).volume(),1,1e-10,"Contained box");
        for(var remote:List.of(new AABB(1,-1,-1,3,1,1),new AABB(1,1,-1,3,3,1),new AABB(1,1,1,3,3,3),new AABB(1.01,-1,-1,3,1,1))){
            var result=ChamberObbVolume.intersect(box,identity,remote,identity);
            near(result.volume(),0,1e-12,"Contact/disjoint boxes have zero volume");check(result.centroid()==null,"Zero-volume centroid is undefined");
        }
        var diagonal=new Pose3d();diagonal.orientation().rotateZ(Math.PI/4);
        near(ChamberObbVolume.intersect(box,identity,box,diagonal).volume(),16*(Math.sqrt(2)-1),1e-9,"Analytic rotated-square prism intersection");
        var relative=new Quaterniond().rotateXYZ(.31,-.17,.42);var relativeOffset=new Vector3d(.37,-.25,.21);
        var b=new Pose3d();b.orientation().set(relative);b.position().set(relativeOffset);
        var reference=ChamberObbVolume.intersect(box,identity,box,b);
        var random=new Random(5721);
        for(int i=0;i<100;i++){
            var common=new Quaterniond().rotateXYZ(random.nextDouble()*5,random.nextDouble()*5,random.nextDouble()*5);
            var a=new Pose3d();a.orientation().set(common);a.position().set(2048000+random.nextDouble()*20,63+random.nextDouble()*20,2048000+random.nextDouble()*20);
            var moved=new Pose3d();moved.orientation().set(common).mul(relative);moved.position().set(common.transform(new Vector3d(relativeOffset))).add(a.position());
            var result=ChamberObbVolume.intersect(box,a,box,moved);var reverse=ChamberObbVolume.intersect(box,moved,box,a);
            near(result.volume(),reference.volume(),1e-7,"Rigid transform/far-plot invariance");near(result.volume(),reverse.volume(),1e-8,"Intersection symmetry");
            near(result.centroid().distance(reverse.centroid()),0,1e-7,"Centroid symmetry");
            var expected=common.transform(new Vector3d(reference.centroid())).add(a.position());
            near(result.centroid().distance(expected),0,1e-7,"Centroid rigid transform");
            check(result.volume()>0 && result.volume()<=8,"Volume bounded by each OBB");
        }
        var scaled=new Pose3d();scaled.orientation().rotateXYZ(.2,.6,.4);scaled.scale().set(2,.5,3);
        near(ChamberObbVolume.intersect(box,scaled,box,scaled).volume(),24,1e-8,"Nonuniform orthogonal scale");
        var thin=new AABB(-1,-.00001,-1,1,.00001,1);
        near(ChamberObbVolume.intersect(thin,identity,box,identity).volume(),.00008,1e-10,"Thin positive volume is not contact");
        UUID owner=new UUID(0,1);
        var union=ChamberTopology.build(List.of(new ChamberTopology.Node(new UUID(0,2),owner,box),
                new ChamberTopology.Node(new UUID(0,3),owner,new AABB(0,-1,-1,2,1,1)))).getFirst().union();
        var enclosing=new AABB(-4,-4,-4,4,4,4);double total=0;var moment=new Vector3d();
        for(var piece:union.pieces){var r=ChamberObbVolume.intersect(piece.box(),identity,enclosing,identity);total+=r.volume();moment.fma(r.volume(),r.centroid());}
        near(total,12,1e-9,"Union pieces must not double-count overlapping chambers");
        near(moment.div(total).distance(new Vector3d(.5,0,0)),0,1e-9,"Union intersection centroid");
        // Move a real OBB through a chamber and back: partial overlap must retain history.
        var chamber=new AABB(0,0,0,4,2,2);boolean held=false;
        double[] positions={-1,-.5,-.011,-.01,0,3,3.5,3.989,3.99,4,3.5,3};
        boolean[] expectedHeld={false,false,false,true,true,true,true,true,false,false,false,true};
        for(int i=0;i<positions.length;i++){
            var moving=new AABB(positions[i],0,0,positions[i]+1,1,1);
            double overlap=ChamberObbVolume.intersect(chamber,identity,moving,identity).volume();
            held=ChamberObbVolume.takeover(overlap,1,held);
            check(held==expectedHeld[i],"Full entry/exit traversal step "+i);
        }
        for(double overlap:new double[]{.011,.25,.5,.989}){
            check(!ChamberObbVolume.takeover(overlap,1,false),"Partial entry remains unmanaged");
            check(ChamberObbVolume.takeover(overlap,1,true),"Partial exit remains managed");
        }
        check(ChamberObbVolume.takeover(.99-1e-11,1,false),"Entry tolerates roundoff");
        check(!ChamberObbVolume.takeover(.01+1e-11,1,true),"Exit tolerates roundoff");
        check(!ChamberObbVolume.takeover(0,1,true),"Zero contact releases");
        check(!ChamberObbVolume.takeover(Double.NaN,1,true),"Invalid overlap rejected");
        System.out.println("OBB intersection volume/centroid: analytic rotation, symmetry, far plot, scale, union and threshold checks passed.");
    }
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private static void near(double actual,double expected,double tolerance,String reason){if(!Double.isFinite(actual)||Math.abs(actual-expected)>tolerance)throw new AssertionError(reason+": "+actual+" != "+expected);}
}
