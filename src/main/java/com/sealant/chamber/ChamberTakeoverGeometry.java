package com.sealant.chamber;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.phys.AABB;

/** Spatial hysteresis: enter an inset OBB, release at the original fixed OBB.
 * The inset is a temporary test, never a new marker size or a movement vector. */
public final class ChamberTakeoverGeometry {
    public static boolean touches(ChamberIntersection sat,AABB chamber,Pose3dc pose,AABB body,Pose3dc bodyPose,boolean held,double margin){
        return sat.touches(!held && margin>0?entryBounds(chamber,margin):chamber,pose,body,bodyPose);
    }
    public static AABB entryBounds(AABB chamber,double margin){
        // Never let a narrow chamber invert or disappear through AABB normalization.
        double x=Math.min(margin,chamber.getXsize()*.49),y=Math.min(margin,chamber.getYsize()*.49),z=Math.min(margin,chamber.getZsize()*.49);
        return new AABB(chamber.minX+x,chamber.minY+y,chamber.minZ+z,chamber.maxX-x,chamber.maxY-y,chamber.maxZ-z);
    }
    private ChamberTakeoverGeometry(){}
}
