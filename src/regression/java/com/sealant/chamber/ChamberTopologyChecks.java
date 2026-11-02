package com.sealant.chamber;

import com.sealant.chamber.topology.*;
import com.sealant.chamber.physics.*;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import java.util.*;

public final class ChamberTopologyChecks {
    private static final UUID OWNER=new UUID(0,100),OTHER=new UUID(0,200);
    private static ChamberTopology.Node node(int id,double x0,double x1){return new ChamberTopology.Node(new UUID(0,id),OWNER,new AABB(x0,0,0,x1,2,2));}
    public static void run() {
        var a=node(1,0,2);var duplicate=node(2,0,2);
        var single=ChamberTopology.build(List.of(a)).getFirst();
        var same=ChamberTopology.build(List.of(a,duplicate)).getFirst();
        near(same.union().volume,8,"Duplicate chamber cannot add capacity");
        near(area(same),24,"Duplicate chamber cannot add pressure/leak surfaces");
        require(same.union().cells.size()==single.union().cells.size(),"Duplicate chamber cannot multiply physics work");
        var overlap=ChamberTopology.build(List.of(a,node(2,1,3))).getFirst();
        near(overlap.union().volume,12,"Overlap volume counted once");near(area(overlap),32,"Internal cut faces must be removed");
        var face=ChamberTopology.build(List.of(a,node(2,2,4))).getFirst();
        near(face.union().volume,16,"Shared face connects without adding overlap");near(area(face),40,"Shared face is not exterior");
        require(ChamberTopology.build(List.of(a,node(2,2.01,4))).size()==2,"A gap isolates components");
        require(ChamberTopology.build(List.of(a,new ChamberTopology.Node(new UUID(0,2),OWNER,new AABB(2,2,0,4,4,2)))).size()==2,"Edge-only contact has no flow aperture");
        require(ChamberTopology.build(List.of(a,new ChamberTopology.Node(new UUID(0,2),OTHER,a.box()))).size()==2,"Different sublevels never share a component");
        require(ChamberTopology.build(List.of(a,node(2,1,3),node(3,2.5,4.5))).size()==1,"Connectivity must be transitive");
        // An L union has exposed partial rectangles; uniform pressure must still cancel exactly.
        var l=ChamberTopology.build(List.of(a,new ChamberTopology.Node(new UUID(0,2),OWNER,new AABB(1,1,0,3,3,2)))).getFirst();
        var force=new ChamberWrench();
        for(var panel:l.union().panels)force.add(panel.point(),new Vector3d(panel.outward()).mul(-100*panel.area()),new Vector3d());
        near(force.force.length()+force.torque.length(),0,"Union boundary must be closed for uniform pressure");
        remap();lights();
        var random=new Random(42);var ramps=new ArrayList<ChamberFluid.Cell>();double capacity=0;
        for(int i=0;i<200;i++){double volume=.1+random.nextDouble();ramps.add(new ChamberFluid.Cell(random.nextDouble()*20,.05+random.nextDouble(),volume));capacity+=volume;}
        for(int i=1;i<100;i++) {
            double water=capacity*i/100;double surface=ChamberFluid.surfaceFor(ramps,water);
            near(ChamberFluid.volumeAt(ramps,surface),water,"Cached water profile agrees with direct rotated-cell ramp sum");
        }
        var overflow=new ChamberFluid();overflow.restore(10,100);
        overflow.advance(List.of(new ChamberFluid.Cell(1,1,2)),List.of(),10,.05);
        near(overflow.totalWater(),10,"Capacity shrink preserves pending liquid inventory");
        overflow.advance(List.of(new ChamberFluid.Cell(1,1,20)),List.of(),10,.05);
        near(overflow.totalWater(),10,"Pending liquid returns when volume is available");
        require(overflow.overflow()==0,"Expanded volume consumes pending liquid");
        System.out.println("Chamber topology/union/conservative remap/room-light regression checks passed.");
    }
    private static void remap() {
        var left=node(1,0,2);var right=node(2,3,5);var bridge=node(3,1,4);
        var joined=ChamberTopology.build(List.of(left,right,bridge)).getFirst();
        var old=new ChamberInventory.Snapshot(OWNER,Set.of(left.id(),right.id(),bridge.id()),joined.union().pieces,
                List.of(new ChamberInventory.Parcel(left.box(),3,1),new ChamberInventory.Parcel(right.box(),1,3)),4,200);
        var split=ChamberTopology.build(List.of(left,right));
        var parts=ChamberInventory.remap(List.of(old),split);
        near(parts.stream().mapToDouble(ChamberInventory.Amount::water).sum(),4,"Split cannot lose/duplicate water");
        near(parts.stream().mapToDouble(ChamberInventory.Amount::gasPV).sum(),200,"Split cannot lose/duplicate gas");
        near(parts.getFirst().water(),3,"Split respects spatial liquid distribution");near(parts.getFirst().gasPV(),50,"Gas uses dry-space distribution, not water weights");
        var snapshots=new ArrayList<ChamberInventory.Snapshot>();
        for(int i=0;i<split.size();i++)snapshots.add(new ChamberInventory.Snapshot(OWNER,Set.of(split.get(i).id()),split.get(i).union().pieces,List.of(),parts.get(i).water(),parts.get(i).gasPV()));
        var merged=ChamberInventory.remap(snapshots,List.of(joined)).getFirst();
        near(merged.water(),4,"Merge conserves water");near(merged.gasPV(),200,"Merge conserves gas");
        near(ChamberInventory.uncovered(left.box(),joined.union().pieces),0,"Fully overlapping new marker adds no fresh gas");
        near(ChamberInventory.uncovered(new AABB(4,0,0,6,2,2),joined.union().pieces),4,"Only truly new volume initializes fresh air");
    }
    private static void lights() {
        for(int sky=0;sky<=15;sky++)for(int block=0;block<=15;block++) {
            var light=new ChamberLightValue(sky,block,true);float encoded=light.shaderScale(0);
            near(Math.floor(encoded/2),block,"Shader block channel decode");
            require(Math.abs((encoded%2)-sky/15.0)<2e-6,"Sky channel must survive shader packing");
        }
        var root=new ChamberLightValue(15,15,false);near(root.shaderScale(.2f),.2f,"Root keeps native underwater sky attenuation");
        var room=new ChamberLightValue(4,8,true);
        int original=(10<<4)|(6<<20);require(room.packedBlockFloor(original)==original,"Room average must not dim an object's own torch");
        int dark=(2<<4)|(6<<20);require(((room.packedBlockFloor(dark)>>4)&15)==8,"Room provides independent block-light floor");
        require((room.packedBlockFloor(dark)>>20)==6,"Block ambient cannot overwrite sky bits");
    }
    private static double area(ChamberTopology.Group g){return g.union().panels.stream().mapToDouble(ChamberSampling.Panel::area).sum();}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void near(double actual,double expected,String message){if(!Double.isFinite(actual)||Math.abs(actual-expected)>1e-7)throw new AssertionError(message+": "+actual+" != "+expected);}
}
