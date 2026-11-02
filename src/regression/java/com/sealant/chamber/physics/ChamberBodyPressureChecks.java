package com.sealant.chamber.physics;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

public final class ChamberBodyPressureChecks {
    public static void run(){
        // This box has NO surface on a chamber integer grid plane: the old grid-face
        // sampling can miss it completely, but its own surface still displaces water.
        var box=new AABB(.13,.17,.19,.87,.83,.89);
        double volume=box.getXsize()*box.getYsize()*box.getZsize();
        var c=box.getCenter();
        for(int i=0;i<30;i++){
            var pose=new Pose3d();pose.orientation().rotateXYZ(i*.13,-i*.09,i*.17);
            var center=pose.transformPosition(new Vector3d(c.x,c.y,c.z));
            var water=new ChamberWrench();var gas=new ChamberWrench();
            for(var sample:ChamberBodyPressure.surface(box)){
                var point=pose.transformPosition(new Vector3d(sample.point()));
                var normal=pose.orientation().transform(new Vector3d(sample.outward()));
                water.add(point,ChamberBodyPressure.force(normal,sample.area(),ChamberFluid.DENSITY*10*(10-point.y)),center);
                gas.add(point,ChamberBodyPressure.force(normal,sample.area(),500),center);
            }
            near(water.force.y,ChamberFluid.DENSITY*10*volume,1e-8,"Off-grid body receives displaced weight");
            near(water.force.x,0,1e-8,"Horizontal forces cancel");near(water.force.z,0,1e-8,"Horizontal forces cancel");
            near(water.torque.length(),0,1e-8,"Tilt cannot invent hydrostatic torque on a fully immersed box");
            near(gas.force.length()+gas.torque.length(),0,1e-8,"Uniform gas pressure produces no propulsion");
        }
        var box2=new AABB(-.5,-.5,-.5,.5,.5,.5);var half=new ChamberWrench();var dry=new ChamberWrench();
        for(var sample:ChamberBodyPressure.surface(box2)){
            half.add(sample.point(),ChamberBodyPressure.force(sample.outward(),sample.area(),10.5*Math.max(0,-sample.point().y)),new Vector3d());
            dry.add(sample.point(),ChamberBodyPressure.force(sample.outward(),sample.area(),0),new Vector3d());
        }
        near(half.force.y,5.25,1e-8,"Half immersed body receives half volume buoyancy");
        near(dry.force.length()+dry.torque.length(),0,1e-12,"Dry body has no invented lift");
        System.out.println("Independent internal body surface pressure: off-grid, rotation, uniform gas and partial immersion checks passed.");
    }
    private static void near(double actual,double expected,double tolerance,String reason){
        if(!Double.isFinite(actual)||Math.abs(actual-expected)>tolerance)throw new AssertionError(reason+": "+actual+" != "+expected);
    }
}
