package com.sealant.chamber.physics;

import com.sealant.chamber.topology.*;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import java.util.*;

/** Buoyancy is rho*g*submerged volume at its true centroid; protect tilt/union/plot precision. */
public final class ChamberLiteChecks {
    public static void run(){
        var pose=new Pose3d();var box=new AABB(-1,-1,-1,1,1,1);
        close(ChamberBuoyancyVolume.below(box,pose,-1).volume(),0,"Dry box");
        close(ChamberBuoyancyVolume.below(box,pose,1).volume(),8,"Full box");
        var half=ChamberBuoyancyVolume.below(box,pose,0);
        close(half.volume(),4,"Half volume");close(half.center().y,-.5,"True submerged centroid");
        for(int i=0;i<40;i++){
            var tilted=new Pose3d();tilted.orientation().rotateXYZ(i*.13,i*.17,-i*.21);
            var full=ChamberBuoyancyVolume.below(box,tilted,10);
            var dry=ChamberBuoyancyVolume.below(box,tilted,-10);
            close(full.volume(),8,"Rotation preserves displacement");close(dry.volume(),0,"Rotated dry box");
            var a=ChamberBuoyancyVolume.below(box,tilted,.23);
            var b=ChamberBuoyancyVolume.below(box,tilted,-.23);
            close(a.volume()+b.volume(),8,"Complement volumes under inversion");
            var centered=ChamberBuoyancyVolume.below(box,tilted,0);
            close(centered.volume(),4,"Any center plane halves centrally symmetric box");
            check(a.center().y<=.23+1e-9,"Buoyancy point cannot be above water");
            double volume=0;var moment=new Vector3d();
            for(var sample:ChamberLitePhysics.subdivide(List.of(new ChamberUnion.Piece(new UUID(0,1),box)))){
                var wet=ChamberBuoyancyVolume.below(sample,tilted,.23);volume+=wet.volume();moment.fma(wet.volume(),wet.center());
            }
            close(volume,a.volume(),"Subdivision preserves displacement");
            check(moment.div(volume).distance(a.center())<1e-8,"Subdivision preserves total torque");
        }
        var remote=new AABB(2048000,62,2048000,2048002,64,2048002);
        var shifted=new Pose3d();shifted.position().set(0,0,0);
        var wet=ChamberBuoyancyVolume.below(remote,shifted,63);
        close(wet.volume(),4,"Far plot volume precision");close(wet.center().y,62.5,"Far plot centroid precision");
        UUID owner=new UUID(0,1);
        var group=ChamberTopology.build(List.of(new ChamberTopology.Node(new UUID(0,2),owner,box),
                new ChamberTopology.Node(new UUID(0,3),owner,new AABB(0,-1,-1,2,1,1)))).getFirst();
        double union=0;for(var sample:ChamberLitePhysics.subdivide(group.union().pieces))union+=ChamberBuoyancyVolume.below(sample,pose,10).volume();
        close(union,12,"Overlapping markers must not duplicate buoyancy");
        var big=ChamberLitePhysics.subdivide(List.of(new ChamberUnion.Piece(owner,new AABB(0,0,0,100,100,100))));
        check(big.size()==64,"Bounded large-domain sample count");
        close(big.stream().mapToDouble(ChamberUnion::volume).sum(),1e6,"Budget cannot discard volume");
        System.out.println("Lightweight chamber dry/full/tilted centroid, subdivision and union buoyancy checks passed.");
    }
    private static void close(double a,double b,String reason){check(Math.abs(a-b)<1e-8,reason+": "+a+" != "+b);}
    private static void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
}
