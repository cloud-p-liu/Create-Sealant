package com.sealant.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sealant.chamber.runtime.ChamberMetrics;
import dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Timing only: the native step always executes with its original scene and dt. No drag gate here. */
@Mixin(value=RapierPhysicsPipeline.class,remap=false)
public abstract class RapierStepMetricsMixin {
    @Shadow @Final private ServerLevel level;
    @WrapOperation(method="physicsTick",at=@At(value="INVOKE",
            target="Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;step(JD)V"),require=1)
    private void sealant$measureStep(long scene,double dt,Operation<Void> original) {
        long start=ChamberMetrics.begin(level);
        try { original.call(scene,dt); }
        finally { ChamberMetrics.end(level,"native_step",start); }
    }
}
