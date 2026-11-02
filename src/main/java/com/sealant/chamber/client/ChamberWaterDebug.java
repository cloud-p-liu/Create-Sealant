package com.sealant.chamber.client;

import com.sealant.chamber.physics.ChamberWaterDebugPacket;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import java.util.List;

/** Water/air quadrature points (blue/pink), not a phase-volume mesh. Never infer physics or water volume from these boxes. */
public final class ChamberWaterDebug {
    private static List<ChamberWaterDebugPacket.Point> points=List.of();
    private static Object world;
    private static long received;
    public static void accept(ChamberWaterDebugPacket packet){
        points=packet.points();world=Minecraft.getInstance().level;received=System.nanoTime();
    }
    public static void clear(){points=List.of();world=null;}
    public static void tick(Minecraft mc){
        if(world!=mc.level || System.nanoTime()-received>2_000_000_000L){clear();return;}
        int index=0;
        for(var p:points){if(index>=com.sealant.config.SealantClientConfig.value(com.sealant.config.SealantClientConfig.DEBUG_POINTS,1024))break;double r=p.radius();
            Outliner.getInstance().showAABB("sealant:water_debug:"+index++,new AABB(p.x()-r,p.y()-r,p.z()-r,p.x()+r,p.y()+r,p.z()+r))
                    .colored(p.color()).clearTextures().disableLineNormals().lineWidth(1/48f);
        }
    }
    private ChamberWaterDebug(){}
}
