package com.sealant.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sealant.chamber.client.ChamberVisuals;
import com.sealant.chamber.client.ChamberWaterSurface;
import net.minecraft.client.particle.BubbleParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Bubble lifetime follows virtual visual water; no fake fluid block is registered. */
@Mixin(BubbleParticle.class)
public abstract class ChamberBubbleMixin {
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z"),require=1)
    private boolean sealant$virtualWater(FluidState state,TagKey<Fluid> tag,Operation<Boolean> original){
        if(tag.equals(FluidTags.WATER) && !state.is(FluidTags.LAVA) && ChamberVisuals.enabled()){
            var wet=ChamberWaterSurface.wet(((Particle)(Object)this).getPos());if(wet.isPresent())return wet.get();
        }return original.call(state,tag);
    }
}
