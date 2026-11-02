package com.sealant.chamber;

import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

/**
 * Pure geometric overlap using Sable's SAT implementation; never applies its returned MTV.
 * Both inputs remain immutable plot-space boxes. Pose conversion is temporary for the query.
 * Instance scratch vectors are private to the client tick, not Sable's physics scratch context.
 */
public final class ChamberIntersection {
    private static final double MIN_OVERLAP_SQUARED = 1e-12;
    private final LevelReusedVectors scratch = new LevelReusedVectors();
    private final OrientedBoundingBox3d first = new OrientedBoundingBox3d(scratch);
    private final OrientedBoundingBox3d second = new OrientedBoundingBox3d(scratch);
    private final Vector3d overlap = new Vector3d();

    /** Touching faces alone do not count. Positive penetration beyond 1e-6 blocks does. */
    public boolean intersects(AABB firstLocal, Pose3dc firstPose, AABB secondLocal, Pose3dc secondPose) {
        configure(first, firstLocal, firstPose);
        configure(second, secondLocal, secondPose);
        double lengthSquared = OrientedBoundingBox3d.sat(first, second, overlap).lengthSquared();
        return Double.isFinite(lengthSquared) && lengthSquared > MIN_OVERLAP_SQUARED;
    }

    /** Inclusive contact SAT for automatic handover: ANY overlap/contact, never center-only. */
    public boolean touches(AABB firstLocal, Pose3dc firstPose, AABB secondLocal, Pose3dc secondPose) {
        configure(first, firstLocal, firstPose);
        configure(second, secondLocal, secondPose);
        first.vertices(scratch.a);
        second.vertices(scratch.b);
        OrientedBoundingBox3d.genChecks(
                first.rotate(scratch.obbARight.set(OrientedBoundingBox3d.RIGHT)),
                first.rotate(scratch.obbAUp.set(OrientedBoundingBox3d.UP)),
                first.rotate(scratch.obbAForward.set(OrientedBoundingBox3d.FORWARD)),
                second.rotate(scratch.obbBRight.set(OrientedBoundingBox3d.RIGHT)),
                second.rotate(scratch.obbBUp.set(OrientedBoundingBox3d.UP)),
                second.rotate(scratch.obbBForward.set(OrientedBoundingBox3d.FORWARD)), scratch.checks);
        for (Vector3d axis : scratch.checks) {
            if (axis.lengthSquared() < 1e-20) continue;
            axis.normalize();
            OrientedBoundingBox3d.checkSeparation(scratch.a, axis, scratch.proj1);
            OrientedBoundingBox3d.checkSeparation(scratch.b, axis, scratch.proj2);
            if (scratch.proj1.x > scratch.proj2.y + 1e-7 || scratch.proj2.x > scratch.proj1.y + 1e-7) return false;
        }
        return true;
    }

    private static void configure(OrientedBoundingBox3d obb, AABB local, Pose3dc pose) {
        var center = local.getCenter();
        var worldCenter = pose.transformPosition(new Vector3d(center.x, center.y, center.z));
        var scale = pose.scale();
        // Sable OBB dimensions are FULL side lengths, not half-extents.
        obb.set(worldCenter, new Vector3d(local.getXsize() * Math.abs(scale.x()),
                local.getYsize() * Math.abs(scale.y()), local.getZsize() * Math.abs(scale.z())), pose.orientation());
    }

    /** Enclosing world AABB is ONLY the broad phase, never the final overlap test or render box. */
    public static BoundingBox3d worldSearchBounds(AABB local, Pose3dc pose) {
        return new BoundingBox3d(local).transform(pose, new BoundingBox3d());
    }
}
