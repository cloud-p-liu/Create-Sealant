package com.sealant.mixin.client;

import com.sealant.chamber.client.ChamberWaterParticle;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Particle.class)
public abstract class ChamberParticleMixin implements ChamberWaterParticle {
    @Unique private boolean sealant$waterVisual;
    public boolean sealant$isWaterVisual(){return sealant$waterVisual;}
    public void sealant$setWaterVisual(boolean water){sealant$waterVisual=water;}
}
