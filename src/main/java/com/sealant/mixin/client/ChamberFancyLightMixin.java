package com.sealant.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.sealant.chamber.client.ChamberLighting;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets="dev.ryanhcode.sable.sublevel.render.dispatcher.FancySubLevelRenderDispatcher",remap=false)
public abstract class ChamberFancyLightMixin {
    @ModifyArg(method="renderSectionLayer",at=@At(value="INVOKE",target="Lfoundry/veil/api/client/render/shader/uniform/ShaderUniform;setFloat(F)V"),index=0,require=1)
    private float sealant$roomLight(float original,@Local ClientSubLevel body){return ChamberLighting.shader(body,original);}
}
