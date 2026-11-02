package com.sealant.chamber.client;

import com.sealant.chamber.*;
import com.mojang.blaze3d.shaders.Uniform;
import com.sealant.chamber.runtime.ChamberRuntime;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL20;
import java.util.*;

/** All modes: replace native reservoir water inside chamber domains visually, never physically. */
public final class ChamberWaterMask {
    private record Box(net.minecraft.world.phys.AABB bounds,Pose3d pose,double distance) {}
    private record Locations(int program,Uniform count,Uniform inverse,Uniform screen,Uniform[] planes,Uniform[] half,Uniform[] water) {}
    private static final Map<ShaderInstance,Locations> uniforms=new WeakHashMap<>();
    private static final float[] planes=new float[ChamberWaterMaskShader.LIMIT*12],half=new float[ChamberWaterMaskShader.LIMIT*4],inverse=new float[16],uv=new float[12];
    private static int count,width,height;private static boolean ready,shaderFound;
    private static ShaderInstance lastShader;
    private static int verifiedProgram=-1;
    private static Uniform[] array(ShaderInstance shader,String name,int size){
        var uniforms=new Uniform[size];for(int i=0;i<size;i++)uniforms[i]=shader.getUniform(name+"["+i+"]");return uniforms;
    }
    private static void set4(Uniform uniform,float[] values,int offset){
        if(uniform!=null){uniform.set(values[offset],values[offset+1],values[offset+2],values[offset+3]);uniform.upload();}
    }
    public static void beginFrame(){count=0;ready=false;}
    public static void prepare(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_SKY)return;
        var mc=Minecraft.getInstance();count=0;ready=false;
        if(mc.level==null || !ChamberRuntime.enabled(mc.level)
                || !ChamberVisuals.enabled() || !com.sealant.config.SealantClientConfig.value(com.sealant.config.SealantClientConfig.WATER_CULL,true))return;
        // This processor is for the vanilla/Sable ShaderInstance path. Sodium's chunk
        // programs and Iris shader packs need their own adapter; do not silently claim support.
        if(net.neoforged.fml.ModList.get().isLoaded("sodium") || net.neoforged.fml.ModList.get().isLoaded("iris"))return;
        var camera=event.getCamera().getPosition();var eye=new Vector3d(camera.x,camera.y,camera.z);
        List<Box> boxes=new ArrayList<>();
        for(var marker:ChamberRuntime.chambers(mc.level)){
            if(!marker.isAlive() || !(Sable.HELPER.getContainingClient(marker) instanceof ClientSubLevel owner) || owner.isRemoved())continue;
            var pose=new Pose3d(owner.renderPose());
            if(pose.scale().distanceSquared(new Vector3d(1,1,1))>1e-10)continue;
            var local=marker.getBoundingBox();var world=ChamberIntersection.worldSearchBounds(local,pose).toMojang();
            if(!event.getFrustum().isVisible(world))continue;
            var c=world.getCenter();double distance=world.contains(camera)?-1:camera.distanceToSqr(c);
            boxes.add(new Box(local,pose,distance));
        }
        boxes.sort(Comparator.comparingDouble(Box::distance));
        for(var box:boxes){if(count>=ChamberWaterMaskShader.LIMIT)break;ChamberMaskGeometry.encode(box.bounds,box.pose,eye,planes,half,count++);}
        if(count==0)return;
        var atlas=mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        String[] names={"block/water_still","block/water_flow","block/water_overlay"};
        for(int i=0;i<names.length;i++){
            var id=ResourceLocation.withDefaultNamespace(names[i]);var sprite=atlas.apply(id);
            if(!sprite.contents().name().equals(id)){count=0;return;}
            uv[i*4]=sprite.getU0();uv[i*4+1]=sprite.getV0();uv[i*4+2]=sprite.getU1();uv[i*4+3]=sprite.getV1();
        }
        new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()).invert().get(inverse);
        width=mc.getMainRenderTarget().width;height=mc.getMainRenderTarget().height;ready=width>0 && height>0;
    }
    public static void apply(ShaderInstance shader){
        if(!shader.getName().equals("rendertype_translucent"))return;
        var loc=uniforms.get(shader);
        if(loc==null || loc.program!=shader.getId()){
            loc=new Locations(shader.getId(),shader.getUniform("SealantDryCount"),shader.getUniform("SealantDryInverseVP"),
                    shader.getUniform("SealantDryScreen"),array(shader,"SealantDryPlanes",48),array(shader,"SealantDryHalf",16),array(shader,"SealantWaterUV",3));
            uniforms.put(shader,loc);
        }
        if(loc.count==null)return;
        shaderFound=true;lastShader=shader;
        loc.count.set(ready?count:0);loc.count.upload();
        if(!ready || count==0)return;
        if(loc.inverse!=null){loc.inverse.set(inverse);loc.inverse.upload();}
        if(loc.screen!=null){loc.screen.set((float)width,(float)height);loc.screen.upload();}
        for(int i=0;i<count*3;i++)set4(loc.planes[i],planes,i*4);
        for(int i=0;i<count;i++)set4(loc.half[i],half,i*4);
        for(int i=0;i<3;i++)set4(loc.water[i],uv,i*4);
        if(verifiedProgram!=shader.getId()){
            verifiedProgram=shader.getId();
            int actual=GL20.glGetUniformi(shader.getId(),loc.count.getLocation());
            dev.ryanhcode.sable.Sable.LOGGER.info("[Sealant water mask] GPU count={}, expected={}, cached planes/half/UV={}/{}/{}",actual,count,
                    loc.planes[0]!=null,loc.half[0]!=null,loc.water[0]!=null);
        }
    }
    public static void invalidate(ShaderInstance shader){uniforms.remove(shader);if(lastShader==shader)lastShader=null;shaderFound=false;verifiedProgram=-1;}
    public static java.util.List<String> report(){
        String gpu="尚無本幀shader";
        if(lastShader!=null){var loc=uniforms.get(lastShader);if(loc!=null && loc.count!=null){
            int actual=GL20.glGetUniformi(lastShader.getId(),loc.count.getLocation());
            gpu="GPU OBB count="+actual+"；CPU="+(ready?count:0);
            if(loc.half[0]!=null){float[] value=new float[4];GL20.glGetUniformfv(lastShader.getId(),loc.half[0].getLocation(),value);
                gpu+="；GPU half="+java.util.Arrays.toString(value);}
        }}
        return java.util.List.of("外水面剔除："+(ready?"運行中":"未啟用／沒有可見氣室／渲染路徑不支援"),
                "本幀 OBB："+count+" / "+ChamberWaterMaskShader.LIMIT+"；水材質shader："+(shaderFound?"已找到":"尚未找到"),gpu,
                "只剔除水atlas材質，不影響玻璃；目前為vanilla/Sable VANILLA路徑，Sodium/Iris不啟用此剔除。");
    }
    private ChamberWaterMask(){}
}
