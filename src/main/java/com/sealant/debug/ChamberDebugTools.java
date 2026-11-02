package com.sealant.debug;

import com.sealant.chamber.ChamberEntity;
import com.sealant.chamber.physics.ChamberPhysics;
import com.sealant.chamber.runtime.ChamberRuntime;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Server-authoritative inspector actions; reuses the permission-checked command implementation. */
public final class ChamberDebugTools {
    public static void handle(ServerPlayer player,DebugActionPacket request){
        if(!player.hasPermissions(2)){
            PacketDistributor.sendToPlayer(player,new DebugStatePacket(false,false,false,false,false,null,List.of()));return;
        }
        UUID target=target(player);List<String> lines=new ArrayList<>();
        String command=request.action().command;
        switch(request.action()){
            case REFRESH -> command=target==null?"sealant chamber status":"sealant chamber status "+target;
            case WATER_TOGGLE -> command="sealant chamber waterdebug "+(ChamberPhysics.waterDebugEnabled(player)?"off":"on");
            case AIR_TOGGLE -> command="sealant chamber airdebug "+(ChamberPhysics.airDebugEnabled(player)?"off":"on");
            case AUTO_TOGGLE -> command="sealant auto "+(ChamberRuntime.enabled(player.level())?"off":"on");
            case PHYSICS_TOGGLE -> command="sealant chamber physics "+(ChamberPhysics.enabled(player.serverLevel())?"off":"on");
            case PROFILE_START -> {if(request.seconds()<1 || request.seconds()>300)return;command="sealant perf start "+request.seconds();}
            case RESET -> {
                // Require the explicitly confirmed UUID to still be the server's aimed chamber.
                if(request.target()==null || !request.target().equals(target))lines.add("目標已改變；請重新讀取狀態後再重設。");
                else command="sealant chamber reset "+target;
            }
            default -> {}
        }
        if(command!=null){
            CommandSource sink=new CommandSource(){
                @Override public void sendSystemMessage(Component message){if(lines.size()<96)lines.add(message.getString());}
                @Override public boolean acceptsSuccess(){return true;}
                @Override public boolean acceptsFailure(){return true;}
                @Override public boolean shouldInformAdmins(){return false;}
            };
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withSource(sink),command);
        }
        var bounded=lines.stream().limit(96).map(line->line.length()>2048?line.substring(0,2048):line).toList();
        PacketDistributor.sendToPlayer(player,new DebugStatePacket(true,ChamberPhysics.waterDebugEnabled(player),ChamberPhysics.airDebugEnabled(player),ChamberRuntime.enabled(player.level()),ChamberPhysics.enabled(player.serverLevel()),target,bounded));
    }
    public static UUID target(ServerPlayer player){
        Vec3 eye=player.getEyePosition(),end=eye.add(player.getLookAngle().scale(64));double closest=Double.POSITIVE_INFINITY;UUID selected=null;
        var chambers=ChamberRuntime.chambers(player.level()).stream().sorted(Comparator.comparing(ChamberEntity::getUUID)).toList();
        for(var chamber:chambers){
            if(!chamber.isAlive() || !(Sable.HELPER.getContaining(chamber) instanceof ServerSubLevel owner) || owner.isRemoved())continue;
            var localEye=owner.logicalPose().transformPositionInverse(eye);var localEnd=owner.logicalPose().transformPositionInverse(end);var box=chamber.getBoundingBox();
            var hit=box.clip(localEye,localEnd);double distance=box.contains(localEye)?0:hit.map(p->eye.distanceToSqr(owner.logicalPose().transformPosition(p))).orElse(Double.POSITIVE_INFINITY);
            if(distance<closest){closest=distance;selected=chamber.getUUID();}
        }return selected;
    }
    private ChamberDebugTools(){}
}
