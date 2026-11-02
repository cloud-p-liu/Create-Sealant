package com.sealant.mixin.client;

import com.sealant.chamber.client.ChamberVisuals;
import com.sealant.chamber.client.ChamberWaterParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class ChamberParticleEngineMixin {
    @Inject(method="createParticle",at=@At("HEAD"),cancellable=true)
    private void sealant$skipWaterParticle(ParticleOptions options,double x,double y,double z,double dx,double dy,double dz,CallbackInfoReturnable<Particle> cir) {
        if(ChamberVisuals.waterParticle(options.getType()) && ChamberVisuals.masksParticleSource(new Vec3(x,y,z)))cir.setReturnValue(null);
    }
    @Inject(method="makeParticle",at=@At("RETURN"))
    private void sealant$tagWaterParticle(ParticleOptions options,double x,double y,double z,double dx,double dy,double dz,CallbackInfoReturnable<Particle> cir) {
        if(cir.getReturnValue() instanceof ChamberWaterParticle water)water.sealant$setWaterVisual(ChamberVisuals.waterParticle(options.getType()));
    }
    @Inject(method="tickParticle",at=@At("HEAD"),cancellable=true)
    private void sealant$removeEnteringWaterParticle(Particle particle,CallbackInfo ci) {
        if(((ChamberWaterParticle)particle).sealant$isWaterVisual() && ChamberVisuals.masks(particle.getPos())) {
            particle.remove();ci.cancel(); // Normal tickParticleList removes it and balances particle-group counters.
        }
    }
}
