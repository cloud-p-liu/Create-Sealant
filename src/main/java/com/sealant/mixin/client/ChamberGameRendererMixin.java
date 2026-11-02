package com.sealant.mixin.client;

import com.sealant.chamber.client.ChamberVisuals;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class ChamberGameRendererMixin {
    @Inject(method="render",at=@At("HEAD"))
    private void sealant$visualFrame(DeltaTracker delta,boolean renderLevel,CallbackInfo ci){com.sealant.chamber.client.ChamberWaterMask.beginFrame();ChamberVisuals.beginFrame();}
}
