package com.sealant.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Persistent local presentation settings. No simulation constants or server authority live here. */
public final class SealantClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue VISUALS,WATER,LIGHTING,OUTLINES,WATER_CULL,WATER_OVERLAY;
    public static final ModConfigSpec.IntValue WATER_ALPHA,DEBUG_POINTS,PROFILE_SECONDS;
    static {
        var b=new ModConfigSpec.Builder();
        b.comment("Local chamber presentation; these settings do not change server physics.").push("visuals");
        VISUALS=b.comment("Apply chamber dry/wet camera and water-particle effects.").translation("sealant.config.visualEffects").define("visualEffects",true);
        WATER_CULL=b.comment("Hide native exterior-water fragments inside chamber OBBs in all modes; field water uses its own surface pass. Vanilla/Sable renderer only, not Sodium/Iris.")
                .translation("sealant.config.externalWaterCulling").define("externalWaterCulling",true);
        WATER_OVERLAY=b.comment("Draw vanilla water_overlay on submerged transparent chamber hull exteriors; render only.").translation("sealant.config.externalWaterOverlay").define("externalWaterOverlay",true);
        WATER=b.comment("Draw the chamber's reconstructed water interface.").translation("sealant.config.waterSurface").define("waterSurface",true);
        LIGHTING=b.comment("Inherit parent-chamber ambient lighting for contained Sable bodies.").translation("sealant.config.ambientLighting").define("ambientLighting",true);
        OUTLINES=b.comment("Keep chamber and intersecting-structure outlines visible without F3+B.").translation("sealant.config.alwaysShowOutlines").define("alwaysShowOutlines",false);
        WATER_ALPHA=b.comment("Water interface alpha multiplier; 255 preserves the native sprite alpha.").translation("sealant.config.waterOpacity").defineInRange("waterOpacity",145,0,255);
        b.pop().comment("Debug display and profiling preferences; subscriptions remain temporary.").push("tools");
        DEBUG_POINTS=b.comment("Maximum received debug points drawn locally; does not change network or simulation budgets.").translation("sealant.config.debugPointLimit").defineInRange("debugPointLimit",1024,16,1024);
        PROFILE_SECONDS=b.comment("Default profiler duration in game-tick seconds.").translation("sealant.config.profileSeconds").defineInRange("profileSeconds",30,1,300);
        b.pop();SPEC=b.build();
    }
    public static boolean value(ModConfigSpec.BooleanValue key,boolean fallback){return SPEC.isLoaded()?key.get():fallback;}
    public static int value(ModConfigSpec.IntValue key,int fallback){return SPEC.isLoaded()?key.get():fallback;}
    public static void set(ModConfigSpec.BooleanValue key,boolean value){key.set(value);if(SPEC.isLoaded())SPEC.save();}
    private SealantClientConfig(){}
}
