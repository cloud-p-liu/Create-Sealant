package com.sealant.mixin;

import com.sealant.chamber.runtime.ChamberRuntime;
import com.sealant.chamber.runtime.ChamberPlayerWater;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Runs after Sable priority-500 overwrite. Player water comes from authoritative local
 * cells; vanilla/NeoForge handles swimming, jumping, drag, fall distance and breathing. */
@Mixin(value=Entity.class,priority=400)
public abstract class EntityChamberFluidMixin {
    @Shadow protected Object2DoubleMap<FluidType> forgeFluidTypeHeight;
    @Unique private Vec3 sealant$beforeWaterFlow;
    @Inject(method="updateFluidHeightAndDoFluidPushing()V",at=@At("HEAD"))
    private void sealant$rememberFlow(CallbackInfo ci){
        var entity=(Entity)(Object)this;sealant$beforeWaterFlow=entity instanceof Player?entity.getDeltaMovement():null;
    }
    @Inject(method="updateFluidHeightAndDoFluidPushing()V",at=@At("TAIL"))
    private void sealant$virtualPlayerWater(CallbackInfo ci){
        var entity=(Entity)(Object)this;var contact=ChamberPlayerWater.body(entity);
        if(!contact.controlled())return;
        // Sable applies currents before setFluidTypeHeight. Restore only a water-only
        // scan; never rewind lava or another mod fluid's acceleration.
        boolean other=forgeFluidTypeHeight.object2DoubleEntrySet().stream().anyMatch(e->e.getKey()!=NeoForgeMod.WATER_TYPE.value() && !e.getKey().isAir() && e.getDoubleValue()>0);
        if(!other && sealant$beforeWaterFlow!=null)entity.setDeltaMovement(sealant$beforeWaterFlow);
        if(contact.height()>0)forgeFluidTypeHeight.put(NeoForgeMod.WATER_TYPE.value(),contact.height());
        else forgeFluidTypeHeight.removeDouble(NeoForgeMod.WATER_TYPE.value());
        if(contact.height()>0 && contact.waterOwner()!=null && entity instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension movement
                && dev.ryanhcode.sable.Sable.HELPER.getTrackingSubLevel(entity)==null
                && dev.ryanhcode.sable.Sable.HELPER.getContaining(entity)!=contact.waterOwner())
            movement.sable$setTrackingSubLevel(contact.waterOwner());
    }
    @Inject(method="setFluidTypeHeight",at=@At("HEAD"),cancellable=true)
    private void sealant$waterHeight(FluidType type,double height,CallbackInfo ci){
        if(type==NeoForgeMod.WATER_TYPE.value() && ChamberRuntime.contains((Entity)(Object)this)){
            forgeFluidTypeHeight.removeDouble(type);ci.cancel();
        }
    }
    @Inject(method="isInWater",at=@At("RETURN"),cancellable=true)
    private void sealant$waterMotion(CallbackInfoReturnable<Boolean> cir){
        var entity=(Entity)(Object)this;var contact=ChamberPlayerWater.body(entity);
        if(contact.controlled()){cir.setReturnValue(contact.height()>0);return;}
        if(cir.getReturnValue() && ChamberRuntime.contains(entity))cir.setReturnValue(false);
    }
    @Inject(method="isUnderWater",at=@At("RETURN"),cancellable=true)
    private void sealant$submergedPlayer(CallbackInfoReturnable<Boolean> cir){
        ChamberPlayerWater.submerged((Entity)(Object)this).ifPresent(cir::setReturnValue);
    }
    @Inject(method="getEyeInFluidType",at=@At("RETURN"),cancellable=true)
    private void sealant$playerEyes(CallbackInfoReturnable<FluidType> cir){
        var type=cir.getReturnValue();if(type!=NeoForgeMod.WATER_TYPE.value() && !type.isAir())return;
        var eye=ChamberPlayerWater.eyes((Entity)(Object)this);
        if(eye.isPresent())cir.setReturnValue(eye.get()?NeoForgeMod.WATER_TYPE.value():NeoForgeMod.EMPTY_TYPE.value());
    }
    @Inject(method="isEyeInFluid",at=@At("RETURN"),cancellable=true)
    private void sealant$playerEyeTag(net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> tag,CallbackInfoReturnable<Boolean> cir){
        if(!tag.equals(net.minecraft.tags.FluidTags.WATER))return;
        var entity=(Entity)(Object)this;var type=entity.getEyeInFluidType();
        if(type!=NeoForgeMod.WATER_TYPE.value() && !type.isAir())return;
        var eye=ChamberPlayerWater.eyes(entity);eye.ifPresent(cir::setReturnValue);
    }
}
