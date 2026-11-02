package com.sealant.buoyancy;

import org.joml.Vector3d;
import org.joml.Vector3dc;
import java.util.List;

/** Gate contracts: selected-only masking, exact restoration and failure isolation. */
public final class WaterDragGateChecks {
    private static final class Body implements WaterDragGate.Body {
        final Vector3d linear = new Vector3d(3, -7, 2);
        final Vector3d angular = new Vector3d(0.25, -0.5, 0.125);
        public Vector3d linearVelocity() { return linear; }
        public Vector3d angularVelocity() { return angular; }
        public void addVelocities(Vector3dc l, Vector3dc a) { linear.add(l); angular.add(a); }
    }
    public static void run() {
        Body selected = new Body(), untouched = new Body();
        Vector3d linear = new Vector3d(selected.linear), angular = new Vector3d(selected.angular);
        int[] calls = {0};
        WaterDragGate.collectWithoutDrag(List.of(selected), () -> {
            calls[0]++;
            check(selected.linear.lengthSquared() == 0 && selected.angular.lengthSquared() == 0,
                    "Both linear and rotational water drag must see zero velocity");
            check(untouched.linear.equals(linear) && untouched.angular.equals(angular),
                    "Other structures must retain their drag inputs");
        });
        check(calls[0] == 1, "Native collector must run exactly once");
        check(selected.linear.equals(linear) && selected.angular.equals(angular),
                "Real velocities must be restored before any simulation step");

        Body second = new Body();
        RuntimeException original = new RuntimeException("native force collection failed");
        try {
            WaterDragGate.collectWithoutDrag(List.of(selected, second), () -> { throw original; });
            throw new AssertionError("Collector exception swallowed");
        } catch (RuntimeException error) {
            check(error == original, "Preserve original collector exception");
        }
        check(selected.linear.equals(linear) && second.linear.equals(linear)
                        && selected.angular.equals(angular) && second.angular.equals(angular),
                "Every masked body must be restored after a collector failure");

        WaterDragGate.Body failsDuringMask = new WaterDragGate.Body() {
            boolean first = true;
            final Vector3d l = new Vector3d(linear), a = new Vector3d(angular);
            public Vector3d linearVelocity() { return l; }
            public Vector3d angularVelocity() { return a; }
            public void addVelocities(Vector3dc dl, Vector3dc da) {
                l.add(dl); // Deliberate partial update before failure.
                if (first) { first = false; throw original; }
                a.add(da);
            }
        };
        try {
            WaterDragGate.collectWithoutDrag(List.of(selected, failsDuringMask), () -> {
                throw new AssertionError("Collector must not run after masking failed");
            });
            throw new AssertionError("Mask exception swallowed");
        } catch (RuntimeException error) { check(error == original, "Preserve mask failure"); }
        check(selected.linear.equals(linear) && selected.angular.equals(angular)
                        && failsDuringMask.linearVelocity().equals(linear) && failsDuringMask.angularVelocity().equals(angular),
                "Partial masking failure must restore previously masked and partially modified bodies");
        System.out.println("Water drag collection/restoration regression checks passed.");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
