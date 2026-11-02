package com.sealant.client;

import com.sealant.Sealant;
import com.sealant.config.SealantClientConfig;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.createmod.catnip.config.ui.ConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class SealantClientTools {
    public static final KeyMapping OPEN=new KeyMapping("key.sealant.inspector",GLFW.GLFW_KEY_F8,"key.categories.sealant");
    private static int pending;
    public static void queueInspector(){pending=1;}
    public static void queueConfig(){pending=2;}
    public static Screen config(Screen parent){
        return new BaseConfigScreen(parent,Sealant.ID).withSpecs(SealantClientConfig.SPEC,null,com.sealant.config.SealantServerConfig.SPEC)
                .withButtonLabels(Component.translatable("sealant.ui.clientConfig").getString(),"Common Config",Component.translatable("sealant.ui.serverConfig").getString());
    }
    public static void tick(Minecraft mc){
        while(OPEN.consumeClick())if(mc.screen==null && mc.player!=null)pending=1;
        if(pending==0)return;
        int action=pending;pending=0;
        if(action==2)mc.setScreen(config(null));
        else if(mc.player!=null && mc.level!=null)mc.setScreen(new ChamberInspectorScreen(null));
    }
    private SealantClientTools(){}
}
