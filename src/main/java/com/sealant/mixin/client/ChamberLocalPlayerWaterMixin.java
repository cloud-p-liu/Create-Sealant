package com.sealant.mixin.client;

import com.sealant.chamber.runtime.ChamberPlayerWater;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** LocalPlayer overrides Entity.isUnderWater; the superclass injection cannot reach it.
 * Sprint/swim input must use logical player immersion in every camera mode. */
@Mixin(value=LocalPlayer.class,priority=400)
public abstract class ChamberLocalPlayerWaterMixin {
    @Inject(method="isUnderWater",at=@At("RETURN"),cancellable=true)
    private void sealant$logicalSubmersion(CallbackInfoReturnable<Boolean> cir){
        ChamberPlayerWater.submerged((LocalPlayer)(Object)this).ifPresent(cir::setReturnValue);
    }
}
