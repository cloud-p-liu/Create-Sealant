package com.sealant.mixin;

import com.sealant.chamber.physics.ChamberPhysics;
import dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Separate from the proven native buoyancy/drag gates. Add custom impulses before each step. */
@Mixin(value=RapierPhysicsPipeline.class,remap=false)
public abstract class RapierChamberPhysicsMixin {
    @Shadow @Final private ServerLevel level;
    @Inject(method="physicsTick(D)V",at=@At("HEAD"),require=1)
    private void sealant$pressureStep(double dt,CallbackInfo ci) {
        ChamberPhysics.step(level,(RapierPhysicsPipeline)(Object)this,dt);
    }
}
