package com.sealant.chamber.physics;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.*;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import java.util.*;

public final class ChamberBlockVolumeChecks {
    private static final AABB UNIT=new AABB(0,0,0,1,1,1);
    public static void run(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        near(ChamberBlockVolumes.builtin(Blocks.STONE.defaultBlockState()),1,"Full block default");
        near(ChamberBlockVolumes.builtin(Blocks.TORCH.defaultBlockState()),1,"Non-full collision still defaults to one");
        near(ChamberBlockVolumes.builtin(Blocks.AIR.defaultBlockState()),0,"Air exception");
        near(ChamberBlockVolumes.builtin(Blocks.WATER.defaultBlockState()),0,"World fluid is not solid occupancy");
        for(var block:List.of(Blocks.SOUL_SAND,Blocks.HONEY_BLOCK))for(var state:block.getStateDefinition().getPossibleStates()){
            near(ChamberBlockVolumes.builtin(state),1,"Explicit full-volume block exception");
            near(ChamberBlockVolumes.solid(state,List.of()),1,"Full-volume exception without configured overrides");
        }
        for(var state:Blocks.SNOW.getStateDefinition().getPossibleStates()){
            near(ChamberBlockVolumes.builtin(state),0,"Every snow layer state has zero declared volume");
            near(ChamberBlockVolumes.solid(state,List.of()),0,"Snow layer zero-volume exception without configured overrides");
        }
        near(ChamberBlockVolumes.builtin(Blocks.SNOW_BLOCK.defaultBlockState()),1,"Snow block retains default full volume");
        for(var block:List.of(Blocks.KELP,Blocks.KELP_PLANT))for(var state:block.getStateDefinition().getPossibleStates()){
            near(ChamberBlockVolumes.builtin(state),0,"All kelp ages and plant states are explicit zero-volume exceptions");
            near(ChamberBlockVolumes.solid(state,List.of()),0,"Kelp cannot reduce field capacity through root-world overlap");
            for(var d:Direction.values())near(open(state,d,4),16,"Kelp collision leaves every passage open");
        }
        near(ChamberBlockVolumes.solid(Blocks.KELP.defaultBlockState(),List.of("minecraft:kelp=0.25")),.25,"Explicit server override still wins over kelp exception");
        near(ChamberBlockVolumes.solid(Blocks.KELP.defaultBlockState(),List.of()),0,"Removing override restores zero-volume kelp");
        near(ChamberFacePassage.resolution(com.sealant.config.SealantServerConfig.Simulation.FIELD_LOW),2,"Low uses 2x2");
        near(ChamberFacePassage.resolution(com.sealant.config.SealantServerConfig.Simulation.FIELD_FULL),4,"Full uses 4x4");
        for(int resolution:new int[]{2,4}){
            int panels=resolution*resolution;
        for(var type:SlabType.values()){
            var state=Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,type);
            near(ChamberBlockVolumes.builtin(state),type==SlabType.DOUBLE?1:.5,"Slab state volume");
            near(open(state,Direction.DOWN,resolution),type==SlabType.TOP?panels:0,"Slab bottom passage");
            near(open(state,Direction.UP,resolution),type==SlabType.BOTTOM?panels:0,"Slab top passage");
            for(var d:Direction.Plane.HORIZONTAL)near(open(state,d,resolution),type==SlabType.DOUBLE?0:panels/2,"Slab side passage");
        }
        for(var facing:Direction.Plane.HORIZONTAL)for(var half:Half.values())for(var shape:StairsShape.values()){
            var state=Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,facing).setValue(StairBlock.HALF,half).setValue(StairBlock.SHAPE,shape);
            double volume=switch(shape){case STRAIGHT->.75;case INNER_LEFT,INNER_RIGHT->.875;case OUTER_LEFT,OUTER_RIGHT->.625;};
            near(ChamberBlockVolumes.builtin(state),volume,"Stair state volume");
            near(open(state,half==Half.BOTTOM?Direction.DOWN:Direction.UP,resolution),0,"Stair base seals face");
            near(open(state,half==Half.BOTTOM?Direction.UP:Direction.DOWN,resolution),(1-volume)*panels*2,"Stair exposed step opening");
            // Rotation and top/bottom mirroring must preserve the total opening count.
            int total=0;for(var d:Direction.values())total+=open(state,d,resolution);
            near(total,(1-volume)*panels*6,"All stair orientations/halves/corners preserve rotated passage");
        }
        var lower=Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.BOTTOM);
        var upper=lower.setValue(SlabBlock.TYPE,SlabType.TOP);
        near(shared(lower,lower,resolution),panels/2,"Aligned openings pass");
        near(shared(lower,upper,resolution),0,"Opposite half openings do not connect");
        near(shared(Blocks.AIR.defaultBlockState(),Blocks.STONE.defaultBlockState(),resolution),0,"Either solid side blocks");
        }
        var lower=Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.BOTTOM);
        var upper=lower.setValue(SlabBlock.TYPE,SlabType.TOP);
        var rules=List.of("minecraft:oak_slab=0.2","minecraft:oak_slab[type=top]=0.7");
        near(ChamberBlockVolumes.solid(lower,rules),.2,"Block override");near(ChamberBlockVolumes.solid(upper,rules),.7,"State override");
        near(ChamberBlockVolumes.solid(lower,List.of()),.5,"Config change invalidates cache");
        check(!ChamberBlockVolumes.validRule("minecraft:stone=1.1"),"Out-of-range rule rejected");
        check(!ChamberBlockVolumes.validRule("minecraft:stone=NaN"),"Nonfinite rule rejected");
        try{ChamberBlockVolumes.solid(lower,List.of("minecraft:oak_slab[type=wrong]=0.2"));throw new AssertionError("Invalid state accepted");}catch(IllegalArgumentException expected){}
        near(ChamberBlockVolumes.capacity(.5,.125),.375,"Clipped declared occupancy");
        // Capacity changes EOS pressure while a closed face still permits no exchange.
        double gas=ChamberFluid.AMBIENT*.25;
        check(ChamberField.pressure(.25,0,gas)>ChamberField.pressure(.5,0,gas),"Declared capacity controls pressure");
        var field=new ChamberField(List.of(new ChamberField.Geometry(.25,0,1),new ChamberField.Geometry(.5,0,1)),List.of(),new double[]{.1,0},new double[]{gas,gas},new double[0],10);
        field.advance(.2);near(field.water(0),.1,"No face means no water flow");near(field.water(1),0,"Closed neighbor receives nothing");
        double fast=transfer(.25),slow=transfer(.5);
        check(fast>slow && slow>0,"Same open face: smaller capacity raises the same water inventory higher and increases head-driven transfer");
        var fragments=new java.util.ArrayList<AABB>(List.of(UNIT));
        for(var shape:shapes(lower)){var next=new java.util.ArrayList<AABB>();for(var part:fragments)next.addAll(com.sealant.chamber.topology.ChamberUnion.subtract(part,shape));fragments=next;}
        var targets=fragments.stream().map(b->new ChamberGridInventory.Target(0,b)).toList();
        var assigned=ChamberGridInventory.assign(List.of(new ChamberGridState.Cell(UNIT,1,2)),targets,1,List.of(UNIT));
        near(assigned.water()[0],.5,"New slab retains only same-position fluid");
        near(assigned.parked().stream().mapToDouble(ChamberGridState.Cell::water).sum(),.5,"Blocked half remains parked");
        var again=ChamberGridInventory.assign(assigned.parked(),targets,1,List.of(UNIT));
        near(again.water()[0],0,"Parked fluid cannot repeatedly leak through new slab");
        System.out.println("Declared block volumes, 40 stair states at 2x2 and 4x4, slab passages, state overrides and capacity/EOS checks passed.");
    }
    private static double transfer(double capacity){
        var link=new ChamberField.Link(0,1,.05,.25,1,0,0,ChamberFluid.AMBIENT,false,false);
        double gas=ChamberFluid.AMBIENT*(capacity-.1),otherGas=ChamberFluid.AMBIENT*.5;
        var field=new ChamberField(List.of(new ChamberField.Geometry(capacity,0,1),new ChamberField.Geometry(.5,0,1)),List.of(link),new double[]{.1,0},new double[]{gas,otherGas},new double[1],10);
        field.advance(.05);
        near(field.totalWater(),.1,"Open-face water conservation");near(field.totalGas(),gas+otherGas,"Open-face gas conservation");
        check(field.water(0)>=0 && field.water(1)>=0,"Open-face nonnegative water");return field.water(1);
    }
    private static List<AABB> shapes(BlockState state){return state.getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).toAabbs();}
    private static boolean solid(List<AABB> shapes,Vector3d p){return shapes.stream().anyMatch(b->b.contains(p.x,p.y,p.z));}
    private static int open(BlockState state,Direction direction,int resolution){
        int axis=direction.getAxis().ordinal(),sign=direction.getAxisDirection()==Direction.AxisDirection.POSITIVE?1:-1;
        double plane=sign>0?1:0;int count=0;var shapes=shapes(state);
        for(int i=0;i<resolution;i++)for(int j=0;j<resolution;j++){
            var p=ChamberFacePassage.point(UNIT,axis,plane,i,j,resolution);p.setComponent(axis,plane-sign*ChamberFacePassage.SIDE_OFFSET);
            if(!solid(shapes,p))count++;
        }return count;
    }
    private static int shared(BlockState left,BlockState right,int resolution){
        int count=0;var a=shapes(left);var b=shapes(right);
        for(int i=0;i<resolution;i++)for(int j=0;j<resolution;j++){
            var p=ChamberFacePassage.point(UNIT,0,1,i,j,resolution);var q=new Vector3d(p);p.x=1-ChamberFacePassage.SIDE_OFFSET;q.x=ChamberFacePassage.SIDE_OFFSET;
            if(!solid(a,p)&&!solid(b,q))count++;
        }return count;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void near(double actual,double expected,String message){check(Math.abs(actual-expected)<1e-9,message+": "+actual+" != "+expected);}
}
