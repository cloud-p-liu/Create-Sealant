package com.sealant.mixin;

import com.sealant.chamber.runtime.ChamberRuntime;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Boat.class)
public abstract class BoatChamberFluidMixin {
    @Inject(method="getStatus",at=@At("RETURN"),cancellable=true)
    private void sealant$waterStatus(CallbackInfoReturnable<Boat.Status> cir) {
        var status=cir.getReturnValue();
        if((status==Boat.Status.IN_WATER || status==Boat.Status.UNDER_WATER || status==Boat.Status.UNDER_FLOWING_WATER)
                && ChamberRuntime.contains((Boat)(Object)this)) cir.setReturnValue(Boat.Status.IN_AIR);
    }
}
