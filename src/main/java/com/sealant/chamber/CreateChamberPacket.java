// Adapted from Simulated 1.3.2 HoneyGlueSpawnPacket (MIT); see THIRD_PARTY_NOTICES.md.
package com.sealant.chamber;

import com.sealant.Sealant;
import com.sealant.SealantNetwork.Context;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;

public record CreateChamberPacket(BlockPos from, BlockPos to) implements CustomPacketPayload {
    public static final Type<CreateChamberPacket> TYPE = new Type<>(Sealant.path("create_chamber"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CreateChamberPacket> CODEC = StreamCodec.of(
            (buf, packet) -> { buf.writeBlockPos(packet.from); buf.writeBlockPos(packet.to); },
            buf -> new CreateChamberPacket(buf.readBlockPos(), buf.readBlockPos()));
    @Override public Type<CreateChamberPacket> type() { return TYPE; }

    public void handle(Context context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        InteractionHand hand = player.getMainHandItem().is(Sealant.SEALANT.get()) ? InteractionHand.MAIN_HAND
                : player.getOffhandItem().is(Sealant.SEALANT.get()) ? InteractionHand.OFF_HAND : null;
        if (hand == null || player.isSpectator()) return;
        var level = player.serverLevel();
        // Both corners must belong to the SAME plot. Comparing world-projected AABBs
        // would inflate a tilted box and was explicitly rejected in the game test.
        var owner = Sable.HELPER.getContaining(level, from);
        if (owner == null || owner != Sable.HELPER.getContaining(level, to)) {
            player.displayClientMessage(Component.translatable("item.sealant.sealant.same_structure"), true);
            return;
        }
        AABB box = AABB.encapsulatingFullBlocks(from, to); // copied full-block endpoint semantics
        if (!ChamberSizing.checkBounds(box).getFirst()) return;
        ChamberEntity entity = Sealant.CHAMBER.get().create(level);
        if (entity == null) return;
        entity.initializeBounds(box);
        if (!level.addFreshEntity(entity)) return;
        var stack = player.getItemInHand(hand);
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        stack.hurtAndBreak(1, level, player, item -> {});
        var center = owner.logicalPose().transformPosition(box.getCenter());
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.75F, 1.0F);
        player.displayClientMessage(Component.translatable("item.sealant.sealant.chamber_created"), true);
    }
}
