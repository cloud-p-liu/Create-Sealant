package com.sealant.chamber;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Immutable plot-space volume. Rotation belongs to Sable's pose, never to this box. */
public record ChamberBounds(AABB box) {
    public ChamberBounds {
        if (!finite(box.minX) || !finite(box.minY) || !finite(box.minZ)
                || !finite(box.maxX) || !finite(box.maxY) || !finite(box.maxZ)
                || box.getXsize() < 1 || box.getYsize() < 1 || box.getZsize() < 1)
            throw new IllegalArgumentException("Invalid chamber bounds: " + box);
    }
    private static boolean finite(double value) { return Double.isFinite(value); }
    public ChamberBounds atPosition(double x, double y, double z) {
        Vec3 center = box.getCenter();
        return new ChamberBounds(box.move(x - center.x, y - box.minY, z - center.z));
    }
    public boolean contains(Vec3 plotPosition) { return box.contains(plotPosition); }
}
