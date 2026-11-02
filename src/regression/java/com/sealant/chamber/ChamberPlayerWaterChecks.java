package com.sealant.chamber;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import java.util.*;

public final class ChamberPlayerWaterChecks {
    public static void run(){
        localPlayerOverride();
        var identity=new Pose3d();var body=new AABB(-.3,0,-.3,.3,1.8,.3);
        var sea=new ChamberFluidContact.Domain(new AABB(-3,-3,-3,3,3,3),identity);
        near(ChamberFluidContact.height(body,identity,sea,.5,List.of()),.5,"Shallow feet immersion");
        near(ChamberFluidContact.height(body,identity,sea,3,List.of()),1.8,"Fully submerged player");
        near(ChamberFluidContact.height(body,identity,sea,0,List.of()),0,"Surface touching feet is not wet");
        var far=new ChamberFluidContact.Domain(new AABB(2,0,2,3,2,3),identity);
        near(ChamberFluidContact.height(body,identity,far,2,List.of()),0,"World bounding proximity cannot create water");
        var touching=new ChamberFluidContact.Domain(new AABB(.3,0,-1,1,2,1),identity);
        near(ChamberFluidContact.height(body,identity,touching,2,List.of()),0,"Zero-volume face contact stays dry");
        var chamber=new ChamberFluidContact.Domain(new AABB(-1,-1,-1,1,2,1),identity);
        near(ChamberFluidContact.height(body,identity,sea,3,List.of(chamber)),0,"External water removed inside exact dry chamber");
        var left=new ChamberFluidContact.Domain(new AABB(-1,-1,-1,0,2,1),identity);
        var right=new ChamberFluidContact.Domain(new AABB(0,-1,-1,1,2,1),identity);
        near(ChamberFluidContact.height(body,identity,sea,3,List.of(left)),1.8,"Partially exited body retains outside sea contact");
        near(ChamberFluidContact.height(body,identity,sea,3,List.of(left,right)),0,"Connected dry union removes external water without cracks");
        check(ChamberFluidContact.covers(chamber,body,identity),"Complete containment shortcut exact");
        check(!ChamberFluidContact.covers(left,body,identity),"Partial containment cannot use whole-body dry shortcut");
        var tilted=new Pose3d();tilted.orientation().rotateZ(Math.PI/4);
        near(ChamberFluidContact.height(new AABB(-.3,-1,-.3,.3,1,.3),tilted,sea,0,List.of()),1.3,"Tilted body gets local-axis depth, not world AABB depth");
        var plotBody=body.move(2048000,64,2048000);var plotWater=new AABB(2047998,63,2047998,2048002,67,2048002);
        for(int i=0;i<20;i++){
            var pose=new Pose3d();pose.orientation().rotateXYZ(.07*i,.11*i,-.09*i);
            pose.position().set(pose.orientation().transform(new Vector3d(2048000,64,2048000)).negate());
            near(ChamberFluidContact.height(plotBody,pose,new ChamberFluidContact.Domain(plotWater,pose),100,List.of()),1.8,"Rotated far-plot contact invariant");
        }
        try{
            var entity=net.minecraft.world.entity.Entity.class;
            check(entity.getDeclaredMethod("updateFluidHeightAndDoFluidPushing").getReturnType()==void.class,"Sable no-arg fluid scan hook exists");
            check(entity.getDeclaredMethod("getEyeInFluidType").getReturnType()==net.neoforged.neoforge.fluids.FluidType.class,"NeoForge eye hook exists");
            check(entity.getDeclaredField("forgeFluidTypeHeight").getType()==it.unimi.dsi.fastutil.objects.Object2DoubleMap.class,"Fluid height map contract");
            check(dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension.class.getDeclaredMethod("sable$setTrackingSubLevel",dev.ryanhcode.sable.sublevel.SubLevel.class).getReturnType()==void.class,"Sable native swimming tracking contract");
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
        // Camera classification uses exactly the same tetrahedral field as drawn geometry.
        var a=new AABB(0,0,0,1,1,1);var b=new AABB(1,0,0,2,1,1);
        var cells=List.of(new ChamberWaterMesh.Cell(a,.2),new ChamberWaterMesh.Cell(b,.8));
        var surface=ChamberWaterMesh.prepare(cells,identity);
        check(surface.wet(new Vector3d(.999,.45,.5)).orElse(false),"Camera near low cell follows visible shared slope, not old horizontal slice");
        check(!surface.wet(new Vector3d(1.001,.55,.5)).orElse(true),"Camera above shared slope stays dry despite high neighbor cell");
        check(surface.wet(new Vector3d(-1,.5,.5)).isEmpty(),"No camera claim outside domain");
        for(var triangle:surface.mesh()){
            var center=new Vector3d(triangle.a()).add(triangle.b()).add(triangle.c()).div(3);
            check(surface.wet(new Vector3d(center).add(0,-1e-5,0)).orElse(false),"Below rendered triangle is wet");
            check(!surface.wet(new Vector3d(center).add(0,1e-5,0)).orElse(true),"Above rendered triangle is dry");
        }
        System.out.println("Player virtual water: exact shallow/tilted/rotated/far-plot contact, sea boundary subtraction, NeoForge hooks and shared-tetra camera checks passed.");
    }
    /** Guard the inheritance gap: Entity's injected method is bypassed by LocalPlayer. */
    private static void localPlayerOverride(){
        try{
            var loader=ChamberPlayerWaterChecks.class.getClassLoader();
            var player=new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(loader.getResourceAsStream("net/minecraft/client/player/LocalPlayer.class")).accept(player,0);
            check(player.methods.stream().anyMatch(m->m.name.equals("isUnderWater") && m.desc.equals("()Z")),"LocalPlayer has its own underwater method; superclass hook is insufficient");
            var mixin=new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(loader.getResourceAsStream("com/sealant/mixin/client/ChamberLocalPlayerWaterMixin.class")).accept(mixin,0);
            boolean logical=false,hook=false;
            for(var method:mixin.methods){
                if(method.visibleAnnotations!=null)for(var annotation:method.visibleAnnotations)
                    if(annotation.desc.endsWith("/Inject;") && annotation.values.toString().contains("isUnderWater"))hook=true;
                for(var instruction:method.instructions)if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call){
                    if(call.owner.equals("com/sealant/chamber/runtime/ChamberPlayerWater") && call.name.equals("submerged"))logical=true;
                    check(!call.owner.startsWith("net/minecraft/client/Camera") && !call.name.equals("getFluidInCamera"),"Swimming hook must not read detached camera water");
                }
            }
            check(hook && logical,"LocalPlayer override must inject the shared logical immersion check");
            try(var stream=loader.getResourceAsStream("sealant.mixins.json")){
                check(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).contains("client.ChamberLocalPlayerWaterMixin"),"LocalPlayer swimming hook must be registered");
            }
        }catch(java.io.IOException e){throw new AssertionError(e);}
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void near(double actual,double expected,String message){check(Double.isFinite(actual)&&Math.abs(actual-expected)<1e-6,message+": "+actual+" != "+expected);}
}
