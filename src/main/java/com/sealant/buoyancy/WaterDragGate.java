package com.sealant.buoyancy;

import org.joml.Vector3d;
import org.joml.Vector3dc;
import java.util.ArrayList;
import java.util.List;

/**
 * Sable 2.0.4's native tick collects water forces without integrating positions.
 * do_drag reads linear/angular velocity; do_float does not. Mask only during that
 * collection call, and restore BEFORE Rapier step, collision solving or custom forces.
 * Never wrap a simulation step with this gate: that would freeze motion.
 */
public final class WaterDragGate {
    public interface Body {
        Vector3d linearVelocity();
        Vector3d angularVelocity();
        void addVelocities(Vector3dc linear, Vector3dc angular);
    }
    private record Snapshot(Body body, Vector3d linear, Vector3d angular) {
        void restore() {
            body.addVelocities(new Vector3d(linear).sub(body.linearVelocity()),
                    new Vector3d(angular).sub(body.angularVelocity()));
        }
    }
    public static void collectWithoutDrag(Iterable<? extends Body> selected, Runnable collectForces) {
        List<Snapshot> snapshots = new ArrayList<>();
        Throwable failure = null;
        try {
            for (Body body : selected) {
                Vector3d linear = new Vector3d(body.linearVelocity());
                Vector3d angular = new Vector3d(body.angularVelocity());
                // Register BEFORE masking, so even a failed partial update is restored.
                snapshots.add(new Snapshot(body, linear, angular));
                body.addVelocities(new Vector3d(linear).negate(), new Vector3d(angular).negate());
            }
            collectForces.run();
        } catch (RuntimeException | Error error) {
            failure = error;
            throw error;
        } finally {
            Throwable restorationFailure = null;
            for (Snapshot snapshot : snapshots) {
                try { snapshot.restore(); }
                catch (RuntimeException | Error error) {
                    if (restorationFailure == null) restorationFailure = error;
                    else restorationFailure.addSuppressed(error);
                }
            }
            if (restorationFailure != null) {
                if (failure != null) failure.addSuppressed(restorationFailure);
                else if (restorationFailure instanceof RuntimeException error) throw error;
                else throw (Error)restorationFailure;
            }
        }
    }
    private WaterDragGate() {}
}
