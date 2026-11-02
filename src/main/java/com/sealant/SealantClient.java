package com.sealant;

import com.sealant.chamber.SealantSelectionHandler;
import com.sealant.chamber.ChamberRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = Sealant.ID, value = Dist.CLIENT)
public final class SealantClient {
    private static final SealantSelectionHandler HANDLER = new SealantSelectionHandler();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        com.sealant.client.SealantClientTools.tick(mc);
        if (mc.level != null && mc.player != null) {
            com.sealant.chamber.client.ChamberWaterDebug.tick(mc);
            com.sealant.chamber.client.ChamberWaterSurface.ambient(mc);
            HANDLER.clientTick(mc.level, mc.player);
            com.sealant.chamber.ChamberDebugOutline.tick(mc);
            com.sealant.chamber.ChamberIntersectionOutline.tick(mc);
        } else {
            com.sealant.chamber.client.ChamberWaterSurface.clear();
            com.sealant.chamber.client.ChamberGlassOverlay.clear();
            com.sealant.chamber.client.ChamberWaterDebug.clear();
            HANDLER.reset();
            com.sealant.chamber.client.ChamberVisuals.clear();
            com.sealant.chamber.ChamberIntersectionOutline.clear();
        }
    }
    @SubscribeEvent public static void visualCommands(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("sealanttools").executes(c->{com.sealant.client.SealantClientTools.queueInspector();return 1;}));
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("sealantconfig").executes(c->{com.sealant.client.SealantClientTools.queueConfig();return 1;}));
        var root=net.minecraft.commands.Commands.literal("sealantvisual");
        for(String action:new String[]{"on","off","status"})root.then(net.minecraft.commands.Commands.literal(action).executes(context->{
            if(!action.equals("status"))com.sealant.chamber.client.ChamberVisuals.setEnabled(action.equals("on"));
            context.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal("Chamber 水下視覺停用："+(com.sealant.chamber.client.ChamberVisuals.enabled()?"開啟":"關閉")),false);return 1;
        }));
        var water=net.minecraft.commands.Commands.literal("water");
        for(String action:new String[]{"on","off","status"})water.then(net.minecraft.commands.Commands.literal(action).executes(context->{
            if(!action.equals("status"))com.sealant.chamber.client.ChamberWaterSurface.setVisible(action.equals("on"));
            context.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal("Chamber 水面："+(com.sealant.chamber.client.ChamberWaterSurface.visible()?"開啟":"關閉")+"；Sodium："+(net.neoforged.fml.ModList.get().isLoaded("sodium")?"已載入":"未載入")),false);return 1;
        }));
        root.then(water);
        var light=net.minecraft.commands.Commands.literal("light");
        for(String action:new String[]{"on","off","status"})light.then(net.minecraft.commands.Commands.literal(action).executes(context->{
            if(!action.equals("status"))com.sealant.chamber.client.ChamberVisuals.setLighting(action.equals("on"));
            context.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal("Chamber 水下天空光修正："+(com.sealant.chamber.client.ChamberVisuals.lighting()?"開啟":"關閉")),false);return 1;
        }));
        root.then(net.minecraft.commands.Commands.literal("groups").executes(context->{
            for(String line:com.sealant.chamber.client.ChamberLighting.report()) {
                context.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal(line),false);
                dev.ryanhcode.sable.Sable.LOGGER.info("[Sealant light] {}",line);
            }
            return 1;
        }));
        event.getDispatcher().register(root.then(light));
    }
    @SubscribeEvent public static void waterSurface(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        com.sealant.chamber.client.ChamberWaterMask.prepare(event);
        com.sealant.chamber.client.ChamberWaterSurface.render(event);
        com.sealant.chamber.client.ChamberGlassOverlay.render(event);
    }
    @SubscribeEvent public static void waterOverlay(net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent event) {
        if(event.getOverlayType()==net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent.OverlayType.WATER
                && com.sealant.chamber.client.ChamberVisuals.cameraMasked())event.setCanceled(true);
    }
    @SubscribeEvent public static void clickBlock(PlayerInteractEvent.RightClickBlock event) {
        if(event.getItemStack().is(Sealant.INSPECTOR.get())){
            if(event.getLevel().isClientSide && event.getEntity().isLocalPlayer())com.sealant.client.SealantClientTools.queueInspector();
            event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);return;
        }
        if (!event.getItemStack().is(Sealant.SEALANT.get())) return;
        if (event.getLevel().isClientSide && event.getEntity().isLocalPlayer())
            HANDLER.selectPos(event.getPos(), event.getEntity(), event.getItemStack());
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
    @SubscribeEvent public static void inspectorUse(PlayerInteractEvent.RightClickItem event){
        if(!event.getItemStack().is(Sealant.INSPECTOR.get()))return;
        if(event.getLevel().isClientSide && event.getEntity().isLocalPlayer())com.sealant.client.SealantClientTools.queueInspector();
        event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);
    }
    @SubscribeEvent public static void input(InputEvent.InteractionKeyMappingTriggered event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        var result = event.isUseItem() ? HANDLER.onUse(0, 1, mc.options.keyUse)
                : event.isAttack() ? HANDLER.onAttack(0, 1, mc.options.keyAttack) : null;
        if (result != null && result.cancelled()) { event.setCanceled(true); event.setSwingHand(false); }
    }
    @EventBusSubscriber(modid = Sealant.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent public static void clientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event){
            foundry.veil.platform.VeilEventPlatform.INSTANCE.onVeilAddShaderProcessors((resources,registry)->
                    registry.addPreprocessor(new com.sealant.chamber.client.ChamberWaterMaskShader(),false));
            event.enqueueWork(()->net.neoforged.fml.ModList.get().getModContainerById(Sealant.ID).orElseThrow()
                    .registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                            (container,parent)->com.sealant.client.SealantClientTools.config(parent)));
        }
        @SubscribeEvent public static void keys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event){event.register(com.sealant.client.SealantClientTools.OPEN);}
        @SubscribeEvent public static void config(net.neoforged.fml.event.config.ModConfigEvent event){
            if(event.getConfig().getSpec()==com.sealant.config.SealantClientConfig.SPEC)com.sealant.chamber.client.ChamberLighting.invalidate();
        }
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(Sealant.CHAMBER.get(), ChamberRenderer::new);
        }
    }
}
