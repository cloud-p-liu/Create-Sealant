package com.sealant.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Version-pinned Sable expression patch; no global fullbright or world-light mutation.
 * Its one per-body float carries native sky [0,1] plus 2*block-floor, decoded only at lightmap sampling.
 * Root/default values [0,1] decode identically. Sable's fancy program inherits this expression.
 */
@Mixin(targets="dev.ryanhcode.sable.render.dynamic_shade.SableDynamicDirectionalShadingPreProcessor",remap=false)
public abstract class ChamberLightShaderMixin {
    @ModifyArg(method="modify",at=@At(value="INVOKE",target="Lio/github/ocelot/glslprocessor/api/GlslParser;parseExpressionList(Ljava/lang/String;)Ljava/util/List;"),index=0,require=1)
    private String sealant$ambientChannels(String source) {
        return source.replace("UV2 * vec2(1.0, SableSkyLightScale)",
                "vec2(max(float(UV2.x), floor(SableSkyLightScale / 2.0) * 16.0), float(UV2.y) * mod(SableSkyLightScale, 2.0))");
    }
}
