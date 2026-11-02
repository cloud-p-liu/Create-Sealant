# Live EOS coupling for noncarrier bodies

The current baseline combines low-frequency field exchange with per-body implicit 6DOF pressure coupling each native physics substep. **Excessive backpressure, cell-center trapping, bottom suction, and some boundary ejection remain unresolved.** A global native-substep two-phase nonlinear solver was removed after nonconvergence and an approximately 300 ms/tick user report. Reverting that experiment was not a physical fix.

## Original cylinder failure

In FIELD_LOW, a dry atmospheric 3 × 3 × 4 cylinder with no exterior water ejected a 3 × 3 × 1 wood plate about 100 blocks upward when dropped from roughly two blocks above and entering halfway. Previously, a field-update pressure load could be repeated for about 0.2 s without reacting to the plate's new pose/capacity.

Noncarrier loads now retain only the low-frequency integrated hydrostatic component and local torque. EOS base pressure is recomputed through current-substep coupling. Carrier grid-wall pressure is a separate path; this change is not a complete energy proof for every boundary or moving reference frame.

## Current-substep solve

Use current body/carrier poses, mass, world inertia, center of mass, relative velocity, and managed status. Lazy geometry caches surface ownership and capacity gradients only when exact keys match. No current pressure surface, removal, or exit means no old gas impulse. Held hydrostatic loads also recheck management.

```text
C_current = C_snapshot + oldOccupiedVolume - currentOccupiedVolume
s_i = [-A*n, r cross (-A*n)]  # accumulate surface contributions per cell
C_i(new) = C_i(current) + dt * dot(s_i, u(new))
M * (u(new)-u(old)) = dt * sum((P_i(C_i(new),W_i,G_i)-P0)*s_i)
Jacobian = M + dt^2 * sum(k_i * s_i * transpose(s_i))
k_i = 1 / (W_i*B/(B+P_i-P0)^2 + G_i/P_i^2)
```

Declared block-envelope intersections determine occupied volume. W/G remain local snapshot quantities. Complete closed surfaces under uniform pressure cancel net force/torque. Solve the 6 × 6 backward-Euler system from a positive-capacity feasible start with backtracking. Invalid capacity or nonconvergence explicitly stops that EOS calculation; do not fall back to stale explicit high-pressure forces. Retain compression and negative gauge pressure without arbitrary pressure, speed, or force caps.

Convert the impulse world-to-local once. Where joined to the existing force channel, divide by dt before the channel multiplies by actual dt. Never double-apply time. Do not combine newly rotated points with stale world normals or rebuild collision shapes on same-state buoyancy metadata refreshes.

## Scope and evidence

LOW remains 5 Hz/2 × 2 and FULL 20 Hz/4 × 4, with local conservative exchange and external diving-bell rules. LIGHTWEIGHT does not use this path. No gas-flow coefficient increase is part of the cylinder fix.

For fixed W/G, a fixed reference frame, and affine capacity, the convex EOS potential and backward Euler provide a non-increasing combined local energy regression. Native gravity/collision splitting, accelerated carriers, crossing cells/domains, multiple bodies, nonlinear rotation, field remapping, and incomplete contact/exterior boundary conditions remain outside that guarantee. The selected domain's first carrier supplies the current relative-velocity reference; nested-owner fluid coupling is not implemented.

The 12 dry-piston cases combine masses 0.1/1/10 and dt 0.2/0.05/0.025/0.0125 for nine gas cells. Checks cover positive capacity, energy, rebound within release energy or settling, absent-surface zero impulse, uniform pressure cancellation, and eccentric 6DOF compression. They do not reproduce all Sable collisions/remapping.

The user confirmed the original LOW dry cylinder no longer produces the 100-block ejection and rebound is acceptable. No exact rebound height was reported. Do not claim zero rebound or that bottom/boundary issues are solved. FULL, other masses/rotations, and other boundaries require their own tests.

`live_body_pressure` is included in `chamber_impulses`, not low-frequency `chamber_pressure`. `internal_body_pressure` prepares held hydrostatic loads and shape caches. Inspector distinguishes hydrostatic Y load from live EOS Y load; neither is the carrier's total buoyancy. See [profiling](../development/performance.md).
