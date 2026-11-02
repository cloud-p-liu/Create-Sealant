package com.sealant.chamber;

import com.sealant.chamber.physics.*;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import java.util.List;

public final class ChamberFluidChecks {
    public static void run() {
        var cells=List.of(new ChamberFluid.Cell(0.5,.5,1),new ChamberFluid.Cell(1.5,.5,1));
        near(ChamberFluid.surfaceFor(cells,.75),.75,1e-7,"Internal volume/surface inversion");
        var sealed=new ChamberFluid();sealed.advance(cells,List.of(),10,.05);
        near(sealed.water(),0,1e-12,"Sealed chamber cannot flood");
        near(sealed.at(-10,10),ChamberFluid.AMBIENT,1e-10,"Dry chamber cannot contain phantom hydrostatic pressure below its floor");
        var solidOnly=new ChamberFluid();solidOnly.advance(List.of(),List.of(),10,.05);
        near(solidOnly.at(-20,10),ChamberFluid.AMBIENT,1e-10,"Fully solid chamber has no internal water pressure");
        near(sealed.pressure(),ChamberFluid.AMBIENT,1e-10,"Initial gas is ambient");
        sealed.restore(1,ChamberFluid.AMBIENT*2);sealed.advance(cells,List.of(),10,.05);
        near(sealed.pressure(),ChamberFluid.AMBIENT*2,1e-10,"Half gas volume doubles isothermal pressure");
        var restored=new ChamberFluid();restored.restore(sealed.water(),sealed.gasPV());restored.advance(cells,List.of(),10,.05);
        near(restored.pressure(),sealed.pressure(),1e-10,"Saved gas/water must resume same pressure");
        var flooded=new ChamberFluid();
        var wetPort=new ChamberFluid.Port(0,.25,ChamberFluid.AMBIENT+21,true,false);
        flooded.advance(cells,List.of(wetPort),10,.05);
        require(flooded.water()>0,"An unblocked wet opening must admit water");
        require(flooded.pressure()>ChamberFluid.AMBIENT,"Submerged bottom opening must compress trapped air");
        var vented=new ChamberFluid();vented.restore(1,ChamberFluid.AMBIENT*2);
        vented.advance(cells,List.of(new ChamberFluid.Port(2,.25,ChamberFluid.AMBIENT,false,true)),10,.05);
        near(vented.pressure(),ChamberFluid.AMBIENT,1e-8,"Open dry top vents gas");
        var draining=new ChamberFluid();draining.restore(1,ChamberFluid.AMBIENT);
        draining.advance(cells,List.of(new ChamberFluid.Port(0,.25,ChamberFluid.AMBIENT,false,false),new ChamberFluid.Port(2,.25,ChamberFluid.AMBIENT,false,true)),10,.05);
        require(draining.water()<1,"Internally submerged hole must drain into outside air");
        var gasOnly=new ChamberFluid();gasOnly.restore(0,ChamberFluid.AMBIENT*4);
        gasOnly.advance(cells,List.of(new ChamberFluid.Port(2,1,ChamberFluid.AMBIENT+1,true,true)),10,.05);
        near(gasOnly.water(),0,1e-12,"Escaping gas must not be counted as negative liquid");
        require(gasOnly.gasPV()<ChamberFluid.AMBIENT*4,"Upward submerged opening can bubble gas out");
        for(int i=0;i<400;i++)flooded.advance(cells,List.of(wetPort),10,.05);
        require(Double.isFinite(flooded.pressure()) && flooded.water()>=0 && flooded.water()<2,"Flood state must remain bounded");
        require(Math.abs(flooded.at(0,10)-wetPort.externalPressure())<.2,"Trapped-air inflow approaches pressure equilibrium");
        var invalid=new ChamberFluid();invalid.restore(Double.NaN,Double.POSITIVE_INFINITY);invalid.advance(cells,List.of(),10,.05);
        require(Double.isFinite(invalid.pressure()),"Corrupt saved values cannot poison native physics");
        checkPartialOpening();
        checkNearFullStability();
        checkDamping();
        checkPressureLoads();
        System.out.println("Chamber gas, leak, vent, pressure equilibrium and impulse regression checks passed.");
    }
    private static void checkPartialOpening() {
        var side=new ChamberFluid.Port(.25,.5,ChamberFluid.AMBIENT,false,false,.5,0);
        near(side.wetFraction(.01),.02,1e-12,"Low water must cover the bottom strip even below panel center");
        near(side.headIntegral(.01),.0001,1e-12,"Wet strip head integral");
        require(side.airExposed(.01),"Air above a partly wet side hole remains connected");
        require(!side.airExposed(.6),"Fully submerged inner opening cannot vent to outside air");
        var tilted=new ChamberFluid.Port(0,1,ChamberFluid.AMBIENT,false,false,.4,.7);
        for(double height:new double[]{-.54,-.3,-.1,0,.1,.3,.54}) {
            double wet=0,head=0;int n=240;
            for(int i=0;i<n;i++)for(int j=0;j<n;j++){
                double y=((i+.5)/n-.5)*.4+((j+.5)/n-.5)*.7;
                if(y<height)wet++;head+=Math.max(0,height-y);
            }
            near(tilted.wetFraction(height),wet/(n*n),.0002,"Tilted rectangle wet area matches independent quadrature");
            near(tilted.headIntegral(height),head/(n*n),.00001,"Tilted rectangle hydrostatic head integral");
        }
        // Low water used to stop exactly because the side panel center was above the surface.
        var cells=List.of(new ChamberFluid.Cell(.5,.5,1));
        var low=new ChamberFluid();low.restore(.001,ChamberFluid.AMBIENT*.999);
        for(int i=0;i<80;i++)low.advance(cells,List.of(side),10,.05);
        require(low.water()<.00099 && low.water()>0,"Partially wet side opening drains without deleting small quantities");
        near(low.pressure(),ChamberFluid.AMBIENT,1e-9,"Exposed dry strip avoids artificial suction in implicit trials");
        var trapped=new ChamberFluid();trapped.restore(.001,ChamberFluid.AMBIENT*.999);
        for(int i=0;i<80;i++)trapped.advance(cells,List.of(),10,.05);
        near(trapped.water(),.001,1e-12,"Low water in a sealed chamber is never cleared");
        var wetSide=new ChamberFluid.Port(.25,.5,ChamberFluid.AMBIENT,true,false,.5,0);
        require(!wetSide.airExposed(.01),"External water cannot be treated as atmospheric air");
        // Equal physical vertical opening split into panels must give identical integrated area/head.
        var lower=new ChamberFluid.Port(.125,.25,ChamberFluid.AMBIENT,false,false,.25,0);
        var upper=new ChamberFluid.Port(.375,.25,ChamberFluid.AMBIENT,false,false,.25,0);
        for(double h:new double[]{.001,.1,.249,.3,.49}){
            near(side.area()*side.wetFraction(h),lower.area()*lower.wetFraction(h)+upper.area()*upper.wetFraction(h),1e-12,"Panel subdivision preserves wet area");
            near(side.area()*side.headIntegral(h),lower.area()*lower.headIntegral(h)+upper.area()*upper.headIntegral(h),1e-12,"Panel subdivision preserves integrated head");
        }
    }
    private static void checkNearFullStability() {
        // Reproduces the user log: ~72 blocks^3 almost full, tiny gas pocket, many wet openings.
        var cells=List.of(new ChamberFluid.Cell(1.5,1.5,72));
        var ports=new java.util.ArrayList<ChamberFluid.Port>();
        for(int i=0;i<54;i++)ports.add(new ChamberFluid.Port(3,.5,ChamberFluid.AMBIENT*1.1,true,false));
        var fluid=new ChamberFluid();fluid.restore(71.313,.687*ChamberFluid.AMBIENT*.099);
        for(int i=0;i<20;i++) {
            fluid.advance(cells,ports,10,.05);
            require(fluid.pressure()<=ChamberFluid.AMBIENT*1.101,"Near-full inflow must not overshoot external equilibrium: "+fluid.pressure()/ChamberFluid.AMBIENT+" atm");
        }
        near(fluid.pressure()/ChamberFluid.AMBIENT,1.1,.001,"Near-full gas converges without pressure spikes");
        var split=new ChamberFluid();var whole=new ChamberFluid();
        split.restore(71.313,10);whole.restore(71.313,10);
        var divided=new java.util.ArrayList<ChamberFluid.Port>();
        for(int i=0;i<54;i++)divided.add(new ChamberFluid.Port(3,.5,ChamberFluid.AMBIENT*1.1,true,true));
        for(int i=0;i<10;i++) {
            split.advance(cells,divided,10,.05);
            whole.advance(cells,List.of(new ChamberFluid.Port(3,27,ChamberFluid.AMBIENT*1.1,true,true)),10,.05);
        }
        near(split.water(),whole.water(),1e-8,"Splitting an opening into panels cannot change leak rate");
        near(split.gasPV(),whole.gasPV(),1e-8,"Splitting an opening into panels cannot multiply bubble venting");
    }
    private static void checkDamping() {
        var inertia=new org.joml.Matrix3d().scaling(2,3,4);
        var points=List.of(new ChamberDamping.Point(new Vector3d(),1e8));
        var impulse=ChamberDamping.solve(10,inertia,new Vector3d(0,20,0),new Vector3d(),points,.05);
        double newY=20+impulse.linear().y/10;
        require(newY>=0 && newY<20,"Very strong drag must slow without reversing velocity");
        near(impulse.angular().length(),0,1e-8,"Central drag cannot invent torque");
        var offCenter=List.of(new ChamberDamping.Point(new Vector3d(2,0,0),10),new ChamberDamping.Point(new Vector3d(-1,0,1),20));
        var v=new Vector3d(2,-3,1);var w=new Vector3d(.5,2,-1);
        double oldEnergy=10*v.lengthSquared()+new Vector3d(w).dot(inertia.transform(new Vector3d(w)));
        var drag=ChamberDamping.solve(10,inertia,v,w,offCenter,.05);
        var afterV=new Vector3d(v).add(new Vector3d(drag.linear()).div(10));
        var afterW=new Vector3d(w).add(new org.joml.Matrix3d(inertia).invert().transform(new Vector3d(drag.angular())));
        double newEnergy=10*afterV.lengthSquared()+new Vector3d(afterW).dot(inertia.transform(new Vector3d(afterW)));
        require(newEnergy<oldEnergy,"Distributed drag cannot add kinetic energy");
        var rest=ChamberDamping.solve(10,inertia,new Vector3d(),new Vector3d(),offCenter,.05);
        near(rest.linear().length()+rest.angular().length(),0,1e-12,"Water drag cannot accelerate a resting body");
        var asymmetric=ChamberDamping.solve(10,inertia,new Vector3d(0,5,0),new Vector3d(),List.of(offCenter.getFirst()),.05);
        require(asymmetric.angular().length()>0,"Off-center wet contact must produce drag torque");
    }
    private static void checkPressureLoads() {
        // Uniform pressure must cancel on a closed box, including a rotated one.
        // Hydrostatic pressure must integrate to rho*g*V upward and vanish in X/Z.
        var sample=new ChamberSampling(new AABB(-2,-1,-3,2,1,3));
        for(boolean rotated:new boolean[]{false,true}) {
            var pose=new Pose3d();pose.position().set(10,-8,20);
            if(rotated)pose.orientation().rotateXYZ(.43,.71,-.22);
            var hydro=new ChamberWrench();var uniform=new ChamberWrench();
            for(var panel:sample.panels) {
                var world=pose.transformPosition(new Vector3d(panel.point()));
                var normal=pose.orientation().transform(new Vector3d(panel.outward()));
                double pressure=-world.y*10*ChamberFluid.DENSITY;
                hydro.add(world,new Vector3d(normal).mul(-pressure*panel.area()),pose.position());
                uniform.add(world,new Vector3d(normal).mul(-100*panel.area()),pose.position());
            }
            near(hydro.force.y,48*10*ChamberFluid.DENSITY,1e-8,"Rotated hydrostatic integral must equal displaced weight");
            near(hydro.force.x,0,1e-8,"Horizontal hydrostatic forces cancel");near(hydro.force.z,0,1e-8,"Horizontal hydrostatic forces cancel");
            near(hydro.torque.length(),0,1e-7,"Fully submerged symmetric box has no torque about its center");
            near(uniform.force.length()+uniform.torque.length(),0,1e-7,"Uniform gas pressure cannot create propulsion");
            var total=new Vector3d();
            for(int i=0;i<5;i++) {var w=new ChamberWrench();w.force.set(hydro.force);w.toLocalImpulse(pose.orientation(),.01);pose.orientation().transform(w.force);total.add(w.force);}
            near(total.distance(new Vector3d(hydro.force).mul(.05)),0,1e-8,"Impulse must not depend on substep count");
        }
        var offset=new ChamberWrench();offset.add(new Vector3d(2,0,0),new Vector3d(0,10,0),new Vector3d());
        near(offset.torque.z,20,1e-12,"Off-center force must generate correct torque");
        near(sample.cells.stream().mapToDouble(ChamberSampling.Cell::volume).sum(),48,1e-8,"Quadrature volume conserved");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void near(double actual,double expected,double tolerance,String message){if(!Double.isFinite(actual)||Math.abs(actual-expected)>tolerance)throw new AssertionError(message+": "+actual+" != "+expected);}
}
