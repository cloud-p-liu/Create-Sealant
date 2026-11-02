package com.sealant.chamber.physics;

import java.util.*;

/** Equivalence to the previous full-range hypot formula, including pressure-floor
 * transitions, strong compression, near cancellation, overflow and underflow. */
public final class ChamberEosChecks {
    private static volatile double sink;
    public static void run(){
        double worst=0;var random=new Random(20261103);
        for(int i=0;i<100000;i++){
            double c=Math.pow(10,-11+random.nextDouble()*20);
            double water=c*Math.pow(10,-8+random.nextDouble()*10);
            double gas=c*ChamberFluid.AMBIENT*Math.pow(10,-25+random.nextDouble()*30);
            if((i&7)==0)gas=0;
            if((i&15)==0)water=0;
            worst=Math.max(worst,compare(c,water,gas));
        }
        for(double c:new double[]{1e-13,1e-10,1,1e100,1e200})for(double w:new double[]{0,c*.5,c,c*2})for(double g:new double[]{0,1e-320,1e-200,1e-20,101.325,1e155,1e300})worst=Math.max(worst,compare(c,w,g));
        for(double c:new double[]{1e-10,.5,1,100}){
            double atCancellation=c*(ChamberField.BULK-ChamberFluid.AMBIENT)/ChamberField.BULK;
            for(double w:new double[]{Math.nextDown(atCancellation),atCancellation,Math.nextUp(atCancellation)})
                for(double g:new double[]{0,1e-320,1e-24,1e-10,1,100})worst=Math.max(worst,compare(c,w,g));
        }
        System.out.println("EOS finite sqrt / full-range hypot equivalence: maximum "+worst+" ULP across random, cancellation and extreme inputs.");
        // Isolated warm timing is diagnostic only, not a TPS assertion.
        double[] c=new double[4096],w=new double[4096],g=new double[4096];
        for(int i=0;i<c.length;i++){c[i]=.05+random.nextDouble();w[i]=c[i]*random.nextDouble()*1.1;g[i]=ChamberFluid.AMBIENT*c[i]*random.nextDouble();}
        for(int warm=0;warm<4;warm++){time(c,w,g,true);time(c,w,g,false);}
        long[] previous=new long[5],current=new long[5];
        for(int i=0;i<5;i++){previous[i]=time(c,w,g,true);current[i]=time(c,w,g,false);}
        Arrays.sort(previous);Arrays.sort(current);
        System.out.printf(Locale.ROOT,"Isolated EOS timing (not TPS): previous %.3fms, current %.3fms, median speedup %.2fx.%n",previous[2]/1e6,current[2]/1e6,(double)previous[2]/current[2]);
    }
    private static double compare(double c,double w,double g){
        double reference=reference(c,w,g),actual=ChamberField.pressure(c,w,g);
        if(Double.doubleToLongBits(reference)==Double.doubleToLongBits(actual))return 0;
        if(!Double.isFinite(reference) || !Double.isFinite(actual))throw new AssertionError("EOS range changed: "+c+" / "+w+" / "+g+" => "+reference+" vs "+actual);
        double ulps=Math.abs(reference-actual)/Math.ulp(reference);
        if(ulps>8)throw new AssertionError("EOS accuracy changed by "+ulps+" ULP: "+c+" / "+w+" / "+g);
        return ulps;
    }
    private static double reference(double capacity,double water,double gas){
        if(capacity<=1e-12)return ChamberField.MIN_PRESSURE;
        double k=ChamberField.BULK-ChamberFluid.AMBIENT,b=capacity*k-water*ChamberField.BULK-gas,c=gas*k;
        double root=Math.hypot(b,2*Math.sqrt(capacity*c));
        double p=b>=0?(c==0?0:2*c/(root+b)):(root-b)/(2*capacity);
        return Math.max(ChamberField.MIN_PRESSURE,p);
    }
    private static long time(double[] c,double[] w,double[] g,boolean previous){
        long start=System.nanoTime();double value=0;
        for(int repeat=0;repeat<64;repeat++)for(int i=0;i<c.length;i++)value+=previous?reference(c[i],w[i],g[i]):ChamberField.pressure(c[i],w[i],g[i]);
        sink=value;return System.nanoTime()-start;
    }
    private ChamberEosChecks(){}
}
