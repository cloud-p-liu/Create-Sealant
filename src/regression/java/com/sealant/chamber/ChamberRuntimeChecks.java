package com.sealant.chamber;

import com.sealant.chamber.runtime.ChamberGrid;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.AABB;
import java.util.List;

public final class ChamberRuntimeChecks {
    public static void run() {
        var sat=new ChamberIntersection(); var pose=new Pose3d();
        var box=new AABB(-2,-2,-2,2,2,2);
        require(sat.touches(box,pose,new AABB(2,2,2,3,3,3),pose),"Corner contact must hand over");
        require(sat.touches(box,pose,new AABB(2,-1,-1,4,1,1),pose),"Face contact must hand over");
        require(!sat.touches(box,pose,new AABB(2.001,-1,-1,4,1,1),pose),"A real gap must restore native mode");
        var shallow=new AABB(1.94,-1,-1,3,1,1);
        require(!ChamberTakeoverGeometry.touches(sat,box,pose,shallow,pose,false,.125),"Shallow contact cannot trigger takeover");
        require(ChamberTakeoverGeometry.touches(sat,box,pose,new AABB(1.86,-1,-1,3,1,1),pose,false,.125),"Deep contact enters the inset OBB");
        require(ChamberTakeoverGeometry.touches(sat,box,pose,shallow,pose,true,.125),"Held body stays managed until original OBB exit");
        require(!ChamberTakeoverGeometry.touches(sat,box,pose,new AABB(2.001,-1,-1,3,1,1),pose,true,.125),"Original OBB exit must release without an outer margin");
        require(ChamberTakeoverGeometry.touches(sat,box,pose,shallow,pose,false,0),"Zero margin restores original contact rule");
        var narrow=new AABB(0,0,0,.1,.1,.1);var inset=ChamberTakeoverGeometry.entryBounds(narrow,.125);
        require(inset.getXsize()>0 && inset.getXsize()<narrow.getXsize(),"Narrow chamber inset must not invert or expand");
        require(ChamberVisualGeometry.contains(box,pose,new net.minecraft.world.phys.Vec3(1.94,0,0)),"Breath/visual point retains original box, not inset entry region");
        var rotated=new Pose3d();rotated.orientation().rotateY(Math.PI/4);
        var gapPose=new Pose3d(rotated);gapPose.position().set(rotated.orientation().transform(new org.joml.Vector3d(-.06,0,0)));
        require(ChamberTakeoverGeometry.touches(sat,box,rotated,new AABB(2,-1,-1,3,1,1),gapPose,true,.125),"Held body uses original rotated OBB");
        require(!ChamberTakeoverGeometry.touches(sat,box,rotated,new AABB(2,-1,-1,3,1,1),gapPose,false,.125),"Rotated shallow contact cannot trigger inset entry");
        var beside=new Pose3d(rotated);beside.position().set(.3,0,.3);
        var thin=new AABB(-2,-.1,-.1,2,.1,.1);
        require(!sat.touches(thin,rotated,thin,beside),"World envelope is not final contact test");
        var grid=new ChamberGrid<AABB>();
        var negative=new AABB(-17,-1,-1,-16,1,1);
        var huge=new AABB(-100000,-100000,-100000,100000,100000,100000);
        grid.rebuild(List.of(negative,huge),b->b);
        var candidates=grid.query(new AABB(-16,0,0,-15,1,1));
        require(candidates.contains(negative),"Negative-coordinate cell-boundary contact lost");
        require(candidates.contains(huge),"Oversized chambers must remain candidates");
        require(candidates.size()==2,"Candidates must be deduplicated across cells");
        require(grid.query(huge).size()==2,"Oversized query must fall back to all chambers");
        int[] visits={0};
        require(grid.anyMatch(huge,b->{visits[0]++;return true;}) && visits[0]==1,"Lazy query must stop at first match");
        require(!grid.anyMatch(huge,b->false),"All candidates rejected must report outside");
        grid.rebuild(List.of(),b->b);
        require(grid.query(huge).isEmpty(),"Removed chambers must leave no grid entries");
        System.out.println("Inclusive contact and chamber spatial-index regression checks passed.");
    }
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
