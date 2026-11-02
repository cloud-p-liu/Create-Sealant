package com.sealant.chamber.physics;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import java.util.UUID;

public final class ChamberPhysicsCommands {
    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        var root=Commands.literal("chamber");
        var physics=Commands.literal("physics");
        for(String action:new String[]{"on","off","status"})physics.then(Commands.literal(action).executes(context->{
            var source=context.getSource();
            if(!action.equals("status"))ChamberPhysics.setEnabled(source.getLevel(),action.equals("on"));
            source.sendSuccess(()->Component.literal("Chamber 壓力／進排水物理："+(ChamberPhysics.enabled(source.getLevel())?"開啟":"關閉")+"；auto off 時一併暫停。"),false);
            return 1;
        }));
        root.then(physics);
        var debug=Commands.literal("waterdebug");
        for(String action:new String[]{"on","off"})debug.then(Commands.literal(action).executes(c->{
            ChamberPhysics.setWaterDebug(c.getSource().getPlayerOrException(),action.equals("on"));
            c.getSource().sendSuccess(()->Component.literal("氣室水除錯："+action+"；藍=濕區取樣（框大小表示浸水率），橙=水下開口，綠=空氣開口；5Hz，最多1024點，非水面網格。"),false);
            return 1;
        }));
        root.then(debug);
        var air=Commands.literal("airdebug");
        for(String action:new String[]{"on","off"})air.then(Commands.literal(action).executes(c->{
            ChamberPhysics.setAirDebug(c.getSource().getPlayerOrException(),action.equals("on"));
            c.getSource().sendSuccess(()->Component.literal("氣室空氣除錯："+action+"；粉色=實際氣體取樣，框大小表示氣體佔比，可與 waterdebug 同時開啟；不顯示真空。"),false);
            return 1;
        }));
        root.then(air);
        root.then(Commands.literal("status").executes(c->report(c.getSource(),null))
                .then(Commands.argument("chamber",UuidArgument.uuid()).executes(c->report(c.getSource(),UuidArgument.getUuid(c,"chamber")))));
        root.then(Commands.literal("reset").then(Commands.argument("chamber",UuidArgument.uuid()).executes(c->{
            UUID id=UuidArgument.getUuid(c,"chamber");
            if(ChamberPhysics.reset(c.getSource().getLevel(),id)) {
                c.getSource().sendSuccess(()->Component.literal("已重設此 chamber 所在連通群為乾燥／初始常壓；下一物理 tick 依邊界重新計算。"),false);return 1;
            }
            c.getSource().sendFailure(Component.literal("找不到已載入的 chamber UUID。"));return 0;
        })));
        return root;
    }
    private static int report(CommandSourceStack source,UUID id) {
        for(String line:ChamberPhysics.report(source.getLevel(),id)) {
            source.sendSuccess(()->Component.literal(line),false);
            dev.ryanhcode.sable.Sable.LOGGER.info("[Sealant chamber] {}",line);
        }
        return 1;
    }
    private ChamberPhysicsCommands() {}
}
