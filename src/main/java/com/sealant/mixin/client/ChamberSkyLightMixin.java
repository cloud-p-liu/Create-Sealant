package com.sealant.mixin.client;

import com.sealant.chamber.client.ChamberVisuals;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Both vanilla-chunk and Sable fancy dispatchers consume this scale. Never modifies world light data. */
@Mixin(value=ClientSubLevel.class,remap=false)
public abstract class ChamberSkyLightMixin {
    @Inject(method="scaleLightColor",at=@At("RETURN"),cancellable=true)
    private void sealant$blockAmbient(int original,CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(com.sealant.chamber.client.ChamberLighting.packed((ClientSubLevel)(Object)this,cir.getReturnValue()));
    }
    @Inject(method="getLatestSkyLightScale",at=@At("RETURN"),cancellable=true)
    private void sealant$removeWaterSkyLoss(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ChamberVisuals.skyLight((ClientSubLevel)(Object)this,cir.getReturnValue()));
    }
}
