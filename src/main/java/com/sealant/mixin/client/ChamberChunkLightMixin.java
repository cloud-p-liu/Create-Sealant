package com.sealant.mixin.client;

import com.sealant.chamber.client.ChamberLighting;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets="dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData",remap=false)
public abstract class ChamberChunkLightMixin {
    @Shadow @Final private ClientSubLevel subLevel;
    @ModifyArg(method="renderChunkedSubLevel",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/shaders/Uniform;set(F)V"),index=0,require=1)
    private float sealant$roomLight(float original){return ChamberLighting.shader(subLevel,original);}
}
