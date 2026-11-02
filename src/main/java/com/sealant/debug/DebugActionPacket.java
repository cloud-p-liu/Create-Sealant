package com.sealant.debug;

import com.sealant.Sealant;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.UUID;

public record DebugActionPacket(DebugAction action,int seconds,UUID target) implements CustomPacketPayload {
    public static final Type<DebugActionPacket> TYPE=new Type<>(Sealant.path("debug_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DebugActionPacket> CODEC=StreamCodec.of((b,p)->{
        b.writeEnum(p.action);b.writeVarInt(p.seconds);b.writeBoolean(p.target!=null);if(p.target!=null)b.writeUUID(p.target);
    },b->new DebugActionPacket(b.readEnum(DebugAction.class),b.readVarInt(),b.readBoolean()?b.readUUID():null));
    @Override public Type<DebugActionPacket> type(){return TYPE;}
}
