package com.sealant.mixin.client;

import com.sealant.chamber.client.ChamberGlassOverlay;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class ChamberOverlayBlockMixin {
    @Inject(method="blockChanged",at=@At("HEAD"))
    private void sealant$overlayGeometryChanged(BlockGetter level,BlockPos pos,BlockState oldState,BlockState newState,int flags,CallbackInfo ci){
        if(oldState!=newState)ChamberGlassOverlay.blockChanged(pos);
    }
}
