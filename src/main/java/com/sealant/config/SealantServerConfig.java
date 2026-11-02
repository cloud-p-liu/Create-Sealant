package com.sealant.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** World-owned simulation selection. Lightweight is an explicitly separate sealed-volume model;
 * neither field mode averages or discards the independent Field V3 cell state. */
@net.neoforged.fml.common.EventBusSubscriber(modid=com.sealant.Sealant.ID)
public final class SealantServerConfig {
    public enum Simulation {
        LIGHTWEIGHT(1,"輕量浮力（無漏水）"),
        FIELD_LOW(4,"低頻三維流體場（5 Hz）"),
        FIELD_FULL(1,"完整三維流體場（20 Hz）");
        public final int interval;
        public final String description;
        Simulation(int interval,String description){this.interval=interval;this.description=description;}
        public boolean field(){return this!=LIGHTWEIGHT;}
    }
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<Simulation> SIMULATION;
    public static final ModConfigSpec.DoubleValue ENTRY_MARGIN;
    public static final ModConfigSpec.BooleanValue VOLUME_CHECK;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> BLOCK_VOLUMES;
    static {
        var b=new ModConfigSpec.Builder();
        b.comment("World simulation / 世界模擬：修改後在下一物理步生效，所有玩家共用。").push("simulation");
        SIMULATION=b.comment("LIGHTWEIGHT（預設）：密封排水體積浮力及水阻，不計算漏水、氣壓或流體交換。已有Field V3資料暫停並保留。",
                "FIELD_LOW：每4 tick更新逐格三維場（5 Hz），每次仍用8個隱式子步；反應較慢，仍可能有更新尖峰。",
                "FIELD_FULL：原有20 Hz逐格三維場、漏水與氣壓；性能成本最高。",
                "Lightweight assumes a sealed dry chamber, regardless of openings; saved field water is not rendered or applied until field mode resumes.")
                .translation("sealant.config.simulationMode").defineEnum("mode",Simulation.LIGHTWEIGHT);
        b.pop().comment("Automatic takeover entry hysteresis / 接管進入死區。").push("takeover");
        ENTRY_MARGIN=b.comment("新接管需碰到原OBB內縮此距離的範圍；已接管者離開原OBB即釋放。單位格，0停用死區。",
                "Does not expand chamber geometry, water masking, or breathable space.")
                .translation("sealant.config.entryMargin").defineInRange("entryMargin",.125,0,.5);
        VOLUME_CHECK=b.comment("預設關閉。啟用後，其他Sable結構完整進入才接管、完整離開才釋放，邊界固定約1%容忍緩衝，取代entryMargin；不影響carrier、原版實體或呼吸。",
                "OBB volume includes empty space. Same-owner connected chamber union is deduplicated before measuring.",
                "這是全結構接管門檻，不按相交比例混合浮力或阻力。")
                .translation("sealant.config.intersectionVolume").define("intersectionVolume",false);
        b.pop().comment("Declared solid volumes / 方塊指定占用體積；僅場模式使用。").push("field");
        BLOCK_VOLUMES=b.comment("所有方塊預設1；內建特例：空氣／流體／海帶0，單台階.5，雙台階1，直樓梯.75，內角.875，外角.625。",
                "格式 minecraft:oak_slab[type=top]=0.5；狀態較精確者優先，同精確度後列者優先。範圍0～1；不從碰撞形狀推算。")
                .defineListAllowEmpty("blockVolumes",java.util.List.<String>of(),()->"minecraft:stone=1.0",com.sealant.chamber.physics.ChamberBlockVolumes::validRule);
        b.pop();SPEC=b.build();
    }
    public static Simulation simulation(){return SPEC.isLoaded()?SIMULATION.get():Simulation.LIGHTWEIGHT;}
    public static double entryMargin(){return SPEC.isLoaded()?ENTRY_MARGIN.get():.125;}
    public static boolean volumeCheck(){return SPEC.isLoaded() && VOLUME_CHECK.get();}
    public static java.util.List<? extends String> blockVolumes(){return SPEC.isLoaded()?BLOCK_VOLUMES.get():java.util.List.of();}
    private static java.util.List<? extends String> savedVolumes;
    private static Simulation savedMode;
    private static boolean savedVolume;
    private static double savedMargin=Double.NaN;
    @net.neoforged.bus.api.SubscribeEvent
    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre event){
        // Catnip applies server values through its permission-2 validated config packet.
        // Explicitly flush mode changes: ConfigValue.set alone is not a persistence contract.
        if(SPEC.isLoaded() && (savedMode!=simulation() || savedMargin!=entryMargin() || savedVolume!=volumeCheck() || !blockVolumes().equals(savedVolumes))){
            SPEC.save();savedVolumes=java.util.List.copyOf(blockVolumes());savedMode=simulation();savedMargin=entryMargin();savedVolume=volumeCheck();
            for(var player:event.getServer().getPlayerList().getPlayers())
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new com.sealant.chamber.runtime.ChamberSimulationPacket(savedMode,savedMargin));
        }
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event){savedVolumes=null;savedMode=null;savedMargin=Double.NaN;savedVolume=false;}
    private SealantServerConfig(){}
}
