package com.sealant.mixin.client;

import com.sealant.chamber.client.ChamberLighting;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sable's small-body fast path reads world light directly instead of the chunk shader. */
@Mixin(targets="dev.ryanhcode.sable.sublevel.render.vanilla.SingleBlockSubLevelWrapper",remap=false)
public abstract class ChamberSingleBlockLightMixin {
    @Shadow private ClientLevel level;
    @Shadow @Final private BlockPos.MutableBlockPos localPos;
    @Inject(method="getBrightness",at=@At("RETURN"),cancellable=true)
    private void sealant$roomBrightness(LightLayer layer,BlockPos pos,CallbackInfoReturnable<Integer> cir) {
        if(level==null || !(Sable.HELPER.getContaining(level,localPos) instanceof ClientSubLevel body))return;
        var value=ChamberLighting.ambient(body);if(!value.inherited())return;
        cir.setReturnValue(layer==LightLayer.SKY?(int)Math.round(value.sky()):Math.max(cir.getReturnValue(),(int)Math.round(value.block())));
    }
    @Inject(method="getRawBrightness",at=@At("RETURN"),cancellable=true)
    private void sealant$rawRoomBrightness(BlockPos pos,int darken,CallbackInfoReturnable<Integer> cir) {
        if(level==null || !(Sable.HELPER.getContaining(level,localPos) instanceof ClientSubLevel body))return;
        var value=ChamberLighting.ambient(body);if(!value.inherited())return;
        int block=Math.max(level.getBrightness(LightLayer.BLOCK,localPos),(int)Math.round(value.block()));
        cir.setReturnValue(Math.max(block,(int)Math.round(value.sky())-darken));
    }
}
