package com.sealant;

import com.sealant.chamber.ChamberEntity;
import com.sealant.chamber.SealantItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

@Mod(Sealant.ID)
public final class Sealant {
    public static final String ID = "sealant";
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ID);
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final DeferredHolder<Item, SealantItem> SEALANT = ITEMS.register("sealant",
            () -> new SealantItem(new Item.Properties().stacksTo(1).durability(100)));
    public static final DeferredHolder<Item, Item> INSPECTOR = ITEMS.register("chamber_inspector",
            com.sealant.debug.ChamberInspectorItem::new);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.sealant"))
                    .icon(() -> SEALANT.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {output.accept(SEALANT.get());output.accept(INSPECTOR.get());})
                    .build());
    // Registry ID intentionally stays stable for existing verified saves and BOTH Sable tags.
    // Do not rename without migrating save data and tags together; see docs/CHAMBER.md.
    public static final DeferredHolder<EntityType<?>, EntityType<ChamberEntity>> CHAMBER = ENTITIES.register("chamber_structure",
            () -> EntityType.Builder.<ChamberEntity>of(ChamberEntity::create, MobCategory.MISC)
                    .sized(0.1F, 0.1F).eyeHeight(0).clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .build("sealant:chamber_structure"));

    public Sealant(IEventBus bus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT,com.sealant.config.SealantClientConfig.SPEC);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,com.sealant.config.SealantServerConfig.SPEC);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        CREATIVE_TABS.register(bus);
        bus.addListener(SealantNetwork::register);
    }

    public static ResourceLocation path(String name) { return ResourceLocation.fromNamespaceAndPath(ID, name); }
}
