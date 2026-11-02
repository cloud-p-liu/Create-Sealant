package com.sealant.chamber.physics;

import java.util.*;

/** Local two-phase finite volumes. No connected-component/room equilibrium is used.
 * Water stores reference volume (= mass / reference density); gas stores isothermal PV mass.
 * Weak liquid compressibility supplies pressure even in a completely water-filled cell.
 * Shared-face backward Euler exchanges conserve both inventories exactly, including inertia.
 */
public final class ChamberField {
    public static final double BULK=10000,MIN_PRESSURE=1e-5;
    // Internal sub-grid exchange only. External pressure/leak/vent rates are unchanged.
    // The former .25 excessively throttled redistribution after fluid entered the domain.
    private static final double INTERNAL_DISPLACEMENT=1.0;
    public record Geometry(double capacity,double low,double high) {}
    /** Positive flux a -> b; -1 is the external reservoir. Water flux is reference volume/sec. */
    public record Link(int a,int b,double height,double area,double length,double riseA,double riseB,
                       double outsidePressure,boolean outsideWet,boolean bubbleOut) {}
    private final Geometry[] geometry;
    private final List<Link> links;
    private final double[] water,gas,pressure,surface,liquid,flux;
    private final double gravity;
    private double displacedVolume,advancedTime,restResidualPerTick;
    private static final int REST_UPDATES=3;
    private static final double WATER_PRESSURE_EPS=1e-3,GAS_PRESSURE_EPS=1e-5;
    // Whole-group absolute exchange budget, normalized to one 0.05s game tick.
    public static final double REST_VOLUME_PER_TICK=0.000400;
    private int quietUpdates;
    private boolean sleeping,skippedExchange;
    public record RestReport(double waterPerTick,double gasPerTick,double displacementPerTick,int exteriorLinks,
                             double waterPressureGap,double gasPressureGap,String reason) {}
    private RestReport restReport=new RestReport(0,0,0,0,0,0,"尚未檢查");
    public RestReport restReport(){return restReport;}
    public boolean sleeping(){return sleeping;}
    public boolean restCandidate(){return quietUpdates>0;}
    public boolean skippedExchange(){return skippedExchange;}
    public void wake(){quietUpdates=0;sleeping=false;skippedExchange=false;}
    /** Carry only the consecutive quiet-observation count within one runtime group.
     * Body count, poses and geometry identities are NOT sleep conditions. The new
     * field's actual openings, pressures and phase fluxes are checked in advance
     * before any exchange is skipped. No physical inventory or sleeping flag is copied. */
    public void inheritRest(ChamberField previous){
        if(previous!=null && geometry.length==previous.geometry.length && gravity==previous.gravity)
            quietUpdates=previous.quietUpdates;
    }
    private RestReport evaluateRest(double dt){
        for(int i=0;i<water.length;i++)if(!Double.isFinite(water[i]+gas[i]+pressure[i]+surface[i]+liquid[i])){
            return new RestReport(0,0,0,0,0,0,"非有限狀態");
        }
        double waterRateSum=0,gasRateSum=0,displacementRateSum=0,waterGap=0,gasGap=0;int exterior=0;
        for(int i=0;i<links.size();i++){
            var f=links.get(i);if(f.a<0 || f.b<0)exterior++;
            if(!Double.isFinite(flux[i])){return new RestReport(0,0,0,exterior,0,0,"非有限通量");}
            double ca=wet(f.a,f),cb=wet(f.b,f),height=waterHeight(f,ca,cb);
            if(ca+cb>0)waterGap=Math.max(waterGap,Math.abs(p(f.a,f,height)-p(f.b,f,height)));
            double pa=f.a>=0?pressure[f.a]:f.outsidePressure,pb=f.b>=0?pressure[f.b]:f.outsidePressure;
            if(Math.min(1-ca,1-cb)>0)gasGap=Math.max(gasGap,Math.abs(pa-pb));
            // Stored water momentum needs a currently wet donor to transport mass.
            // Preserve history; a predicted reversal is still counted by waterRate.
            double oldWater=(flux[i]>=0?ca>0:cb>0)?Math.abs(flux[i]):0;
            waterRateSum+=Math.max(oldWater,Math.abs(waterRate(f,flux[i],dt/8)));
            gasRateSum+=Math.abs(gasRate(f))/Math.max(MIN_PRESSURE,Math.min(pa,pb));
            displacementRateSum+=displacementRate(f);
        }
        double w=.05*waterRateSum,g=.05*gasRateSum,d=.05*displacementRateSum;
        double residual=w+g+d;
        String reason=exterior>0?"外部開口":waterGap>WATER_PRESSURE_EPS?"水頭壓差":gasGap>GAS_PRESSURE_EPS?"氣體壓差":
                !Double.isFinite(residual)?"非有限殘差":residual>REST_VOLUME_PER_TICK?"交換殘差超標":"靜止確認";
        return new RestReport(w,g,d,exterior,waterGap,gasGap,reason);
    }
    private boolean atRest(double dt){
        restReport=evaluateRest(dt);
        restResidualPerTick=restReport.waterPerTick+restReport.gasPerTick+restReport.displacementPerTick;
        return restReport.reason.equals("靜止確認");
    }
    public RestReport currentRestReport(){return evaluateRest(advancedTime>0?advancedTime:.05);}
    public double restResidualPerTick(){return restResidualPerTick;}
    public ChamberField(List<Geometry> geometry,List<Link> links,double[] water,double[] gas,double[] flux,double gravity){
        this.geometry=geometry.toArray(Geometry[]::new);this.links=List.copyOf(links);this.water=water.clone();this.gas=gas.clone();this.gravity=gravity;
        if(water.length!=geometry.size() || gas.length!=water.length || flux.length!=links.size())throw new IllegalArgumentException("Field dimensions");
        this.flux=flux.clone();pressure=new double[water.length];surface=new double[water.length];liquid=new double[water.length];
        for(int i=0;i<water.length;i++){
            if(!Double.isFinite(water[i]+gas[i])||water[i]<0||gas[i]<0)throw new IllegalArgumentException("Invalid field inventory");refresh(i);
        }
        for(var link:links)if(link.a< -1 || link.b< -1 || link.a>=water.length || link.b>=water.length || link.a==link.b || link.area<=0 || link.length<=0)throw new IllegalArgumentException("Invalid field face");
    }
    public static double pressure(double capacity,double water,double gas){
        if(capacity<=1e-12)return MIN_PRESSURE;
        // capacity*P^2 + [capacity*(B-P0)-water*B-gas]*P - gas*(B-P0) = 0.
        double k=BULK-ChamberFluid.AMBIENT,b=capacity*k-water*BULK-gas,c=gas*k;
        double other=2*Math.sqrt(capacity*c),squared=b*b+other*other;
        // Normal game values need only a hardware sqrt. Keep hypot's scaling for
        // overflow/subnormal cases; never trade floating-point range for speed.
        double root=Double.isFinite(squared) && (squared>=Double.MIN_NORMAL || b==0 && other==0)
                ?Math.sqrt(squared):Math.hypot(b,other);
        double p=b>=0?(c==0?0:2*c/(root+b)):(root-b)/(2*capacity);
        return Math.max(MIN_PRESSURE,p);
    }
    private void refresh(int i){
        var cell=geometry[i];pressure[i]=pressure(cell.capacity,water[i],gas[i]);
        liquid[i]=Math.clamp(water[i]*BULK/(BULK+pressure[i]-ChamberFluid.AMBIENT),0,cell.capacity);
        surface[i]=cell.low+(cell.high-cell.low)*(cell.capacity>0?liquid[i]/cell.capacity:0);
    }
    public double water(int i){return water[i];}
    public double gas(int i){return gas[i];}
    public double pressure(int i){return pressure[i];}
    public double surface(int i){return surface[i];}
    public double liquid(int i){return liquid[i];}
    public double air(int i){return Math.clamp(gas[i]/pressure[i],0,geometry[i].capacity); }
    public double capacity(int i){return geometry[i].capacity;}
    public double[] flux(){return flux.clone();}
    public double at(int i,double height){return pressure[i]+(liquid[i]>0?ChamberFluid.DENSITY*gravity*Math.max(0,surface[i]-height):0);}
    public double displacedVolume(){return displacedVolume;}
    public double displacementPerTick(){return advancedTime>0?displacedVolume*.05/advancedTime:0;}
    public double totalWater(){return Arrays.stream(water).sum();}
    public double totalGas(){return Arrays.stream(gas).sum();}
    private double p(int cell,Link face,double height){
        return cell>=0?at(cell,height):face.outsidePressure+(face.outsideWet?ChamberFluid.DENSITY*gravity*(face.height-height):0);
    }
    private double wet(int cell,Link face){
        if(cell<0)return face.outsideWet?1:0;
        if(liquid[cell]<=1e-14)return 0;
        return ChamberFaceCoverage.fraction(face.height,face.riseA,face.riseB,surface[cell]);
    }
    private double waterHeight(Link face,double ca,double cb){
        double height=face.height;
        if(face.a>=0 && ca>0 && ca<1)height=Math.min(height,surface[face.a]-ChamberFaceCoverage.head(face.height,face.riseA,face.riseB,surface[face.a])/ca);
        if(face.b>=0 && cb>0 && cb<1)height=Math.min(height,surface[face.b]-ChamberFaceCoverage.head(face.height,face.riseA,face.riseB,surface[face.b])/cb);
        return height;
    }
    private double waterRate(Link face,double oldFlux,double dt){
        double height=waterHeight(face,wet(face.a,face),wet(face.b,face));
        double delta=p(face.a,face,height)-p(face.b,face,height);
        double q=(oldFlux+dt*face.area*delta/(ChamberFluid.DENSITY*face.length))/(1+dt*(2+2*Math.abs(oldFlux)/face.area));
        // A pressure reversal may change the donor; do not transport water out of a dry cell.
        return q*(q>=0?wet(face.a,face):wet(face.b,face));
    }
    private double gasRate(Link face){
        double pa=face.a>=0?pressure[face.a]:face.outsidePressure,pb=face.b>=0?pressure[face.b]:face.outsidePressure;
        double dryA=face.a>=0?1-wet(face.a,face):face.outsideWet?0:1;
        double dryB=face.b>=0?1-wet(face.b,face):face.outsideWet?0:1;
        double aperture=Math.min(dryA,dryB);
        // Bubble release is one local face flux, never one global pressure reset.
        if(face.b<0 && face.outsideWet && face.bubbleOut && pa>pb)aperture=dryA;
        if(face.a<0 && face.outsideWet && face.bubbleOut && pb>pa)aperture=dryB;
        return 2*face.area/face.length*aperture*(pa-pb);
    }
    private void setTrial(Link f,double a,double b,double value,boolean gasPhase){
        double[] field=gasPhase?gas:water;
        if(f.a>=0){field[f.a]=Math.max(0,a-value);refresh(f.a);}
        if(f.b>=0){field[f.b]=Math.max(0,b+value);refresh(f.b);}
    }
    private double exchange(Link f,double dt,double previous,boolean gasPhase){
        double[] field=gasPhase?gas:water;
        double a=f.a>=0?field[f.a]:0,b=f.b>=0?field[f.b]:0;
        double initial=gasPhase?gasRate(f):waterRate(f,previous,dt);
        if(Math.abs(initial)<1e-14)return 0;
        // Bracket only the predicted transfer direction and amount. This avoids creating
        // tiny reverse flows when a low-water exchange is far smaller than a whole cell.
        double predicted=dt*initial;
        double lo=predicted<0?-Math.min(f.b>=0?b:Double.POSITIVE_INFINITY,-predicted):0;
        double hi=predicted>0?Math.min(f.a>=0?a:Double.POSITIVE_INFINITY,predicted):0;
        for(int iteration=0;iteration<28;iteration++){
            double value=(lo+hi)*.5;setTrial(f,a,b,value,gasPhase);
            double rate=gasPhase?gasRate(f):waterRate(f,previous,dt);
            if(value-dt*rate>0)hi=value;else lo=value;
        }
        double value=(lo+hi)*.5;setTrial(f,a,b,value,gasPhase);
        return value/dt;
    }
    /** Sub-grid counter-current displacement through an INTERNAL open face.
     * A resolved face may be covered by water on one side and gas on the other. Requiring
     * overlapping gas apertures then creates an artificial per-cell gas lock. A paired
     * volume exchange lets dense fluid descend/spread while displaced gas takes its place.
     * Both phase masses are transferred together; no gas is invented or pressure averaged.
     * Exterior faces deliberately use their own pressure/vent boundary conditions instead.
     */
    private double displacementRate(Link face){
        if(face.a<0 || face.b<0 || gravity<=0)return 0;
        int donor=surface[face.a]>=surface[face.b]?face.a:face.b,receiver=donor==face.a?face.b:face.a;
        double contact=wet(donor,face)*(1-wet(receiver,face));
        if(liquid[donor]<=1e-14 || gas[receiver]/pressure[receiver]<=1e-14 || contact<=1e-12)return 0;
        double head=surface[donor]-surface[receiver];
        return head<=1e-12?0:INTERNAL_DISPLACEMENT*face.area*contact*Math.sqrt(2*gravity*head);
    }
    private void displace(Link face,double dt){
        if(face.a<0 || face.b<0 || gravity<=0)return;
        int donor=surface[face.a]>=surface[face.b]?face.a:face.b,receiver=donor==face.a?face.b:face.a;
        double contact=wet(donor,face)*(1-wet(receiver,face));
        double gasVolume=gas[receiver]/pressure[receiver];
        if(liquid[donor]<=1e-14 || gasVolume<=1e-14 || contact<=1e-12)return;
        double head=surface[donor]-surface[receiver];if(head<=1e-12)return;
        double wd=water[donor],wr=water[receiver],gd=gas[donor],gr=gas[receiver];
        double waterDensity=(BULK+pressure[donor]-ChamberFluid.AMBIENT)/BULK,gasDensity=pressure[receiver];
        double initial=INTERNAL_DISPLACEMENT*face.area*contact*Math.sqrt(2*gravity*head);
        double lo=0,hi=Math.min(Math.min(wd/waterDensity,gr/gasDensity),dt*initial);
        for(int iteration=0;iteration<28;iteration++){
            double volume=(lo+hi)*.5;
            water[donor]=Math.max(0,wd-volume*waterDensity);water[receiver]=wr+volume*waterDensity;
            gas[donor]=gd+volume*gasDensity;gas[receiver]=Math.max(0,gr-volume*gasDensity);
            refresh(donor);refresh(receiver);
            double aperture=wet(donor,face)*(1-wet(receiver,face));
            double rate=INTERNAL_DISPLACEMENT*face.area*aperture*Math.sqrt(2*gravity*Math.max(0,surface[donor]-surface[receiver]));
            if(volume-dt*rate>0)hi=volume;else lo=volume;
        }
        double volume=(lo+hi)*.5;displacedVolume+=volume;
        water[donor]=Math.max(0,wd-volume*waterDensity);water[receiver]=wr+volume*waterDensity;
        gas[donor]=gd+volume*gasDensity;gas[receiver]=Math.max(0,gr-volume*gasDensity);
        refresh(donor);refresh(receiver);
    }
    public void advance(double dt){
        skippedExchange=false;
        if(dt<=0)return;
        if(!Double.isFinite(dt))throw new IllegalArgumentException("Invalid field dt");
        displacedVolume=0;advancedTime=dt;
        if(atRest(dt)){
            quietUpdates=Math.min(REST_UPDATES,quietUpdates+1);
            if(quietUpdates>=REST_UPDATES){sleeping=true;skippedExchange=true;return;}
        }else{quietUpdates=0;sleeping=false;}
        for(int step=0;step<8;step++){
            double sub=dt/8;
            // Alternate face traversal to reduce directional bias without nonlocal averaging.
            for(int k=0;k<links.size();k++){
                int i=(step&1)==0?k:links.size()-1-k;var link=links.get(i);
                displace(link,sub);
                exchange(link,sub,0,true);
                flux[i]=exchange(link,sub,flux[i],false);
            }
        }
        for(int i=0;i<water.length;i++)if(!Double.isFinite(water[i]+gas[i]+pressure[i]))throw new IllegalArgumentException("Non-finite field state");
    }
}
