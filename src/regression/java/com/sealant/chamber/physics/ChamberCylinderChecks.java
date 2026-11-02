package com.sealant.chamber.physics;

import java.util.*;

/** Sealed 3x3x4 dry cylinder, a 3x3 piston intruding half a block from above.
 * Audits the real field solver, not Sable rigid-body dynamics or moving collision mapping. */
public final class ChamberCylinderChecks {
    private static int cell(int x,int y,int z){return (y*3+x)*3+z;}
    private static ChamberField cylinder(){
        var geometry=new ArrayList<ChamberField.Geometry>();
        for(int y=0;y<4;y++)for(int x=0;x<3;x++)for(int z=0;z<3;z++)geometry.add(new ChamberField.Geometry(y==3?.5:1,y,y+1));
        var links=new ArrayList<ChamberField.Link>();
        for(int y=0;y<4;y++)for(int x=0;x<3;x++)for(int z=0;z<3;z++){
            int a=cell(x,y,z);
            if(x<2)links.add(link(a,cell(x+1,y,z),y+.5));
            if(z<2)links.add(link(a,cell(x,y,z+1),y+.5));
            if(y<3)links.add(link(a,cell(x,y+1,z),y+1));
        }
        double[] gas=new double[36];Arrays.fill(gas,ChamberFluid.AMBIENT);
        return new ChamberField(geometry,links,new double[36],gas,new double[links.size()],10);
    }
    private static ChamberField.Link link(int a,int b,double height){return new ChamberField.Link(a,b,height,1,1,0,0,ChamberFluid.AMBIENT,false,false);}
    private static double top(ChamberField f){double p=0;for(int x=0;x<3;x++)for(int z=0;z<3;z++)p+=f.pressure(cell(x,3,z));return p/9;}
    public static void run(){
        var low=cylinder();var full=cylinder();
        double initial=top(low);
        low.advance(.2);for(int i=0;i<4;i++)full.advance(.05);
        double lowPressure=top(low),fullPressure=top(full);
        double expected=ChamberFluid.AMBIENT*36/31.5;
        for(int i=0;i<300;i++)low.advance(.2);
        near(low.totalGas(),ChamberFluid.AMBIENT*36,1e-7,"Sealed cylinder conserves gas");
        near(low.totalWater(),0,1e-12,"Dry cylinder invents no water");
        for(int i=0;i<36;i++)near(low.pressure(i),expected,1e-4,"Local gas flux reaches Boyle equilibrium without flattening");
        double stored=ChamberFluid.AMBIENT*(36*Math.log(36/31.5)-4.5);
        System.out.printf(Locale.ROOT,"Cylinder gas audit (fixed half-entry): initial top P=%.6f, after 0.2s LOW=%.6f FULL=%.6f, equilibrium P=%.6f, piston gauge force=%.6f, isothermal relative free energy=%.6f. Not a moving-body/game validation.%n",initial,lowPressure,fullPressure,expected,9*(expected-ChamberFluid.AMBIENT),stored);
    }
    private static void near(double actual,double expected,double tolerance,String reason){if(!Double.isFinite(actual)||Math.abs(actual-expected)>tolerance)throw new AssertionError(reason+": "+actual+" != "+expected);}
}
