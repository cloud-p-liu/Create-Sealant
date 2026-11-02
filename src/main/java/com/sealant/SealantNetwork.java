package com.sealant;

import com.sealant.chamber.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class SealantNetwork {
    public record Context(Player player) { public Level level() { return player.level(); } }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("9");
        registrar.playToServer(CreateChamberPacket.TYPE, CreateChamberPacket.CODEC,
                (packet, context) -> packet.handle(new Context(context.player())));
        registrar.playToServer(RemoveChamberPacket.TYPE, RemoveChamberPacket.CODEC,
                (packet, context) -> packet.handle(new Context(context.player())));
        registrar.playToClient(com.sealant.chamber.runtime.ChamberModePacket.TYPE, com.sealant.chamber.runtime.ChamberModePacket.CODEC,
                (packet, context) -> com.sealant.chamber.runtime.ChamberRuntime.setEnabled(context.player().level(), packet.enabled()));
        registrar.playToClient(com.sealant.chamber.physics.ChamberWaterDebugPacket.TYPE, com.sealant.chamber.physics.ChamberWaterDebugPacket.CODEC,
                (packet, context) -> com.sealant.chamber.client.ChamberWaterDebug.accept(packet));
        registrar.playToClient(com.sealant.chamber.physics.ChamberWaterStatePacket.TYPE, com.sealant.chamber.physics.ChamberWaterStatePacket.CODEC,
                (packet, context) -> {
                    com.sealant.chamber.runtime.ChamberPlayerWater.accept(context.player().level(),packet);
                    com.sealant.chamber.client.ChamberWaterSurface.accept(packet);
                });
        registrar.playToServer(com.sealant.debug.DebugActionPacket.TYPE,com.sealant.debug.DebugActionPacket.CODEC,
                (packet,context)->com.sealant.debug.ChamberDebugTools.handle((net.minecraft.server.level.ServerPlayer)context.player(),packet));
        registrar.playToClient(com.sealant.debug.DebugStatePacket.TYPE,com.sealant.debug.DebugStatePacket.CODEC,
                (packet,context)->com.sealant.client.ChamberInspectorScreen.receive(packet));
        registrar.playToClient(com.sealant.chamber.runtime.ChamberSimulationPacket.TYPE,com.sealant.chamber.runtime.ChamberSimulationPacket.CODEC,
                (packet,context)->com.sealant.chamber.runtime.ChamberRuntime.acceptSimulation(context.player().level(),packet));
        // No bounds-change or bounds-sync packet: a chamber's box is fixed.
        // Initial tracking and rejoin use ChamberEntity's complex spawn data.
    }
    public static void sendToServer(CustomPacketPayload payload) { PacketDistributor.sendToServer(payload); }
}
