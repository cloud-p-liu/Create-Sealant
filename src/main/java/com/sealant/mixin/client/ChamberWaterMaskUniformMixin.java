package com.sealant.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Run after the complete apply, including Veil's tail uploads. Update its cached Uniforms
 * as well as the GPU, so subsequent uploads cannot reset the water mask to zero. */
@Mixin(value=ShaderInstance.class,priority=400)
public abstract class ChamberWaterMaskUniformMixin {
    @Inject(method="updateLocations",at=@At("RETURN"))
    private void sealant$resetClipLocations(CallbackInfo ci){
        com.sealant.chamber.client.ChamberWaterMask.invalidate((ShaderInstance)(Object)this);
    }
    @Inject(method="close",at=@At("HEAD"))
    private void sealant$freeClipLocations(CallbackInfo ci){
        com.sealant.chamber.client.ChamberWaterMask.invalidate((ShaderInstance)(Object)this);
    }
    @WrapMethod(method="apply")
    private void sealant$waterClipUniforms(Operation<Void> original){
        original.call();
        com.sealant.chamber.client.ChamberWaterMask.apply((ShaderInstance)(Object)this);
    }
}
