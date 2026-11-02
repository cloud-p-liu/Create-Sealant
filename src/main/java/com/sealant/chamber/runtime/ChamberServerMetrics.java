package com.sealant.chamber.runtime;

import com.sealant.Sealant;
import java.lang.management.ManagementFactory;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in whole-server work interval (no tick sleep); also includes other dimensions.
 * Player packet processing may happen outside this interval, so it has its own timer.
 * GC counters are JVM-wide and cannot attribute a pause to a particular thread or mod.
 */
@EventBusSubscriber(modid=Sealant.ID)
public final class ChamberServerMetrics {
    private static final Map<ServerLevel,Long> starts=new IdentityHashMap<>();
    private static long gcCount,gcMillis;
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void pre(ServerTickEvent.Pre event) {
        starts.clear();
        for(var level:event.getServer().getAllLevels()) {
            long start=ChamberMetrics.begin(level);
            if(start!=0)starts.put(level,start);
        }
        if(starts.isEmpty())return;
        gcCount=0;gcMillis=0;
        for(var bean:ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount+=Math.max(0,bean.getCollectionCount());
            gcMillis+=Math.max(0,bean.getCollectionTime());
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void post(ServerTickEvent.Post event) {
        if(starts.isEmpty())return;
        long end=System.nanoTime(),count=0,millis=0;
        for(var bean:ManagementFactory.getGarbageCollectorMXBeans()) {
            count+=Math.max(0,bean.getCollectionCount());
            millis+=Math.max(0,bean.getCollectionTime());
        }
        for(var entry:starts.entrySet()) {
            var level=entry.getKey();
            ChamberMetrics.duration(level,"server_tick_work",end-entry.getValue());
            ChamberMetrics.count(level,"jvm_gc_count_during_ticks",Math.max(0,count-gcCount));
            ChamberMetrics.count(level,"jvm_gc_ms_during_ticks",Math.max(0,millis-gcMillis));
        }
        starts.clear();
    }
    private ChamberServerMetrics() {}
}
