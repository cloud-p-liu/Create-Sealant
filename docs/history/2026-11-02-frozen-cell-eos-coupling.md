# Local EOS coupling baseline

This baseline decoupled current-substep rigid-body EOS response from low-frequency held hydrostatic loads. It corrected capacity from the field reference occupancy to the body's current pose, accumulated per-cell 6DOF surface gradients, and solved backward Euler using current mass, inertia, and relative velocity. Uniform pressure cancellation, positive capacity, and local energy regressions were retained.

A later global transport experiment temporarily superseded it, but was rejected. The local EOS baseline is current again; “frozen cell” refers to W/G held between field exchanges, not frozen geometry or an accepted final solution to backpressure. The full formulas, validation scope, dry-cylinder success, and unresolved grid/boundary behavior are maintained in [current pressure coupling](../architecture/pressure-coupling.md), rather than duplicated as competing specifications here.
