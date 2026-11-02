package com.sealant.chamber;

import com.sealant.chamber.physics.*;
import com.sealant.chamber.topology.*;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Geometry and spatial-state regressions. Dynamic transport is checked by ChamberFieldChecks. */
public final class ChamberCellGridChecks {
    private static final UUID ID=new UUID(0,1);
    public static void run(){
        var grid=grid(new AABB(0,0,0,4,3,3));
        require(grid.cells.size()==36,"Unit grid count");
        near(grid.cells.stream().mapToDouble(c->ChamberUnion.volume(c.box())).sum(),36,"Grid domain volume");
        double outer=grid.faces.stream().filter(f->f.a()<0||f.b()<0).mapToDouble(ChamberCellGrid.Face::area).sum();near(outer,66,"Exterior faces cover domain once");
        var cut=grid(new AABB(.25,.125,.5,2.75,1.875,2.5));
        near(cut.cells.stream().mapToDouble(c->ChamberUnion.volume(c.box())).sum(),8.75,"Partial boundary cells preserve union volume");
        // An L union must not grow a bounding-box shortcut through its missing corner.
        var union=new ChamberUnion(List.of(new ChamberTopology.Node(ID,ID,new AABB(0,0,0,2,1,1)),new ChamberTopology.Node(new UUID(0,2),ID,new AABB(0,0,0,1,1,2))));
        var l=new ChamberCellGrid(union.pieces);near(l.cells.stream().mapToDouble(c->ChamberUnion.volume(c.box())).sum(),3,"L domain keeps missing corner empty");
        inventory();
        traction();
        System.out.println("Chamber block-grid geometry, spatial inventory and boundary-pressure checks passed.");
    }
    private static void traction(){
        var pose=new dev.ryanhcode.sable.companion.math.Pose3d();var force=new org.joml.Vector3d();
        double rhoG=ChamberFluid.DENSITY*10;
        for(var face:new ChamberSampling(new AABB(-1.5,-1.5,-1.5,1.5,1.5,1.5)).panels)
            force.add(ChamberWrench.pressureForce(face.outward(),face.area(),rhoG*(10-face.point().y),0));
        for(var face:new ChamberSampling(new AABB(-.5,-.5,-.5,.5,.5,.5)).panels)
            force.add(ChamberWrench.pressureForce(face.outward(),face.area(),0,20000+rhoG*Math.max(0,-face.point().y)));
        near(force.y,rhoG*(27-.5),"Outer displacement minus contained-water weight; gas baseline cannot propel a closed vessel");
        near(force.x,0,"Grid traction no spurious lateral pressure force");near(force.z,0,"Grid traction symmetric lateral pressure");
        var left=ChamberWrench.pressureForce(new org.joml.Vector3d(1,0,0),2,0,25);
        var right=ChamberWrench.pressureForce(new org.joml.Vector3d(-1,0,0),2,0,10);
        near(left.add(right).x,30,"Partition traction uses pressure difference between rooms");
    }
    private static void inventory(){
        var left=new AABB(0,0,0,1,1,1);var wall=new AABB(1,0,0,2,1,1);var right=new AABB(2,0,0,3,1,1);
        var old=List.of(new ChamberGridState.Cell(left,.8,12),new ChamberGridState.Cell(wall,.4,7),new ChamberGridState.Cell(right,.1,30));
        var result=ChamberGridInventory.assign(old,List.of(new ChamberGridInventory.Target(0,left),new ChamberGridInventory.Target(1,right)),2,List.of(new AABB(0,0,0,3,1,1)));
        near(result.water()[0],.8,"Wall cannot move left water to right room");near(result.water()[1],.1,"Dry room keeps own water");
        near(result.parked().stream().mapToDouble(ChamberGridState.Cell::water).sum(),.4,"Newly blocked water stays spatially parked");
        near(result.gas()[0],12,"Separated gas inventory");near(result.gas()[1],30,"Independent gas inventory");
        var restored=ChamberGridState.read(ChamberGridState.write(old));
        near(restored.stream().mapToDouble(ChamberGridState.Cell::water).sum(),1.3,"NBT round trip water");
        near(restored.stream().mapToDouble(ChamberGridState.Cell::gasPV).sum(),49,"NBT round trip gas");
        var merged=ChamberGridInventory.assign(old,List.of(new ChamberGridInventory.Target(0,left),new ChamberGridInventory.Target(0,wall),new ChamberGridInventory.Target(0,right)),1,List.of(new AABB(0,0,0,3,1,1)));
        near(merged.water()[0],1.3,"Opening and merging conserves water");near(merged.gas()[0],49,"Opening and merging conserves gas");
        var half=new AABB(0,0,0,.5,1,1);
        var split=ChamberGridInventory.assign(List.of(new ChamberGridState.Cell(left,.8,12)),List.of(new ChamberGridInventory.Target(0,half)),1,List.of(left));
        near(split.water()[0],.4,"Partial occupancy splits inventory by volume");
        var again=ChamberGridInventory.assign(split.parked(),List.of(new ChamberGridInventory.Target(0,half)),1,List.of(left));
        near(again.water()[0],0,"Parked blocked contents cannot leak into free half on repeated ticks");
        near(again.parked().stream().mapToDouble(ChamberGridState.Cell::water).sum(),.4,"Repeated remap retains parked inventory");
    }
    private static ChamberCellGrid grid(AABB box){return new ChamberCellGrid(List.of(new ChamberUnion.Piece(ID,box)));}
    private static void near(double a,double b,String m){if(!Double.isFinite(a)||Math.abs(a-b)>1e-8)throw new AssertionError(m+": "+a+" != "+b);}
    private static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
}
