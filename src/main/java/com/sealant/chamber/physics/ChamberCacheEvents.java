package com.sealant.chamber.physics;

import com.sealant.Sealant;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

@EventBusSubscriber(modid=Sealant.ID)
public final class ChamberCacheEvents {
    @SubscribeEvent public static void loaded(ChunkEvent.Load event){if(event.getLevel() instanceof ServerLevel level)ChamberPhysics.chunkChanged(level,event.getChunk().getPos().x,event.getChunk().getPos().z);}
    @SubscribeEvent public static void unloaded(ChunkEvent.Unload event){if(event.getLevel() instanceof ServerLevel level)ChamberPhysics.chunkChanged(level,event.getChunk().getPos().x,event.getChunk().getPos().z);}
    private ChamberCacheEvents(){}
}
