package com.sealant.chamber.physics;

import java.util.List;

/** Bounded, well-mixed isothermal gas + horizontal internal water, in Sable mass/length/time units.
 * Ambient 101.325 = SI atmospheric pressure / 1000; water density is 1.05 native mass/block^3.
 * No world fluid queries belong here: external pressure enters only through boundary ports.
 */
public final class ChamberFluid {
    public static final double AMBIENT=101.325, DENSITY=1.05;
    public record Cell(double height,double halfHeight,double volume) {}
    /** riseA/B are the absolute world-Y spans of the two rectangular panel edges.
     * They describe actual projected opening geometry, never a tolerance to force water out. */
    public record Port(double height,double area,double externalPressure,boolean wet,boolean upward,double riseA,double riseB) {
        public Port(double height,double area,double externalPressure,boolean wet,boolean upward){this(height,area,externalPressure,wet,upward,0,0);}
        public double wetFraction(double surface){return ChamberFaceCoverage.fraction(height,riseA,riseB,surface);}
        public double headIntegral(double surface){return ChamberFaceCoverage.head(height,riseA,riseB,surface);}
        public boolean airExposed(double surface){return !wet && wetFraction(surface)<1-1e-10;}
    }
    private double water, gasPV, overflow;
    private boolean initialized;
    public double totalWater(){return water+overflow;}
    public double overflow(){return overflow;}
    private double capacity, surface, pressure=AMBIENT;
    public double water(){return water;}
    public double gasPV(){return gasPV;}
    public double capacity(){return capacity;}
    public double surface(){return surface;}
    public double pressure(){return pressure;}
    public void restore(double water,double gasPV) {
        initialized=true;overflow=0;
        this.water=Double.isFinite(water)?Math.max(0,water):0;
        this.gasPV=Double.isFinite(gasPV)?Math.max(0,gasPV):0;
    }
    public void reset(){water=0;gasPV=0;overflow=0;initialized=false;}
    public static double fraction(double surface,Cell cell) {
        return Math.clamp((surface-cell.height()+cell.halfHeight())/(2*cell.halfHeight()),0,1);
    }
    public static double volumeAt(List<Cell> cells,double surface) {
        double sum=0;for(Cell cell:cells)sum+=fraction(surface,cell)*cell.volume();return sum;
    }
    public static double surfaceFor(List<Cell> cells,double water) {return new Profile(cells).surface(water);}
    /** Piecewise-linear sum of cell fill ramps. Build once; inverse queries are logarithmic,
     * rather than rescanning every cell in every nonlinear solver iteration. */
    private static final class Profile {
        final double[] height,volume;final int count;final double capacity;
        Profile(List<Cell> cells) {
            double[] event=new double[cells.size()*2],delta=new double[event.length];double total=0;int n=0;
            for(Cell cell:cells) {
                event[n]=cell.height()-cell.halfHeight();delta[n++]=cell.volume()/(2*cell.halfHeight());
                event[n]=cell.height()+cell.halfHeight();delta[n++]=-cell.volume()/(2*cell.halfHeight());total+=cell.volume();
            }
            capacity=total;height=new double[event.length];volume=new double[event.length];
            if(n==0){count=0;return;}
            sort(event,delta,0,n-1);double slope=0,filled=0,last=event[0];int points=0,i=0;
            while(i<n) {
                double h=event[i];filled=Math.clamp(filled+slope*(h-last),points==0?0:volume[points-1],capacity);
                height[points]=h;volume[points++]=filled;
                double change=0;while(i<n && event[i]==h)change+=delta[i++];
                slope=Math.max(0,slope+change);last=h;
            }
            count=points;volume[count-1]=capacity;
        }
        double surface(double water) {
            if(count==0)return 0;
            if(water<=0)return height[0]-1e-6;
            if(water>=capacity)return height[count-1];
            int lo=0,hi=count-1;
            while(lo<hi){int mid=(lo+hi)>>>1;if(volume[mid]<water)lo=mid+1;else hi=mid;}
            if(lo==0)return height[0];
            double span=volume[lo]-volume[lo-1];
            return span<=0?height[lo]:height[lo-1]+(water-volume[lo-1])/span*(height[lo]-height[lo-1]);
        }
        private static void sort(double[] h,double[] d,int left,int right) {
            int i=left,j=right;double pivot=h[(left+right)>>>1];
            while(i<=j) {
                while(h[i]<pivot)i++;while(h[j]>pivot)j--;
                if(i<=j){double v=h[i];h[i]=h[j];h[j]=v;v=d[i];d[i]=d[j];d[j]=v;i++;j--;}
            }
            if(left<j)sort(h,d,left,j);if(i<right)sort(h,d,i,right);
        }
    }
    public double at(double height,double gravity) {
        return pressure+(water>1e-8?DENSITY*gravity*Math.max(0,surface-height):0);
    }
    private static double flow(List<Port> ports,double surface,double water,double gasPressure,double gravity) {
        double flow=0;
        for(Port port:ports) {
            double inner=gasPressure+(water>1e-8?DENSITY*gravity*Math.max(0,surface-port.height()):0);
            double delta=port.externalPressure()-inner;
            if(port.wet() && delta>0){
                // Keep the existing external-water inlet model; this patch changes internal coverage.
                flow+=.15*port.area()*Math.sqrt(2*delta/DENSITY);
                continue;
            }
            if(water<=1e-8)continue;
            double fraction=port.wetFraction(surface);
            if(fraction<=0)continue;
            double head=port.headIntegral(surface)/fraction;
            double wetHeight=surface-head;
            double outside=port.externalPressure()+(port.wet()?DENSITY*gravity*(port.height()-wetHeight):0);
            double outDelta=gasPressure+DENSITY*gravity*head-outside;
            if(outDelta>0)flow-=.15*port.area()*fraction*Math.sqrt(2*outDelta/DENSITY);
        }
        return flow;
    }
    public void advance(List<Cell> cells,List<Port> ports,double gravity,double dt) {
        Profile profile=new Profile(cells);capacity=profile.capacity;
        if(capacity<=1e-8){overflow+=water;water=0;surface=0;pressure=AMBIENT;return;}
        double minimumGas=Math.max(1e-8,capacity*1e-4);
        double inventory=totalWater();
        water=Math.clamp(inventory,0,capacity-minimumGas);overflow=Math.max(0,inventory-water);
        if(!initialized){gasPV=AMBIENT*(capacity-water);initialized=true;}
        // Backward Euler on water V: V_new - V_old = dt * Q(P(V_new), surface(V_new)).
        // Q decreases monotonically with internal pressure. A bracketed solve cannot jump past
        // equilibrium when the gas pocket is much smaller than 1% of total chamber capacity.
        for(int step=0;step<8;step++) {
            surface=profile.surface(water);
            boolean vent=false;
            for(Port port:ports)if(port.airExposed(surface)){vent=true;break;}
            if(vent)gasPV=AMBIENT*(capacity-water);
            pressure=gasPV/Math.max(minimumGas,capacity-water);
            // One relaxation per step, independent of how many panels split the same opening.
            double bubblePressure=Double.POSITIVE_INFINITY;
            for(Port port:ports)if(port.wet() && port.upward() && port.height()>surface)
                bubblePressure=Math.min(bubblePressure,port.externalPressure());
            if(pressure>bubblePressure)gasPV+=(bubblePressure*(capacity-water)-gasPV)*Math.min(1,dt/8*4);
            double currentPressure=gasPV/Math.max(minimumGas,capacity-water);
            if(Math.abs(flow(ports,surface,water,currentPressure,gravity))<1e-12)continue;
            double old=water,lo=0,hi=capacity-minimumGas;
            for(int iteration=0;iteration<38;iteration++) {
                double candidate=(lo+hi)*.5;
                double height=profile.surface(candidate);
                // An already exposed air opening stays at ambient during the implicit
                // trial too; freezing GasPV here would invent suction as water drains.
                boolean trialVent=false;
                if(vent)for(Port port:ports)if(port.airExposed(height)){trialVent=true;break;}
                double p=trialVent?AMBIENT:gasPV/Math.max(minimumGas,capacity-candidate);
                double flow=flow(ports,height,candidate,p,gravity);
                if(candidate-old-dt/8*flow>0)hi=candidate;else lo=candidate;
            }
            water=Math.clamp((lo+hi)*.5,0,capacity-minimumGas);
        }
        surface=profile.surface(water);
        for(Port port:ports)if(port.airExposed(surface)){gasPV=AMBIENT*(capacity-water);break;}
        pressure=gasPV/Math.max(minimumGas,capacity-water);
    }
}
