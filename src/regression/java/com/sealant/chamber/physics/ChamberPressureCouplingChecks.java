package com.sealant.chamber.physics;

import org.joml.Matrix3d;
import org.joml.Vector3d;
import java.util.*;

public final class ChamberPressureCouplingChecks {
    public static void run(){
        double worst=0;
        // Nine separate top cells of a dry 3x3 piston, initially ambient, no precharge.
        // Start at cylinder entry with exactly the speed from a two-block fall.
        for(double mass:new double[]{.1,1,10})for(double dt:new double[]{.2,.05,.025,.0125}){
            double g=10,y=0,v=-Math.sqrt(2*g*2),initial=.5*mass*v*v,maxEnergy=initial,exitHeight=Double.NaN;
            for(int step=0;step<800;step++){
                double old=v;v-=g*dt;
                List<ChamberPressureCoupling.Cell> cells=new ArrayList<>();
                if(y<=0)for(int i=0;i<9;i++)cells.add(new ChamberPressureCoupling.Cell(1+y,0,ChamberFluid.AMBIENT,new double[]{0,1,0,0,0,0}));
                var impulse=ChamberPressureCoupling.solve(mass,new Matrix3d().identity(),new Vector3d(0,v,0),new Vector3d(),cells,dt);
                v+=impulse.linear().y/mass;y+=dt*v;
                check(y>-1,"Implicit piston must not cross zero-volume barrier");
                double potential=y<0?9*ChamberFluid.AMBIENT*(-Math.log(1+y)+y):0;
                double energy=.5*mass*v*v+mass*g*y+potential;
                maxEnergy=Math.max(maxEnergy,energy);
                check(energy<=initial+1e-6*Math.max(1,initial),"Pressure must not manufacture piston energy");
                if(y>=0 && v>0){exitHeight=y+v*v/(2*g);break;}
            }
            if(Double.isFinite(exitHeight)){
                check(exitHeight<=2+1e-6,"Uncharged dry cylinder cannot launch above the release height");
                worst=Math.max(worst,exitHeight);
            }else check(Math.abs(v)<1e-5,"A dissipated piston settles rather than gaining energy");
            // Absence of any current surface/cell means zero residual pressure impulse.
            var outside=ChamberPressureCoupling.solve(mass,new Matrix3d().identity(),new Vector3d(0,10,0),new Vector3d(),List.of(),dt);
            check(outside.linear().lengthSquared()+outside.angular().lengthSquared()==0,"No stale push outside chamber");
        }
        // Fully enclosed uniform gas cancels before the implicit solve, including torque.
        double[] sum=new double[6];
        for(var sample:ChamberBodyPressure.surface(new net.minecraft.world.phys.AABB(-.5,-.5,-.5,.5,.5,.5))){
            var gradient=ChamberPressureCoupling.gradient(sample.outward(),sample.area(),sample.point());for(int j=0;j<6;j++)sum[j]+=gradient[j];
        }
        var uniform=ChamberPressureCoupling.solve(.1,new Matrix3d().identity(),new Vector3d(),new Vector3d(),List.of(new ChamberPressureCoupling.Cell(1,0,500,sum)),.2);
        check(uniform.linear().length()+uniform.angular().length()<1e-10,"Uniform gas must not propel or torque a closed body");
        // Off-center compression exercises the 6DOF inertia coupling, not just vertical motion.
        var offcenter=ChamberPressureCoupling.solve(.1,new Matrix3d().identity(),new Vector3d(0,-10,0),new Vector3d(0,0,2),List.of(new ChamberPressureCoupling.Cell(.5,0,101.325,new double[]{0,1,0,0,0,1})),.025);
        var linear=new Vector3d(0,-10,0).fma(10,offcenter.linear());
        var angular=new Vector3d(0,0,2).add(offcenter.angular());
        double capacity=.5+.025*(linear.y+angular.z);
        check(capacity>0,"Offcenter 6DOF coupling keeps positive volume");
        double before=.5*.1*100+.5*4+101.325*(-Math.log(.5)-.5);
        double after=.5*.1*linear.lengthSquared()+.5*angular.lengthSquared()+101.325*(-Math.log(capacity)+capacity-1);
        check(after<=before+1e-7,"Offcenter pressure conserves/dissipates kinetic plus EOS potential energy");
        System.out.printf(Locale.ROOT,"Implicit pressure coupling: 12 dry piston mass/dt cases conserve/dissipate energy, max exit height %.6f <= 2; outside release and uniform gas cancellation passed.%n",worst);
    }
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
