package com.sealant.chamber.physics;

/** Allocation-free horizontal clipping integrals of a rectangular face (including tilted faces). */
public final class ChamberFaceCoverage {
    public static double fraction(double height,double riseA,double riseB,double surface){
        double a=Math.min(riseA,riseB),b=Math.max(riseA,riseB),span=a+b;
        if(span<1e-12)return surface>height?1:0;
        double x=surface-height+span/2;if(x<=0)return 0;if(x>=span)return 1;
        boolean upper=x>span/2;if(upper)x=span-x;
        double f=a<1e-12?x/b:x<=a?x*x/(2*a*b):(x-a/2)/b;
        return Math.clamp(upper?1-f:f,0,1);
    }
    public static double head(double height,double riseA,double riseB,double surface){
        double a=Math.min(riseA,riseB),b=Math.max(riseA,riseB),span=a+b;
        if(span<1e-12)return Math.max(0,surface-height);
        double x=surface-height+span/2;if(x<=0)return 0;if(x>=span)return surface-height;
        boolean upper=x>span/2;if(upper)x=span-x;
        double head=a<1e-12?x*x/(2*b):x<=a?x*x*x/(6*a*b):(x*x/2-a*x/2+a*a/6)/b;
        return upper?surface-height+head:head;
    }
    private ChamberFaceCoverage(){}
}
