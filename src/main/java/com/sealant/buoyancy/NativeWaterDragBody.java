package com.sealant.buoyancy;

import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Short-lived adapter used only while the server thread collects native water forces. */
public record NativeWaterDragBody(RapierPhysicsPipeline pipeline, ServerSubLevel structure, long scene)
        implements WaterDragGate.Body {
    public Vector3d linearVelocity() { return pipeline.getLinearVelocity(structure, new Vector3d()); }
    public Vector3d angularVelocity() { return pipeline.getAngularVelocity(structure, new Vector3d()); }
    public void addVelocities(Vector3dc linear, Vector3dc angular) {
        if (linear.lengthSquared() == 0 && angular.lengthSquared() == 0) return;
        // Native delta setter with wake=false: preserve sleeping state during collection.
        Rapier3D.addLinearAngularVelocities(scene, Rapier3D.getID(structure),
                linear.x(), linear.y(), linear.z(), angular.x(), angular.y(), angular.z(), false);
    }
}
