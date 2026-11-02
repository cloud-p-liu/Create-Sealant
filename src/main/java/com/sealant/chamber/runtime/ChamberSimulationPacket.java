package com.sealant.chamber.runtime;

import com.sealant.Sealant;
import com.sealant.config.SealantServerConfig.Simulation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server-authoritative settings for visual/breath prediction; clients cannot send it back. */
public record ChamberSimulationPacket(Simulation mode,double entryMargin) implements CustomPacketPayload {
    public static final Type<ChamberSimulationPacket> TYPE=new Type<>(Sealant.path("simulation_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ChamberSimulationPacket> CODEC=StreamCodec.of(
            (b,p)->{b.writeEnum(p.mode);b.writeDouble(p.entryMargin);},b->{
                var mode=b.readEnum(Simulation.class);double margin=b.readDouble();
                if(!Double.isFinite(margin)||margin<0||margin>.5)throw new IllegalArgumentException("Invalid takeover margin");
                return new ChamberSimulationPacket(mode,margin);
            });
    @Override public Type<ChamberSimulationPacket> type(){return TYPE;}
}
