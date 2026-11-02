package com.sealant.debug;

import com.sealant.Sealant;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.*;

public record DebugStatePacket(boolean allowed,boolean water,boolean air,boolean auto,boolean physics,UUID target,List<String> lines) implements CustomPacketPayload {
    public static final Type<DebugStatePacket> TYPE=new Type<>(Sealant.path("debug_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DebugStatePacket> CODEC=StreamCodec.of((b,p)->{
        b.writeBoolean(p.allowed);b.writeBoolean(p.water);b.writeBoolean(p.air);b.writeBoolean(p.auto);b.writeBoolean(p.physics);b.writeBoolean(p.target!=null);if(p.target!=null)b.writeUUID(p.target);
        b.writeVarInt(p.lines.size());for(var line:p.lines)b.writeUtf(line,2048);
    },b->{
        boolean allowed=b.readBoolean(),water=b.readBoolean(),air=b.readBoolean(),auto=b.readBoolean(),physics=b.readBoolean();UUID target=b.readBoolean()?b.readUUID():null;
        int count=b.readVarInt();if(count<0||count>96)throw new IllegalArgumentException("Debug report budget");
        List<String> lines=new ArrayList<>();for(int i=0;i<count;i++)lines.add(b.readUtf(2048));
        return new DebugStatePacket(allowed,water,air,auto,physics,target,List.copyOf(lines));
    });
    @Override public Type<DebugStatePacket> type(){return TYPE;}
}
