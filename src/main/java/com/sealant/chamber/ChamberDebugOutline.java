package com.sealant.chamber;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;

/** F3+B outlines are independent of the held item and selection state. */
public final class ChamberDebugOutline {
    public static void tick(Minecraft mc) {
        if ((!mc.getEntityRenderDispatcher().shouldRenderHitBoxes() && !com.sealant.config.SealantClientConfig.value(com.sealant.config.SealantClientConfig.OUTLINES,false)) || mc.level == null) return;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ChamberEntity chamber && chamber.isAlive()) {
                // Use exactly the VERIFIED honey glue Outliner path. Sable transforms
                // these plot-space vertices with renderPose, yielding the oriented box.
                // Never feed it a world enclosing AABB or apply the transform twice.
                Outliner.getInstance().showAABB("sealant:chamber_debug:" + chamber.getUUID(), chamber.getBoundingBox())
                        .colored(ChamberStyle.COLOR).clearTextures().disableLineNormals().lineWidth(1 / 32f);
            }
        }
    }
}
