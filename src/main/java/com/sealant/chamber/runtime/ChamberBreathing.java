package com.sealant.chamber.runtime;

import dev.ryanhcode.sable.Sable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** First breathing stage: sealed LIGHTWEIGHT air is unlimited. No oxygen/PV consumption.
 * Eye point, not body contact or the takeover exit margin, decides breathable space. */
public final class ChamberBreathing {
    public static boolean dryEyes(Entity entity){
        if(!(entity instanceof Player) || !ChamberRuntime.lightweight(entity.level()))return false;
        var eye=entity.getEyePosition();var owner=Sable.HELPER.getContaining(entity);
        if(owner!=null)eye=owner.logicalPose().transformPosition(eye);
        return ChamberRuntime.containsPoint(entity.level(),eye);
    }
    private ChamberBreathing(){}
}
