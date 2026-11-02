package com.sealant.mixin;

import com.sealant.buoyancy.NoBuoyancyColliderBakery;
import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderBakery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Sable 2.0.4: friction, VOLUME, restitution, liquid flag, callback. Mass is uploaded separately. */
@Mixin(value = RapierVoxelColliderBakery.class, remap = false)
public abstract class RapierColliderBakeryMixin {
    @ModifyArg(method = "buildPhysicsDataForBlock", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;createVoxelColliderEntry(DDDZLdev/ryanhcode/sable/api/physics/callback/BlockSubLevelCollisionCallback;)Ldev/ryanhcode/sable/physics/impl/rapier/collider/RapierVoxelColliderData;"), index = 1, require = 1)
    private double sealant$displacementVolume(double original) {
        return (Object)this instanceof NoBuoyancyColliderBakery ? 0 : original;
    }
}
