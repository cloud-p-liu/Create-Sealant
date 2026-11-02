package com.sealant.chamber;

import com.sealant.Sealant;
import com.sealant.SealantNetwork.Context;
import dev.ryanhcode.sable.Sable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.UUID;

/** Explicit delete only. Deliberately carries no replacement bounds: chambers cannot resize. */
public record RemoveChamberPacket(UUID chamberId) implements CustomPacketPayload {
    public static final Type<RemoveChamberPacket> TYPE = new Type<>(Sealant.path("remove_chamber"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoveChamberPacket> CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeUUID(packet.chamberId), buf -> new RemoveChamberPacket(buf.readUUID()));
    @Override public Type<RemoveChamberPacket> type() { return TYPE; }
    public void handle(Context context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator()) return;
        if (!player.getMainHandItem().is(Sealant.SEALANT.get()) && !player.getOffhandItem().is(Sealant.SEALANT.get())) return;
        if (!(player.serverLevel().getEntity(chamberId) instanceof ChamberEntity chamber)) return;
        var owner = Sable.HELPER.getContaining(chamber);
        var eye = owner == null ? player.getEyePosition() : owner.logicalPose().transformPositionInverse(player.getEyePosition());
        double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) * 5;
        if (!chamber.getBoundingBox().inflate(range).contains(eye)) return;
        chamber.discard();
        player.displayClientMessage(Component.translatable("item.sealant.sealant.chamber_removed"), true);
    }
}
