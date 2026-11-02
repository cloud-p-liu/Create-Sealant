package com.sealant.chamber;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Exact point membership for camera/particles. Physics deliberately uses a different contact rule. */
public final class ChamberVisualGeometry {
    public static boolean contains(AABB local,Pose3dc pose,Vec3 world) {
        return local.contains(pose.transformPositionInverse(world));
    }
    private ChamberVisualGeometry() {}
}
