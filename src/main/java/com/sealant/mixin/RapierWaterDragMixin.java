package com.sealant.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sealant.buoyancy.StructureWaterDrag;
import com.sealant.buoyancy.NativeWaterDragBody;
import com.sealant.buoyancy.WaterDragGate;
import dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import java.util.ArrayList;
import java.util.List;

/**
 * Water force collection ONLY; intentionally never intercepts Rapier3D.step.
 * Buoyancy colliders and drag flags are independent. Other structures retain native drag.
 */
@Mixin(value = RapierPhysicsPipeline.class, remap = false)
public abstract class RapierWaterDragMixin {
    @Shadow @Final private net.minecraft.server.level.ServerLevel level;
    @Shadow @Final private Int2ObjectMap<ServerSubLevel> activeSubLevels;

    @WrapOperation(method = "prePhysicsTicks", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;tick(JD)V"), require = 1)
    private void sealant$collectWaterForces(long scene, double timeStep, Operation<Void> original) {
        long timing=com.sealant.chamber.runtime.ChamberMetrics.begin(level);
        var pipeline = (RapierPhysicsPipeline)(Object)this;
        List<WaterDragGate.Body> selected = new ArrayList<>();
        for (ServerSubLevel structure : activeSubLevels.values()) {
            if (structure.isRemoved() || !StructureWaterDrag.isDisabled(structure)) continue;
            selected.add(new NativeWaterDragBody(pipeline, structure, scene));
        }
        if(timing==0) {
            if (selected.isEmpty()) original.call(scene, timeStep);
            else WaterDragGate.collectWithoutDrag(selected, () -> original.call(scene, timeStep));
        } else {
            long[] nativeNanos={0};
            Runnable collect=()-> {long start=System.nanoTime();try {original.call(scene,timeStep);}finally {nativeNanos[0]+=System.nanoTime()-start;}};
            try {
                if(selected.isEmpty())collect.run();else WaterDragGate.collectWithoutDrag(selected,collect);
            } finally {
                com.sealant.chamber.runtime.ChamberMetrics.duration(level,"native_water_collect",nativeNanos[0]);
                com.sealant.chamber.runtime.ChamberMetrics.duration(level,"drag_gate",System.nanoTime()-timing-nativeNanos[0]);
                com.sealant.chamber.runtime.ChamberMetrics.count(level,"drag_bodies",selected.size());
            }
        }
    }
}
