package com.sealant.chamber.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3d;
import java.util.HashMap;
import java.util.Map;

/** Original animated atlas sprites, biome tint and lightmap. Uses the vanilla lit/fogged
 * particle-format shader as a material only; these are not particles. Its name is separate
 * from rendertype_translucent so the verified external-water mask cannot erase the overlay. */
final class ChamberWaterMaterial extends RenderType {
    private ChamberWaterMaterial(){super("sealant_water_material",DefaultVertexFormat.PARTICLE,VertexFormat.Mode.TRIANGLES,256,false,false,()->{},()->{});}
    // Hull overlay chooses its water side on the CPU; both viewing directions are intentional.
    static final RenderType SURFACE=type("sealant_water_texture",false),OVERLAY=type("sealant_water_overlay",false);
    private static RenderType type(String name,boolean cull){
        return create(name,DefaultVertexFormat.PARTICLE,VertexFormat.Mode.TRIANGLES,65536,false,false,
                CompositeState.builder().setShaderState(new ShaderStateShard(GameRenderer::getParticleShader))
                        .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS,false,true)).setLightmapState(LIGHTMAP)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(cull?CULL:NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setOutputState(TRANSLUCENT_TARGET).createCompositeState(false));
    }
    private static final Map<Long,Integer> colors=new HashMap<>(),lights=new HashMap<>();
    static void beginFrame(){colors.clear();lights.clear();}
    static TextureAtlasSprite sprite(boolean overlay){return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
            .apply(ResourceLocation.withDefaultNamespace(overlay?"block/water_overlay":"block/water_still"));}
    static int tint(ClientLevel level,Vector3d world){var pos=BlockPos.containing(world.x,world.y,world.z);return colors.computeIfAbsent(pos.asLong(),k->BiomeColors.getAverageWaterColor(level,pos));}
    static int shade(int color,double factor){return ((int)(((color>>16)&255)*factor)<<16)|((int)(((color>>8)&255)*factor)<<8)|(int)((color&255)*factor);}
    private static int lightAt(ClientLevel level,Vector3d point){var pos=BlockPos.containing(point.x,point.y,point.z);return lights.computeIfAbsent(pos.asLong(),k->LevelRenderer.getLightColor(level,pos));}
    static int light(ClientLevel level,Vector3d world,Vector3d local){
        int outer=lightAt(level,world);
        int inner=lightAt(level,local);
        // Do not use plot SKY=15 to bypass the existing outer-world sky attenuation.
        return LightTexture.pack(Math.max(LightTexture.block(outer),LightTexture.block(inner)),LightTexture.sky(outer));
    }
}
