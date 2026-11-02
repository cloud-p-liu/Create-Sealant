package com.sealant.chamber.client;

import com.sealant.chamber.ChamberEntity;
import com.sealant.chamber.ChamberIntersection;
import com.sealant.chamber.ChamberVisualGeometry;
import com.sealant.chamber.runtime.ChamberGrid;
import com.sealant.chamber.runtime.ChamberRuntime;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** CLIENT ONLY. Whole-chamber external-water visual suppression, not a change to fluid physics.
 * Render-interpolated poses prevent a moving/tilting chamber from leaving its camera mask behind.
 * Does not register Sable water-occlusion regions: those also change swimming/physical fluid state.
 */
public final class ChamberVisuals {
    private record Volume(AABB local,Pose3d pose,AABB world,ClientSubLevel owner) {}

    private static ClientLevel level;
    private static long tick=Long.MIN_VALUE;
    private static boolean active;
    public static boolean enabled(){return com.sealant.config.SealantClientConfig.value(com.sealant.config.SealantClientConfig.VISUALS,true);}
    public static boolean lighting(){return com.sealant.config.SealantClientConfig.value(com.sealant.config.SealantClientConfig.LIGHTING,true);}
    public static void setEnabled(boolean value){com.sealant.config.SealantClientConfig.set(com.sealant.config.SealantClientConfig.VISUALS,value);tick=Long.MIN_VALUE;ChamberLighting.invalidate();}
    public static void setLighting(boolean value){com.sealant.config.SealantClientConfig.set(com.sealant.config.SealantClientConfig.LIGHTING,value);ChamberLighting.invalidate();}
    private static final List<Volume> volumes=new ArrayList<>();
    private static final ChamberGrid<Volume> grid=new ChamberGrid<>();

    public static void clear() {
        level=null;tick=Long.MIN_VALUE;active=false;volumes.clear();grid.rebuild(volumes,v->v.world);ChamberLighting.clear();
    }
    /** Once per rendered frame; ticks may also rebuild before particle simulation. */
    public static void beginFrame() {rebuild();ChamberWaterSurface.beginFrame();}
    private static void ensure() {
        var current=Minecraft.getInstance().level;
        if(current!=level || current!=null && (tick!=current.getGameTime() || active!=(enabled() && ChamberRuntime.enabled(current))))rebuild();
    }
    private static void rebuild() {
        ClientLevel current=Minecraft.getInstance().level;
        if(current==null){clear();return;}
        if(current!=level || tick!=current.getGameTime()) {ChamberLighting.invalidate();}
        level=current;tick=level.getGameTime();active=enabled() && ChamberRuntime.enabled(level);
        volumes.clear();
        if(active)for(ChamberEntity chamber:ChamberRuntime.chambers(level)) {
            if(!chamber.isAlive())continue;
            if(!(Sable.HELPER.getContainingClient(chamber) instanceof ClientSubLevel owner) || owner.isRemoved())continue;
            Pose3d pose=new Pose3d(owner.renderPose());AABB local=chamber.getBoundingBox();
            volumes.add(new Volume(local,pose,ChamberIntersection.worldSearchBounds(local,pose).toMojang(),owner));
        }
        grid.rebuild(volumes,v->v.world);
    }
    public static boolean masks(Vec3 world) {
        ensure();if(!active || volumes.isEmpty())return false;
        boolean inside=grid.anyMatch(new AABB(world,world).inflate(1e-7),v->ChamberVisualGeometry.contains(v.local,v.pose,world));
        return inside && !ChamberWaterSurface.wet(world).orElse(false);
    }
    /** Factory inputs can still be in a distant plot; live particle positions have been kicked to world. */
    public static boolean masksParticleSource(Vec3 source) {
        ensure();if(level==null || !active || volumes.isEmpty())return false;
        var owner=Sable.HELPER.getContaining(level,source);
        return masks(owner instanceof ClientSubLevel client?client.renderPose().transformPosition(source):source);
    }
    public static boolean cameraMasked() {
        var mc=Minecraft.getInstance();
        return mc.level!=null && mc.getCameraEntity()!=null && masks(mc.gameRenderer.getMainCamera().getPosition());
    }
    public static boolean waterParticle(ParticleType<?> type) {
        return type==ParticleTypes.UNDERWATER || type==ParticleTypes.BUBBLE || type==ParticleTypes.BUBBLE_POP
                || type==ParticleTypes.BUBBLE_COLUMN_UP || type==ParticleTypes.CURRENT_DOWN
                || type==ParticleTypes.SPLASH || type==ParticleTypes.FISHING || type==ParticleTypes.DOLPHIN
                || type==ParticleTypes.RAIN || type==ParticleTypes.DRIPPING_WATER || type==ParticleTypes.FALLING_WATER
                || type==ParticleTypes.DRIPPING_DRIPSTONE_WATER || type==ParticleTypes.FALLING_DRIPSTONE_WATER;
    }
    /** New policy: roots retain native sky; contained bodies inherit their parent component. */
    public static int skyLight(ClientSubLevel body,int original){return ChamberLighting.sky(body,original);}
    private ChamberVisuals() {}
}
