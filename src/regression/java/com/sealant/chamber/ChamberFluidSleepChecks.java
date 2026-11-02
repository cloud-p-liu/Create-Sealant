package com.sealant.chamber;

import com.sealant.chamber.physics.*;
import java.util.*;

public final class ChamberFluidSleepChecks {
    private static final double P=ChamberFluid.AMBIENT;
    private static ChamberField.Link face(int a,int b,double height){return new ChamberField.Link(a,b,height,1,1,0,0,P,false,false);}
    private static ChamberField gas(double a,double b,boolean open,double flux){
        return new ChamberField(List.of(new ChamberField.Geometry(1,0,1),new ChamberField.Geometry(1,0,1)),
                List.of(face(0,open?-1:1,.5)),new double[2],new double[]{a,b},new double[]{flux},10);
    }
    private static void settle(ChamberField field){for(int i=0;i<4;i++)field.advance(.2);}
    public static void run(){
        var sealed=gas(P,P,false,0);sealed.advance(.2);
        check(!sealed.sleeping(),"Do not sleep on first quiet observation");settle(sealed);
        check(sealed.sleeping()&&sealed.skippedExchange(),"Closed equilibrium sleeps after consecutive checks");
        long water=Double.doubleToLongBits(sealed.totalWater()),gas=Double.doubleToLongBits(sealed.totalGas());
        for(int i=0;i<100;i++)sealed.advance(.2);
        check(Double.doubleToLongBits(sealed.totalWater())==water && Double.doubleToLongBits(sealed.totalGas())==gas,"Sleeping never changes or clears mass");
        var rebuilt=gas(P,P,false,0);rebuilt.inheritRest(sealed);rebuilt.advance(.2);
        check(rebuilt.sleeping()&&rebuilt.skippedExchange(),"Rest survives identical per-update field reconstruction");
        rebuilt.wake();rebuilt.advance(.2);check(!rebuilt.sleeping(),"Mode/explicit wake restarts quiet confirmation");
        var changed=gas(P+1,P,false,0);changed.inheritRest(sealed);changed.advance(.2);
        check(!changed.sleeping()&&!changed.skippedExchange(),"Gas pressure disturbance wakes exchange immediately");
        check(changed.gas(0)<P+1 && changed.gas(1)>P,"Woken gas really exchanges conservatively");
        var open=gas(P,P,true,0);open.inheritRest(sealed);settle(open);
        check(!open.sleeping(),"Dry equilibrium exterior opening forbids sleep");
        var inertia=gas(P,P,false,.1);inertia.advance(.2);check(!inertia.sleeping(),"Existing face momentum forbids sleep");
        // Full-water hydrostatic equilibrium has unequal base pressures but ZERO shared-face delta.
        double rhoG=ChamberFluid.DENSITY*10;
        var geometry=List.of(new ChamberField.Geometry(1,0,1),new ChamberField.Geometry(1,1,2));
        var hydro=new ChamberField(geometry,List.of(face(0,1,1)),new double[]{1+rhoG/ChamberField.BULK,1},new double[2],new double[1],10);
        settle(hydro);check(hydro.sleeping(),"Hydrostatic pressure gradient is not mistaken for flow");
        near(hydro.at(0,.5)-hydro.at(0,1),rhoG*.5,1e-8,"Sleeping preserves local buoyancy gradient");
        check(hydro.pressure(0)>hydro.pressure(1),"Sleeping must not flatten per-cell pressures");
        // Balanced ordinary pressure still must not suppress internal counter-current exchange.
        var inverted=new ChamberField(geometry,List.of(face(0,1,1)),new double[]{0,1},new double[]{P+rhoG,0},new double[1],10);
        inverted.advance(.2);check(!inverted.sleeping() && inverted.water(0)>0,"Counter-current instability stays awake");
        // Layout changes do not require solving if the ACTUAL new field is already at rest.
        var resized=new ChamberField(List.of(new ChamberField.Geometry(.5,0,1),new ChamberField.Geometry(1,0,1)),List.of(face(0,1,.5)),new double[2],new double[]{P*.5,P},new double[1],10);
        resized.inheritRest(sealed);resized.advance(.2);check(resized.sleeping(),"Changed capacity already at physical equilibrium can sleep");
        var tilted=new ChamberField(List.of(new ChamberField.Geometry(1,.1,1.1),new ChamberField.Geometry(1,0,1)),List.of(face(0,1,.6)),new double[2],new double[]{P,P},new double[1],10);
        tilted.inheritRest(sealed);tilted.advance(.2);check(tilted.sleeping(),"Dry world-coordinate changes alone do not block sleep");
        var tiny=new ChamberField(List.of(new ChamberField.Geometry(1,0,1)),List.of(),new double[]{1e-12},new double[]{P},new double[0],10);
        settle(tiny);check(tiny.water(0)==1e-12,"Sleep never zeroes tiny water inventory");
        // A tiny surface mismatch yields the reported ~0.000018 volume/tick exchange.
        for(double dt:new double[]{.05,.2}){
            var jitter=jitter(1);jitter.advance(dt);
            check(jitter.restResidualPerTick()<ChamberField.REST_VOLUME_PER_TICK,"Reported jitter is below whole-group tick budget");
            near(jitter.restResidualPerTick(),.000018,1e-7,"Displacement residual normalized per game tick");
            near(jitter.displacementPerTick(),jitter.displacedVolume()*.05/dt,1e-15,"LOW report divides update displacement by four ticks");
            settle(jitter);check(jitter.sleeping(),"Small exchange residual must not prevent sleep");
            var measured=jitter(.000310/.000018);measured.advance(dt);
            near(measured.restResidualPerTick(),.000310,3e-6,"Measured group residual normalized per tick");
            check(measured.restCandidate(),"Measured residual passes both exchange and water-head tolerances");
            settle(measured);check(measured.sleeping(),"Measured 0.000310 jitter can sleep");
            var moving=jitter(.0008/.000018);moving.advance(dt);
            check(moving.restResidualPerTick()>ChamberField.REST_VOLUME_PER_TICK && !moving.sleeping(),"Larger displacement remains active");
        }
        var opposing=new ChamberField(List.of(new ChamberField.Geometry(1,0,1),new ChamberField.Geometry(1,0,1)),List.of(face(0,1,.25),face(0,1,.25)),new double[]{.5,.5},new double[]{P*.5,P*.5},new double[]{.005,-.005},10);
        opposing.advance(.2);check(opposing.restResidualPerTick()>ChamberField.REST_VOLUME_PER_TICK && !opposing.sleeping(),"Opposing fluxes add absolutely, never cancel into a false rest");
        var dryHistory=gas(P,P,false,.0598378);dryHistory.advance(.2);
        near(dryHistory.restReport().waterPerTick(),0,1e-15,"No phantom water residual from dry face history");
        near(dryHistory.restResidualPerTick(),0,1e-15,"Dry uniform gas has zero residual despite old water momentum");
        settle(dryHistory);check(dryHistory.sleeping(),"Dry history cannot block sleep indefinitely");
        var dryDonor=new ChamberField(List.of(new ChamberField.Geometry(1,0,1),new ChamberField.Geometry(1,0,1)),List.of(face(0,1,.25)),new double[]{0,.5},new double[]{P,P*.5},new double[]{.0598378},0);
        dryDonor.advance(.2);near(dryDonor.restReport().waterPerTick(),0,1e-15,"Dry donor cannot supply historical water flux even if receiver is wet");
        var compressed=gas(P+1,P,false,0);compressed.advance(.2);
        check(compressed.restReport().gasPerTick()>0 && compressed.restReport().waterPerTick()==0 && compressed.restReport().displacementPerTick()==0,"Dry pressure relaxation is diagnosed as gas, not water displacement");
        var submerged=new ChamberField(List.of(new ChamberField.Geometry(1,0,1),new ChamberField.Geometry(1,0,1)),List.of(new ChamberField.Link(0,1,.5,1,1,0,0,P+100,true,false)),new double[2],new double[]{P,P},new double[1],10);
        settle(submerged);check(submerged.sleeping() && submerged.restResidualPerTick()==0,"Outside water pressure cannot drive a sealed dry internal link");
        var leak=new ChamberField(List.of(new ChamberField.Geometry(1,0,1)),List.of(new ChamberField.Link(0,-1,.5,1,1,0,0,P+100,true,false)),new double[1],new double[]{P},new double[1],10);
        leak.advance(.2);check(leak.restReport().exteriorLinks()==1 && leak.restReport().waterPerTick()>0 && !leak.sleeping(),"Actual underwater opening reports inflow and forbids sleep");
        ChamberField rocking=gas(P+1,P,false,0);
        for(int i=0;i<100;i++){
            double offset=Math.sin(i*.17),tilt=Math.cos(i*.19)*.1;
            var next=new ChamberField(List.of(new ChamberField.Geometry(1,offset,offset+1),new ChamberField.Geometry(1,offset+tilt,offset+tilt+1)),
                    List.of(new ChamberField.Link(0,1,offset+.5,1,1,Math.abs(tilt),0,P,false,false)),new double[2],new double[]{rocking.gas(0),rocking.gas(1)},rocking.flux(),10);
            next.inheritRest(rocking);next.advance(.2);rocking=next;
        }
        check(rocking.sleeping(),"Closed dry carrier rocking must not continually reset rest confirmation");
        var changedGraph=new ChamberField(List.of(new ChamberField.Geometry(1,.2,1.2),new ChamberField.Geometry(1,.2,1.2)),List.of(new ChamberField.Link(0,1,.7,.5,1,0,0,P,false,false)),new double[2],new double[]{rocking.gas(0),rocking.gas(1)},new double[1],10);
        changedGraph.inheritRest(rocking);changedGraph.advance(.2);check(changedGraph.sleeping(),"Changed aperture at equilibrium does not force pointless exchange");
        var wetRocking=new ChamberField(List.of(new ChamberField.Geometry(1,.2,1.2),new ChamberField.Geometry(1,.2,1.2)),List.of(face(0,1,.7)),new double[]{.5,.5},new double[]{P*.5,P*.5},new double[1],10);
        wetRocking.inheritRest(rocking);wetRocking.advance(.2);check(wetRocking.sleeping(),"Wet equilibrium also survives a common rigid height change");
        var reportTime=gas(P+1,P,false,0);reportTime.advance(.2);
        var beforeReport=reportTime.restReport();var currentReport=reportTime.currentRestReport();
        near(currentReport.gasPressureGap(),Math.abs(reportTime.pressure(0)-reportTime.pressure(1)),1e-12,"Current rest report matches current pressure range");
        check(beforeReport.gasPressureGap()>currentReport.gasPressureGap(),"Explicit before/after diagnostics show gas relaxation");
        check(reportTime.restReport().equals(beforeReport),"Reading current diagnostics does not mutate sleep decision");
        // A collider displaces .25 of a cell; tiny pose/area/capacity jitter must not
        // continually reset quiet observations when the physical residual is negligible.
        ChamberField occupied=new ChamberField(List.of(new ChamberField.Geometry(.75,0,1),new ChamberField.Geometry(1,0,1)),List.of(face(0,1,.5)),new double[2],new double[]{P*.75,P},new double[1],10);
        double inventory=occupied.totalGas();
        for(int i=0;i<50;i++){
            double tinyJitter=Math.sin(i*.2)*1e-12,shift=Math.sin(i*.15);
            var next=new ChamberField(List.of(new ChamberField.Geometry(.75+tinyJitter,shift,shift+1),new ChamberField.Geometry(1,shift,shift+1)),List.of(new ChamberField.Link(0,1,shift+.5,1+tinyJitter,1,0,0,P,false,false)),new double[2],new double[]{occupied.gas(0),occupied.gas(1)},occupied.flux(),10);
            next.inheritRest(occupied);next.advance(.2);occupied=next;
        }
        check(occupied.sleeping(),"Collider occupancy plus harmless geometry jitter permits sleep");
        near(occupied.totalGas(),inventory,1e-9,"Collider rest inheritance does not copy, flatten or change gas inventory");
        var piston=new ChamberField(List.of(new ChamberField.Geometry(.5,0,1),new ChamberField.Geometry(1,0,1)),List.of(face(0,1,.5)),new double[2],new double[]{occupied.gas(0),occupied.gas(1)},new double[1],10);
        piston.inheritRest(occupied);piston.advance(.2);
        check(!piston.sleeping()&&!piston.skippedExchange() && piston.gas(1)>occupied.gas(1),"Real collider compression wakes and transports gas in the SAME update");
        var waterTilt=new ChamberField(List.of(new ChamberField.Geometry(1,.1,1.1),new ChamberField.Geometry(1,0,1)),List.of(face(0,1,.5)),new double[]{.5,.5},new double[]{P*.5,P*.5},new double[1],10);
        waterTilt.inheritRest(wetRocking);waterTilt.advance(.2);check(!waterTilt.sleeping(),"Tilt creating an actual water head wakes transport");
        var reopened=gas(P,P,true,0);reopened.inheritRest(occupied);reopened.advance(.2);
        check(!reopened.sleeping()&&!reopened.skippedExchange(),"Opening wakes previously resting collider field immediately");
        System.out.println("Fluid sleep: sealed rest/rebuild, opening/pressure/momentum/geometry wake, hydrostatic gradient, counter-current instability and no mass clearing checks passed.");
    }
    private static ChamberField jitter(double multiplier){
        double delta=Math.pow(.000018*multiplier/(.05*.25),2)/20;
        double a=.5+delta/2,b=.5-delta/2;
        return new ChamberField(List.of(new ChamberField.Geometry(1,0,1),new ChamberField.Geometry(1,0,1)),
                List.of(new ChamberField.Link(0,1,.5,1,1,1,0,P,false,false)),new double[]{a,b},new double[]{P*(1-a),P*(1-b)},new double[1],10);
    }
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private static void near(double value,double expected,double tolerance,String reason){check(Math.abs(value-expected)<tolerance,reason+": "+value);}
}
