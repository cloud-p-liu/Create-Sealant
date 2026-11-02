package com.sealant.chamber.runtime;

import com.sealant.Sealant;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server-authoritative dimension mode, also applied to client water-movement prediction. */
public record ChamberModePacket(boolean enabled) implements CustomPacketPayload {
    public static final Type<ChamberModePacket> TYPE=new Type<>(Sealant.path("chamber_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ChamberModePacket> CODEC=StreamCodec.of(
            (buf,packet)->buf.writeBoolean(packet.enabled),buf->new ChamberModePacket(buf.readBoolean()));
    @Override public Type<ChamberModePacket> type(){return TYPE;}
}
