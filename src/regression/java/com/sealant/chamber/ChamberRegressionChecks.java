package com.sealant.chamber;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** No game/world writes. Guards the plot-space extents and inclusive endpoint contract. */
public final class ChamberRegressionChecks {
    public static void main(String[] args) {
        // Sable plots are far from the world origin. Include non-cubic extents so axis errors show up.
        var first = new BlockPos(640010, 63, 640020);
        var last = new BlockPos(640013, 65, 640027);
        var original = new ChamberBounds(AABB.encapsulatingFullBlocks(first, last));
        check(original.box().getXsize() == 4 && original.box().getYsize() == 3 && original.box().getZsize() == 8,
                "Both full endpoint blocks must be included");
        check(original.box().equals(AABB.encapsulatingFullBlocks(last, first)), "Corner order must not matter");
        check(original.contains(Vec3.atCenterOf(first)) && original.contains(Vec3.atCenterOf(last)), "Endpoints belong to chamber");
        check(!original.contains(Vec3.atCenterOf(first.offset(-1, 0, 0))), "Outside block must not belong to chamber");
        var moved = original.atPosition(-20.25, 90.5, 60.75);
        check(moved.box().getCenter().x == -20.25 && moved.box().minY == 90.5 && moved.box().getCenter().z == 60.75,
                "Copied entity anchor must stay center X/Z and bottom Y");
        check(moved.box().getXsize() == 4 && moved.box().getYsize() == 3 && moved.box().getZsize() == 8,
                "Positioning must preserve all three dimensions");
        check(original.box().minX == first.getX(), "Translated copy cannot mutate original volume");

        var rotation = new Quaterniond().rotateXYZ(0.41, 0.72, -0.27);
        Vector3d a = rotation.transform(new Vector3d(0, 0, 0));
        Vector3d b = rotation.transform(new Vector3d(4, 0, 0));
        Vector3d c = rotation.transform(new Vector3d(0, 3, 0));
        Vector3d d = rotation.transform(new Vector3d(0, 0, 8));
        check(Math.abs(a.distance(b) - original.box().getXsize()) < 1e-9
                        && Math.abs(a.distance(c) - original.box().getYsize()) < 1e-9
                        && Math.abs(a.distance(d) - original.box().getZsize()) < 1e-9,
                "A projected OBB keeps local extents; world enclosing AABB must never replace them");
        expectInvalid(new AABB(0, 0, 0, 0, 2, 3));
        expectInvalid(new AABB(0, 0, 0, Double.POSITIVE_INFINITY, 2, 3));
        expectInvalid(new AABB(Double.NaN, 0, 0, 1, 2, 3));
        check(ChamberStyle.COLOR == 0x59B5E8, "Chamber outline color must remain #59B5E8");
        ChamberWaterMeshChecks.run();
        ChamberWaterTilesChecks.run();
        checkWaterObstacles();
        checkWaterSlices();
        checkVisualPoints();
        checkIntersections();
        ChamberRuntimeChecks.run();
        ChamberObbVolumeChecks.run();
        com.sealant.chamber.client.WaterMaskShaderChecks.run();
        ChamberFluidChecks.run();
        com.sealant.chamber.physics.ChamberLiteChecks.run();
        com.sealant.chamber.physics.ChamberBodyPressureChecks.run();
        com.sealant.chamber.physics.ChamberBlockVolumeChecks.run();
        ChamberPlayerWaterChecks.run();
        ChamberCellGridChecks.run();
        com.sealant.chamber.physics.ChamberEosChecks.run();
        ChamberFieldChecks.run();
        ChamberFluidSleepChecks.run();
        com.sealant.chamber.physics.ChamberCylinderChecks.run();
        com.sealant.chamber.physics.ChamberPressureCouplingChecks.run();
        com.sealant.chamber.physics.ChamberLazyCacheChecks.run();
        ChamberTopologyChecks.run();
        com.sealant.buoyancy.WaterDragGateChecks.run();
        System.out.println("Chamber geometry and Sable OBB intersection regression checks passed.");
    }
    private static void checkWaterObstacles(){
        var identity=new dev.ryanhcode.sable.companion.math.Pose3d();
        var water=ChamberWaterGeometry.slice(new AABB(-2,-1,-2,2,1,2),identity,0);
        var center=ChamberWaterGeometry.solidSlice(new AABB(-.5,-1,-.5,.5,1,.5),identity,0);
        var remaining=ChamberWaterGeometry.subtract(water,center);
        double area=remaining.stream().mapToDouble(ChamberWaterGeometry::area).sum();
        check(Math.abs(area-15)<1e-8,"Water mesh removes the solid interior hole, including transparent glass");
        var wall=ChamberWaterGeometry.solidSlice(new AABB(-2,-1,-2,-1,1,2),identity,0);
        check(Math.abs(ChamberWaterGeometry.subtract(water,wall).stream().mapToDouble(ChamberWaterGeometry::area).sum()-12)<1e-8,"Wall strip is subtracted from water");
        check(ChamberWaterGeometry.subtract(water,water).isEmpty(),"A completely solid water patch must disappear");
        var remote=ChamberWaterGeometry.solidSlice(new AABB(5,-1,5,6,1,6),identity,0);
        check(Math.abs(ChamberWaterGeometry.subtract(water,remote).stream().mapToDouble(ChamberWaterGeometry::area).sum()-16)<1e-8,"Disjoint body leaves water unchanged");
        var slab=ChamberWaterGeometry.solidSlice(new AABB(-1,-1,-1,1,0,1),identity,0);
        check(Math.abs(ChamberWaterGeometry.area(slab)-4)<1e-8,"Coincident slab top must not be covered with water");
        for(int i=0;i<20;i++){
            var pose=new dev.ryanhcode.sable.companion.math.Pose3d();pose.orientation().rotateXYZ(.12*i,.17*i,-.08*i);
            var cutter=ChamberWaterGeometry.solidSlice(new AABB(-.5,-.5,-.5,.5,.5,.5),pose,0);
            var patches=ChamberWaterGeometry.subtract(water,cutter);
            check(Math.abs(patches.stream().mapToDouble(ChamberWaterGeometry::area).sum()+ChamberWaterGeometry.area(cutter)-16)<1e-8,"Tilted obstacle subtraction conserves surface area");
            for(var patch:patches){
                double x=0,z=0;for(var p:patch){x+=p.x;z+=p.z;check(Math.abs(p.y)<1e-10,"Subtracted mesh remains horizontal");}
                var q=pose.transformPositionInverse(new Vector3d(x/patch.size(),0,z/patch.size()));
                check(!new AABB(-.499999,-.499999,-.499999,.499999,.499999,.499999).contains(q.x,q.y,q.z),"Remaining convex patch cannot cover solid interior");
            }
        }
    }
    private static void checkWaterSlices(){
        var box=new AABB(640000,60,640000,640004,64,640006);
        var pose=new dev.ryanhcode.sable.companion.math.Pose3d();
        pose.position().set(-640003,-62,-640002);
        var points=ChamberWaterGeometry.slice(box,pose,0);
        check(points.size()==4,"Level box water slice is a rectangle");
        double area=0;for(int i=0;i<points.size();i++){var a=points.get(i);var b=points.get((i+1)%points.size());area+=a.x*b.z-b.x*a.z;}
        check(Math.abs(Math.abs(area)/2-24)<1e-7,"Level water slice keeps exact area");
        for(int iteration=0;iteration<25;iteration++){
            var tilted=new dev.ryanhcode.sable.companion.math.Pose3d();
            tilted.orientation().rotateXYZ(.11*iteration,.37,-.29);
            var local=new AABB(-2,-1,-3,2,1,3);
            double height=(iteration%5-2)*.31;
            var polygon=ChamberWaterGeometry.slice(local,tilted,height);
            check(polygon.size()>=3 && polygon.size()<=6,"Tilted box produces a bounded convex water polygon");
            for(var p:polygon){
                check(Math.abs(p.y-height)<1e-10,"Water surface stays horizontal after rotation");
                var q=tilted.transformPositionInverse(new Vector3d(p));
                check(local.inflate(1e-7).contains(q.x,q.y,q.z),"Slice vertices stay in fixed local domain");
            }
        }
        var simple=new AABB(0,0,0,2,2,2);var identity=new dev.ryanhcode.sable.companion.math.Pose3d();
        check(ChamberWaterGeometry.slice(simple,identity,-1).isEmpty(),"Water below chamber has no surface");
        check(ChamberWaterGeometry.slice(simple,identity,2).isEmpty(),"Upper shared face is excluded to prevent duplicate surfaces");
        check(ChamberWaterGeometry.slice(simple,identity,0).size()==4,"Lower shared face supplies the single surface");
    }
    private static void checkVisualPoints() {
        var local=new AABB(20481032,127,20481029,20481036,130,20481037);
        var pose=new dev.ryanhcode.sable.companion.math.Pose3d();
        var center=local.getCenter();pose.rotationPoint().set(center.x,center.y,center.z);
        pose.position().set(-20,60,30);pose.orientation().rotateXYZ(.41,.72,-.27);
        check(ChamberVisualGeometry.contains(local,pose,pose.transformPosition(center)),"Camera membership must project distant plot through its own pose");
        check(!ChamberVisualGeometry.contains(local,pose,pose.transformPosition(new Vec3(local.maxX+.01,center.y,center.z))),"Player body touching chamber must not mask an outside camera");
        var envelope=ChamberIntersection.worldSearchBounds(local,pose).toMojang();
        check(!ChamberVisualGeometry.contains(local,pose,envelope.getMinPosition()),"World envelope corner must not create a false visual mask for tilted chamber");
        check(local.getXsize()==4 && local.getYsize()==3 && local.getZsize()==8,"Visual queries cannot resize the chamber");
    }
    private static void checkIntersections() {
        var query = new ChamberIntersection();
        var identity = new dev.ryanhcode.sable.companion.math.Pose3d();
        AABB chamber = new AABB(-2, -2, -2, 2, 2, 2);
        check(query.intersects(chamber, identity, new AABB(-1, -1, -1, 1, 1, 1), identity),
                "Fully contained structure with coincident centers must intersect");
        check(query.intersects(chamber, identity, new AABB(1.5, -1, -1, 3, 1, 1), identity),
                "Partial entry must intersect even when target center is outside");
        check(!query.intersects(chamber, identity, new AABB(2, -1, -1, 4, 1, 1), identity),
                "Merely touching a chamber face must not count as volume overlap");
        check(!query.intersects(chamber, identity, new AABB(5, 5, 5, 6, 6, 6), identity),
                "Separated structure must not intersect");

        var tilted = new dev.ryanhcode.sable.companion.math.Pose3d();
        tilted.orientation().rotateY(Math.PI / 4);
        var beside = new dev.ryanhcode.sable.companion.math.Pose3d(tilted);
        beside.position().set(0.3, 0, 0.3);
        AABB thin = new AABB(-2, -0.1, -0.1, 2, 0.1, 0.1);
        check(ChamberIntersection.worldSearchBounds(thin, tilted)
                        .intersects(ChamberIntersection.worldSearchBounds(thin, beside)),
                "Fixture must have overlapping world AABBs");
        check(!query.intersects(thin, tilted, thin, beside),
                "SAT must reject world-AABB false positives for tilted structures");

        AABB plotA = new AABB(20481032, 127, 20481029, 20481036, 130, 20481037);
        AABB plotB = new AABB(20483000, 80, 20483000, 20483001, 81, 20483001);
        var poseA = new dev.ryanhcode.sable.companion.math.Pose3d();
        var aCenter = plotA.getCenter();
        poseA.rotationPoint().set(aCenter.x, aCenter.y, aCenter.z);
        poseA.position().set(50, 70, -10);
        poseA.orientation().rotateXYZ(0.41, 0.72, -0.27);
        var poseB = new dev.ryanhcode.sable.companion.math.Pose3d();
        var bCenter = plotB.getCenter();
        poseB.rotationPoint().set(bCenter.x, bCenter.y, bCenter.z);
        poseB.position().set(50, 70, -10);
        poseB.orientation().rotateXYZ(-0.18, 0.36, 0.57);
        check(query.intersects(plotA, poseA, plotB, poseB),
                "Different distant storage plots may overlap in world space with independent rotations");
        poseB.position().add(100, 0, 0);
        check(!query.intersects(plotA, poseA, plotB, poseB),
                "Moving out must clear overlap without mutating either local box");
        check(plotA.getXsize() == 4 && plotA.getYsize() == 3 && plotA.getZsize() == 8,
                "Intersection queries must never resize chamber bounds");

        var scaled = new dev.ryanhcode.sable.companion.math.Pose3d();
        scaled.scale().set(2, 1, 1);
        check(query.intersects(new AABB(-1, -1, -1, 1, 1, 1), scaled,
                        new AABB(1.5, -0.5, -0.5, 2.5, 0.5, 0.5), identity),
                "Sable pose scale must be included in temporary OBB dimensions");
    }
    private static void expectInvalid(AABB box) {
        try { new ChamberBounds(box); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid volume accepted: " + box);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
