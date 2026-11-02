package com.sealant.chamber;

import com.sealant.chamber.physics.*;
import java.util.*;

public final class ChamberFieldChecks {
    private static final double ATM=ChamberFluid.AMBIENT;
    private static ChamberField.Geometry cell(double y){return new ChamberField.Geometry(1,y,y+1);}
    private static ChamberField.Link face(int a,int b,double y,double area){return new ChamberField.Link(a,b,y,area,1,1,0,ATM,false,false);}
    private static ChamberField field(List<ChamberField.Geometry> cells,List<ChamberField.Link> links,double[] w,double[] g){return new ChamberField(cells,links,w,g,new double[links.size()],10);}
    public static void run(){
        near(ChamberField.pressure(1,0,ATM),ATM,1e-10,"Dry atmospheric gas EOS");
        near(ChamberField.pressure(1,.5,ATM*.5),ATM,1e-10,"Partial water/gas at ambient pressure");
        near(ChamberField.pressure(1,1,0),ATM,1e-8,"Completely full liquid has a defined pressure");
        require(ChamberField.pressure(1,1.001,0)>ATM,"Liquid compression raises local pressure");
        var sealed=field(List.of(cell(0),cell(0)),List.of(),new double[]{.8,.1},new double[]{ATM*.2,ATM*.9});
        for(int i=0;i<20;i++)sealed.advance(.05);
        near(sealed.water(0),.8,1e-12,"Blocked faces do not move water");near(sealed.water(1),.1,1e-12,"Sealed neighboring cell keeps its own state");
        var opened=field(List.of(cell(0),cell(0)),List.of(face(0,1,.5,.25)),new double[]{sealed.water(0),sealed.water(1)},new double[]{sealed.gas(0),sealed.gas(1)});
        opened.advance(.05);
        require(opened.water(0)<.8 && opened.water(0)>.45 && opened.water(1)>.1 && opened.water(1)<.45,"Opening a hole exchanges locally instead of instant equalization");
        near(opened.totalWater(),.9,1e-10,"Opening preserves water mass");
        var pulse=field(List.of(cell(0),cell(0),cell(0)),List.of(face(0,1,.5,.25),face(1,2,.5,.25)),new double[3],new double[]{ATM*2,ATM,ATM});
        pulse.advance(.05);
        require(pulse.gas(1)>ATM,"Pressure pulse reaches adjacent cell through a face");
        require(pulse.pressure(0)>pulse.pressure(1) && pulse.pressure(1)>pulse.pressure(2),"Connected cells retain a local pressure gradient, never room averaging");
        near(pulse.totalGas(),ATM*4,1e-8,"Gas transport is conservative");
        var water=field(List.of(cell(0),cell(0)),List.of(face(0,1,.5,1)),new double[]{.9,.2},new double[]{ATM*.1,ATM*.8});
        water.advance(.05);require(water.water(0)<.9 && water.water(1)>.2,"Local water-head difference transports water");
        near(water.totalWater(),1.1,1e-10,"Shared water flux subtracts exactly what it adds");near(water.totalGas(),ATM*.9,1e-8,"Coupled phases conserve gas");
        // Pure-water column: gravity must transfer mass/compression and create a pressure gradient.
        var column=field(List.of(cell(0),cell(1)),List.of(new ChamberField.Link(1,0,1,1,1,0,0,ATM,true,false)),new double[]{1,1},new double[]{0,0});
        for(int i=0;i<60;i++)column.advance(.05);
        require(column.pressure(0)>column.pressure(1)+5,"Gravity develops pressure in lower liquid cells");
        near(column.totalWater(),2,1e-10,"Gravity cannot create or delete water mass");
        var outlet=field(List.of(cell(0)),List.of(new ChamberField.Link(0,-1,.25,.25,.5,.5,0,ATM,false,false)),new double[]{.001},new double[]{ATM*.999});
        for(int i=0;i<40;i++)outlet.advance(.05);
        require(outlet.water(0)<.001,"Low-water outlet uses wet strip instead of center cutoff");
        // Near-full high gradients reproduce the former instability class without global pressure resets.
        var stiff=field(List.of(cell(0),cell(0)),List.of(face(0,1,.5,4)),new double[]{.99999,.01},new double[]{ATM*.00001*.099,ATM*.99*1.1});
        for(int i=0;i<100;i++){
            stiff.advance(.05);
            require(Math.max(stiff.pressure(0),stiff.pressure(1))<ATM*2,"Near-full local exchange must not generate explosive pressure spikes");
        }
        near(stiff.totalWater(),1.00999,1e-8,"Stiff mixed field conserves water");near(stiff.totalGas(),ATM*.00001*.099+ATM*.99*1.1,1e-7,"Stiff mixed field conserves gas");
        for(int i=0;i<2;i++)require(Double.isFinite(stiff.pressure(i)) && stiff.pressure(i)>0 && stiff.water(i)>=0 && stiff.gas(i)>=0,"Stiff exchange stays finite and positive");
        var symmetric=field(List.of(cell(0),cell(0)),List.of(face(0,1,.5,1)),new double[]{.4,.4},new double[]{ATM*.6,ATM*.6});
        for(int i=0;i<50;i++)symmetric.advance(.05);
        near(symmetric.water(0),.4,1e-12,"Uniform field is stationary");near(symmetric.gas(0),ATM*.6,1e-12,"Uniform gas field is stationary");
        // Low-frequency mode takes one conservative .2s step per four game ticks.
        var low=field(List.of(cell(0),cell(1)),List.of(new ChamberField.Link(1,0,1,1,1,0,0,ATM,false,false)),
                new double[]{0,.4},new double[]{ATM,ATM*.6});
        for(int i=0;i<300;i++)low.advance(.2);
        near(low.totalWater(),.4,1e-9,"Low-frequency field water conservation");
        near(low.totalGas(),ATM*1.6,1e-7,"Low-frequency field gas conservation");
        require(low.water(1)<.004 && low.water(0)>.396,"Low-frequency phase displacement still descends");
        for(int i=0;i<2;i++)require(Double.isFinite(low.pressure(i)) && low.pressure(i)>0 && low.water(i)>=0 && low.gas(i)>=0,"Low-frequency field remains finite and positive");
        var flattening=field(List.of(cell(0),cell(0)),List.of(face(0,1,.5,1)),new double[]{1,0},new double[]{0,ATM});
        for(int i=0;i<5;i++)flattening.advance(.2);
        System.out.printf(java.util.Locale.ROOT,"Internal flattening after one simulated second: receiver=%.8f, spread=%.8f%n",flattening.water(1),Math.abs(flattening.water(0)-flattening.water(1)));
        require(Math.abs(flattening.water(0)-flattening.water(1))<.01,"Internal redistribution should level a two-cell column within one second");
        near(flattening.totalWater(),1,1e-10,"Faster internal flow cannot create water");
        near(flattening.totalGas(),ATM,1e-8,"Faster internal flow cannot create gas");
        displacement();
        threeDimensional();
        System.out.println("Chamber local-field EOS, finite face transport, gravity-gradient and phase-conservation checks passed.");
    }
    private static void displacement(){
        var vertical=new ChamberField.Link(1,0,1,1,1,0,0,ATM,false,false);
        var inverted=field(List.of(cell(0),cell(1)),List.of(vertical),new double[]{0,.4},new double[]{ATM,ATM*.6});
        for(int i=0;i<1200;i++)inverted.advance(.05);
        require(inverted.water(1)<.004 && inverted.water(0)>.396,"Water above air must descend; gas must not stay locked behind a wet face");
        require(inverted.gas(0)<ATM*.7 && inverted.gas(1)>ATM*.9,"Displaced lower gas rises through the shared face");
        near(inverted.totalWater(),.4,1e-9,"Counter-current exchange conserves water mass");near(inverted.totalGas(),ATM*1.6,1e-7,"Counter-current exchange conserves gas mass");
        var spread=field(List.of(cell(0),cell(0)),List.of(face(0,1,.5,1)),new double[]{1,0},new double[]{0,ATM});
        for(int i=0;i<400;i++)spread.advance(.05);
        require(spread.water(1)>.4 && spread.gas(0)>ATM*.4,"A full water cell can spread and receive displaced air without a pre-existing dry aperture");
        near(spread.totalWater(),1,1e-9,"Lateral displacement water conservation");near(spread.totalGas(),ATM,1e-7,"Lateral displacement gas conservation");
        var bell=field(List.of(cell(0)),List.of(new ChamberField.Link(-1,0,0,.25,.5,0,0,ATM+10.5,true,false)),new double[]{0},new double[]{ATM});
        for(int i=0;i<400;i++)bell.advance(.05);
        near(bell.totalGas(),ATM,1e-9,"Submerged bottom boundary still traps gas; internal displacement cannot vent it to the reservoir");
        require(bell.pressure(0)>ATM && bell.water(0)>.01 && bell.water(0)<.2,"Diving-bell backpressure remains active");
        var dry=field(List.of(cell(0)),List.of(),new double[]{0},new double[]{ATM});near(dry.air(0),1,1e-10,"Actual gas volume for pink debug");
        var vacuum=field(List.of(cell(0)),List.of(),new double[]{0},new double[]{0});near(vacuum.air(0),0,1e-10,"Empty vacuum cannot be rendered as air");
    }
    private static void threeDimensional(){
        var domain=new ChamberCellGrid(List.of(new com.sealant.chamber.topology.ChamberUnion.Piece(new UUID(0,1),new net.minecraft.world.phys.AABB(0,0,0,3,3,3))));
        List<ChamberField.Geometry> geometry=new ArrayList<>();double[] w=new double[27],g=new double[27];Arrays.fill(g,ATM);int center=-1;
        for(int i=0;i<domain.cells.size();i++){var b=domain.cells.get(i).box();geometry.add(cell(b.minY));if(b.minX==1 && b.minY==1 && b.minZ==1)center=i;}
        g[center]=2*ATM;List<ChamberField.Link> links=new ArrayList<>();List<Integer> neighbors=new ArrayList<>();
        for(var f:domain.faces)if(f.a()>=0 && f.b()>=0){
            links.add(new ChamberField.Link(f.a(),f.b(),f.rectangle().getCenter().y,1,1,f.axis()==1?0:1,0,ATM,false,false));
            if(f.a()==center)neighbors.add(f.b());if(f.b()==center)neighbors.add(f.a());
        }
        var field=field(geometry,links,w,g);field.advance(.005);
        require(neighbors.size()==6,"Three-dimensional field has six direct axial neighbors");
        for(int i:neighbors)require(field.gas(i)>ATM && field.pressure(center)>field.pressure(i),"Local pressure reaches all three axes without global averaging");
        near(field.totalGas(),28*ATM,1e-7,"3D six-face exchange conserves gas inventory");
    }
    private static void near(double a,double b,double t,String m){if(!Double.isFinite(a)||Math.abs(a-b)>t)throw new AssertionError(m+": "+a+" != "+b);}
    private static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
}
