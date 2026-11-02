package com.sealant.mixin;

import com.sealant.chamber.physics.ChamberPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Real block edits invalidate cached geometry/heads. Metadata refreshes do not. */
@Mixin(LevelChunk.class)
public abstract class ChamberBlockCacheMixin {
    @Shadow @Final net.minecraft.world.level.Level level;
    @Inject(method="setBlockState",at=@At("RETURN"),require=1)
    private void sealant$changed(BlockPos pos,BlockState state,boolean moved,CallbackInfoReturnable<BlockState> cir){
        if(level instanceof ServerLevel server && cir.getReturnValue()!=null && cir.getReturnValue()!=state)
            ChamberPhysics.blockChanged(server,pos);
    }
}
