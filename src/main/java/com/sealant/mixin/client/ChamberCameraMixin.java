package com.sealant.mixin.client;

import com.sealant.chamber.client.ChamberVisuals;
import net.minecraft.client.Camera;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sable FogRenderer temporarily bypasses its OWN occlusion; this independent mask must still apply. */
@Mixin(value=Camera.class,priority=400)
public abstract class ChamberCameraMixin {
    @Inject(method="getFluidInCamera",at=@At("RETURN"),cancellable=true)
    private void sealant$airCamera(CallbackInfoReturnable<FogType> cir) {
        if(com.sealant.chamber.client.ChamberVisuals.enabled()
                && (cir.getReturnValue()==FogType.NONE || cir.getReturnValue()==FogType.WATER)
                && com.sealant.chamber.client.ChamberWaterSurface.wet(((Camera)(Object)this).getPosition()).orElse(false)){
            cir.setReturnValue(FogType.WATER);return;
        }
        if(cir.getReturnValue()==FogType.WATER && ChamberVisuals.masks(((Camera)(Object)this).getPosition()))cir.setReturnValue(FogType.NONE);
    }
}
