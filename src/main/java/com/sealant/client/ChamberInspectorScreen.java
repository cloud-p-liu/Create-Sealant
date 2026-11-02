package com.sealant.client;

import com.sealant.Sealant;
import com.sealant.SealantNetwork;
import com.sealant.config.SealantClientConfig;
import com.sealant.debug.*;
import com.sealant.chamber.client.*;
import net.createmod.catnip.config.ui.ConfigScreen;
import net.createmod.catnip.gui.element.TextStencilElement;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;
import java.util.function.Supplier;

/** Create/Catnip inspector. Presentation is local; every world action is checked by the server. */
public final class ChamberInspectorScreen extends ConfigScreen {
    private record Control(BoxWidget widget,Supplier<MutableComponent> label,boolean server) {}
    private final List<Control> controls=new ArrayList<>();
    private DebugStatePacket state;
    private List<String> lines=List.of();
    private int page,scroll,reportX,reportW,reportY,reportH,controlW;
    public ChamberInspectorScreen(Screen parent){super(parent);ConfigScreen.modID=Sealant.ID;}
    private static MutableComponent tr(String key,Object... args){return Component.translatable("sealant.ui."+key,args);}
    private static MutableComponent toggle(String key,boolean enabled){return tr(key).append(" ").append(tr(enabled?"on":"off"));}
    private boolean allowed(){return state!=null && state.allowed();}
    @Override public boolean isPauseScreen(){return false;}
    @Override protected void init(){
        setWindowSize(Math.min(500,width-16),Math.min(294,height-16));super.init();controls.clear();
        controlW=(windowWidth-34)/2;reportX=guiLeft+controlW+22;reportW=windowWidth-controlW-34;reportY=guiTop+59;reportH=windowHeight-91;
        int tabW=(windowWidth-28)/3;
        for(int i=0;i<3;i++){final int index=i;button(guiLeft+10+i*(tabW+4),guiTop+29,tabW,()->tr("tab"+index),()->{page=index;rebuildWidgets();},false);}
        if(page==0){
            local(0,0,"effects",ChamberVisuals::enabled,()->ChamberVisuals.setEnabled(!ChamberVisuals.enabled()));
            local(1,0,"water",ChamberWaterSurface::visible,()->ChamberWaterSurface.setVisible(!ChamberWaterSurface.visible()));
            local(0,1,"lighting",ChamberVisuals::lighting,()->ChamberVisuals.setLighting(!ChamberVisuals.lighting()));
            local(1,1,"outlines",()->SealantClientConfig.value(SealantClientConfig.OUTLINES,false),()->SealantClientConfig.set(SealantClientConfig.OUTLINES,!SealantClientConfig.value(SealantClientConfig.OUTLINES,false)));
            action(0,2,()->toggle("waterPoints",state!=null && state.water()),DebugAction.WATER_TOGGLE);
            action(1,2,()->toggle("airPoints",state!=null && state.air()),DebugAction.AIR_TOGGLE);
            control(0,3,()->tr("lightReport"),()->{lines=ChamberLighting.report();scroll=0;},false);
            control(1,3,()->tr("config"),()->minecraft.setScreen(SealantClientTools.config(this)),false);
            local(0,4,"waterCull",()->SealantClientConfig.value(SealantClientConfig.WATER_CULL,true),()->SealantClientConfig.set(SealantClientConfig.WATER_CULL,!SealantClientConfig.value(SealantClientConfig.WATER_CULL,true)));
            control(1,4,()->tr("waterCullReport"),()->{lines=ChamberWaterMask.report();scroll=0;},false);
            local(0,5,"waterOverlay",ChamberGlassOverlay::visible,()->ChamberGlassOverlay.setVisible(!ChamberGlassOverlay.visible()));
        }else if(page==1){
            action(0,0,()->tr("status"),DebugAction.STATUS);
            action(1,0,()->tr("refreshTarget"),DebugAction.REFRESH);
            action(0,1,()->tr("profileStart",SealantClientConfig.value(SealantClientConfig.PROFILE_SECONDS,30)),DebugAction.PROFILE_START);
            action(1,1,()->tr("profileReport"),DebugAction.PROFILE_REPORT);
            control(0,2,()->tr("copyReport"),()->minecraft.keyboardHandler.setClipboard(String.join("\n",lines)),false);
            control(1,2,()->tr("config"),()->minecraft.setScreen(SealantClientTools.config(this)),false);
        }else{
            action(0,0,()->toggle("auto",state!=null && state.auto()),DebugAction.AUTO_TOGGLE);
            action(1,0,()->toggle("physics",state!=null && state.physics()),DebugAction.PHYSICS_TOGGLE);
            action(0,1,()->tr("buoyancyOff"),DebugAction.BUOYANCY_OFF);action(1,1,()->tr("buoyancyOn"),DebugAction.BUOYANCY_ON);
            action(0,2,()->tr("dragOff"),DebugAction.DRAG_OFF);action(1,2,()->tr("dragOn"),DebugAction.DRAG_ON);
            action(0,3,()->tr("nativeStatus"),DebugAction.BUOYANCY_STATUS);
            control(1,3,()->tr("reset"),this::confirmReset,true);
            control(0,4,()->tr("simulationConfig"),()->minecraft.setScreen(SealantClientTools.config(this)),false);
        }
        button(guiLeft+10,guiTop+windowHeight-25,controlW,()->tr("close"),this::onClose,false);
        refreshLabels();
        if(state==null && minecraft.player!=null)send(DebugAction.REFRESH,null);
    }
    private void local(int col,int row,String key,Supplier<Boolean> current,Runnable change){control(col,row,()->toggle(key,current.get()),()->{change.run();refreshLabels();},false);}
    private void action(int col,int row,Supplier<MutableComponent> label,DebugAction action){control(col,row,label,()->send(action,null),true);}
    private void control(int col,int row,Supplier<MutableComponent> label,Runnable action,boolean server){
        int w=(controlW-4)/2;button(guiLeft+10+col*(w+4),guiTop+63+row*26,w,label,action,server);
    }
    private void button(int x,int y,int width,Supplier<MutableComponent> label,Runnable action,boolean server){
        var widget=new BoxWidget(x,y,width,20);widget.withCallback(action);addRenderableWidget(widget);
        controls.add(new Control(widget,label,server));
    }
    private void refreshLabels(){
        for(var c:controls){
            var text=c.label.get();c.widget.setMessage(text);
            var element=new TextStencilElement(font,text).centered(true,true);
            element.withElementRenderer(BoxWidget.gradientFactory.apply(c.widget));c.widget.showingElement(element);
            c.widget.active=!c.server || allowed();c.widget.updateGradientFromState();
            c.widget.getToolTip().clear();c.widget.getToolTip().add(text);
            if(c.server)c.widget.getToolTip().add(tr("serverActionHint"));
        }
    }
    private void send(DebugAction action,UUID target){
        SealantNetwork.sendToServer(new DebugActionPacket(action,SealantClientConfig.value(SealantClientConfig.PROFILE_SECONDS,30),target));
    }
    private void confirmReset(){
        if(state==null || state.target()==null){lines=List.of(tr("noTarget").getString());return;}
        UUID target=state.target();
        minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes)send(DebugAction.RESET,target);},tr("resetTitle"),tr("resetBody",target.toString())));
    }
    public static void receive(DebugStatePacket packet){
        if(Minecraft.getInstance().screen instanceof ChamberInspectorScreen screen){
            screen.state=packet;screen.lines=packet.allowed()?packet.lines():List.of(tr("permission").getString());screen.scroll=0;screen.refreshLabels();
        }
    }
    @Override public void tick(){super.tick();}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        if(x>=reportX && x<=reportX+reportW && y>=reportY && y<=reportY+reportH){scroll=Math.max(0,scroll-(int)Math.signum(dy)*3);return true;}
        return super.mouseScrolled(x,y,dx,dy);
    }
    @Override protected void renderWindow(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        g.fill(guiLeft,guiTop,guiLeft+windowWidth,guiTop+windowHeight,0xED172128);
        g.fill(guiLeft,guiTop,guiLeft+windowWidth,guiTop+2,0xFF59B5E8);
        g.drawString(font,tr("title"),guiLeft+11,guiTop+11,0xDDF3FF,false);
        g.fill(reportX-4,reportY-4,reportX+reportW+4,reportY+reportH,0xAA091218);
        List<FormattedCharSequence> wrapped=new ArrayList<>();
        if(state==null)wrapped.addAll(font.split(tr("waiting"),reportW));
        for(String line:lines){wrapped.addAll(font.split(Component.literal(line),reportW));wrapped.add(FormattedCharSequence.EMPTY);}
        int visible=Math.max(1,reportH/10);scroll=Math.min(scroll,Math.max(0,wrapped.size()-visible));
        g.enableScissor(reportX,reportY,reportX+reportW,reportY+reportH);
        for(int i=scroll;i<wrapped.size() && i<scroll+visible;i++)g.drawString(font,wrapped.get(i),reportX,reportY+(i-scroll)*10,0xDDE5EA,false);
        g.disableScissor();
        String target=state!=null && state.target()!=null?state.target().toString().substring(0,8):tr("none").getString();
        g.drawString(font,tr("target",target),reportX,guiTop+windowHeight-20,0x9AB4C4,false);
        if(page==2){int y=guiTop+199;for(var line:font.split(tr("maintenanceHint"),controlW)){
            if(y>guiTop+windowHeight-38)break;g.drawString(font,line,guiLeft+10,y,0xC9BDA1,false);y+=10;
        }}
    }
}
