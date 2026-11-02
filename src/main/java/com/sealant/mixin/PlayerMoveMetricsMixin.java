package com.sealant.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sealant.chamber.runtime.ChamberMetrics;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Includes synchronous movement/collision handling, not asynchronous Netty scheduling or all player ticks. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerMoveMetricsMixin {
    @Shadow public ServerPlayer player;
    @WrapMethod(method="handleMovePlayer")
    private void sealant$measureMovement(ServerboundMovePlayerPacket packet,Operation<Void> original) {
        if(!player.getServer().isSameThread()) { original.call(packet);return; }
        var level=player.serverLevel();
        long start=ChamberMetrics.begin(level);
        try { original.call(packet); }
        finally { ChamberMetrics.end(level,"player_move_packet",start); }
    }
}
