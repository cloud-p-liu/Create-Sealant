package com.sealant.buoyancy;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sealant.Sealant;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Sealant.ID)
public final class BuoyancyCommands {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("sealant").requires(source -> source.hasPermission(2));
        for (String feature : new String[]{"buoyancy", "drag"}) {
            var branch = Commands.literal(feature);
            for (String action : new String[]{"off", "on", "status"}) {
                branch.then(Commands.literal(action).executes(context -> execute(context, feature, action, false))
                        .then(Commands.argument("structure", UuidArgument.uuid())
                                .executes(context -> execute(context, feature, action, true))));
            }
            root.then(branch);
        }
        var auto = Commands.literal("auto");
        for (String action : new String[]{"on", "off", "status"}) {
            auto.then(Commands.literal(action).executes(context -> {
                var source=context.getSource();
                if (!action.equals("status")) com.sealant.chamber.runtime.ChamberRuntime.setEnabled(source.getLevel(), action.equals("on"));
                source.sendSuccess(() -> Component.literal("Chamber 自動接管：" + (com.sealant.chamber.runtime.ChamberRuntime.enabled(source.getLevel())?"開啟":"關閉") + "（本次維度載入期間有效）"),false);
                return 1;
            }));
        }
        root.then(auto);
        root.then(Commands.literal("perf")
            .then(Commands.literal("start").then(Commands.argument("seconds",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,300)).executes(context -> {
                com.sealant.chamber.runtime.ChamberMetrics.start(context.getSource().getLevel(),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context,"seconds"));
                context.getSource().sendSuccess(() -> Component.literal("開始 chamber 量測；結束後使用 /sealant perf report。時間以遊戲 tick 計。"),false);
                return 1;
            })))
            .then(Commands.literal("report").executes(context -> {
                for(String line:com.sealant.chamber.runtime.ChamberMetrics.report(context.getSource().getLevel())) {
                    context.getSource().sendSuccess(() -> Component.literal(line),false);
                    Sable.LOGGER.info("[Sealant perf] {}",line);
                }
                return 1;
            })));
        root.then(com.sealant.chamber.physics.ChamberPhysicsCommands.branch());
        event.getDispatcher().register(root);
    }

    private static int execute(CommandContext<CommandSourceStack> context, String feature, String action, boolean explicit) throws CommandSyntaxException {
        var source = context.getSource();
        ServerSubLevel target = null;
        if (explicit) {
            var container = SubLevelContainer.getContainer(source.getLevel());
            if (container != null && container.getSubLevel(UuidArgument.getUuid(context, "structure")) instanceof ServerSubLevel found)
                target = found;
        } else {
            var player = source.getPlayerOrException();
            var eye = player.getEyePosition();
            var hit = source.getLevel().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(64)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK
                    && Sable.HELPER.getContaining(source.getLevel(), hit.getBlockPos()) instanceof ServerSubLevel found) target = found;
        }
        if (target == null || target.isRemoved()) {
            source.sendFailure(Component.literal("請對準 Sable 結構，或指定同維度中已載入的結構 UUID。"));
            return 0;
        }
        boolean drag = feature.equals("drag");
        String label = drag ? "水阻力" : "水浮力";
        int refreshed = 0;
        try {
            if (!action.equals("status")) {
                if (drag) StructureWaterDrag.setEnabled(target, action.equals("on"));
                else refreshed = StructureBuoyancy.setEnabled(target, action.equals("on"));
            }
        } catch (RuntimeException failure) {
            Sable.LOGGER.error("[Sealant] Could not change {} for {}", feature, target.getUniqueId(), failure);
            source.sendFailure(Component.literal("切換" + label + "失敗：" + failure.getMessage()));
            return 0;
        }
        var structure = target;
        int count = refreshed;
        source.sendSuccess(() -> Component.literal("Sable " + structure.getUniqueId() + " 水浮力："
                + (StructureBuoyancy.isDisabled(structure) ? "停用" : "啟用") + "；水阻力："
                + (StructureWaterDrag.isDisabled(structure) ? "停用" : "啟用") + "；chamber 接管：" + com.sealant.chamber.runtime.ChamberRuntime.isManaged(structure) + "；質量："
                + structure.getMassTracker().getMass() + "；更新方塊：" + count), false);
        return 1;
    }
}
