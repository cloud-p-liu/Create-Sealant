package com.sealant.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sealant.chamber.client.*;
import com.sealant.chamber.runtime.ChamberPlayerWater;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Screen water is a camera effect, never a replacement for the player's breathing point. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ChamberScreenWaterMixin {
    private static boolean sealant$affected(Player player){
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera();
        return ChamberPlayerWater.body(player).controlled() || ChamberWaterSurface.wet(camera.getPosition()).isPresent() || ChamberVisuals.masks(camera.getPosition());
    }
    @WrapOperation(method="renderScreenEffect",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"),require=1)
    private static boolean sealant$cameraWater(LocalPlayer player,net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> tag,Operation<Boolean> original){
        var eye=player.getEyeInFluidType();
        if(tag.equals(net.minecraft.tags.FluidTags.WATER) && (eye.isAir() || eye==NeoForgeMod.WATER_TYPE.value()) && sealant$affected(player)){
            var fog=Minecraft.getInstance().gameRenderer.getMainCamera().getFluidInCamera();
            if(fog==FogType.NONE || fog==FogType.WATER)return fog==FogType.WATER;
        }
        return original.call(player,tag);
    }
    @WrapOperation(method="renderScreenEffect",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;getEyeInFluidType()Lnet/neoforged/neoforge/fluids/FluidType;"),require=1)
    private static FluidType sealant$cameraOverlayType(Player player,Operation<FluidType> original){
        var type=original.call(player);
        if((type.isAir() || type==NeoForgeMod.WATER_TYPE.value()) && sealant$affected(player)){
            var fog=Minecraft.getInstance().gameRenderer.getMainCamera().getFluidInCamera();
            if(fog==FogType.NONE)return NeoForgeMod.EMPTY_TYPE.value();
            if(fog==FogType.WATER)return NeoForgeMod.WATER_TYPE.value();
        }return type;
    }
}
