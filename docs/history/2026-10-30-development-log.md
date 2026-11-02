# Early development decisions

This is an editorial consolidation of pre-publication development notes. The original local notes remain outside the public repository. Historical experiments are not specifications; follow [current architecture](../architecture/chambers.md).

## Selection and marker alignment

The stable selection path was copied from Simulated's MIT honey-glue implementation rather than replacing plot-local selection with root-world coordinates. Two corners, Level.clip, Sable plot conversion, and Outliner projection were validated on rotated structures on 2026-10-29. Missing `retain_in_sub_level` membership previously displaced markers from plots; both retention and destruction tags are now guarded. Bounds are fixed, inclusive maximum block indices become outer edges with +1, and SAT outlines never apply displacement forces.

## Superseded physical models

Early uniform-container, hydraulic-region reduction, independent water-sheet, and room-average approaches were rejected. A connected geometric domain must not collapse local water/gas degrees of freedom. Field V3 retains cell W/G/P and spatial parcels; new obstruction parks mass locally. Legacy V2/V1 data migration remains supported. Later lightweight displacement is a separate dry sealed approximation that pauses field data, not a return to room averaging.

Shared-face local conservative transport and bounded implicit roots replaced attempts that lost mass, created negative inventory, or made low-volume interfaces disappear. Reference water volume is distinct from compressed visual volume. Internal countercurrent displacement fixes water-over-air locking without applying the same exchange to an exterior reservoir, which would destroy diving-bell trapped air. Shared-vertex marching tetrahedra replaced disconnected per-cell water sheets, retaining continuous interfaces and 3D solid subtraction.

## Native physics and rendering lessons

Manual per-body native flags must remain separate from transient chamber takeover. Native buoyancy is disabled with per-plot displacement volume, without altering global collider shapes or rigid-body mass. Native drag gating must restore masked velocities in finally and cannot wrap stepping. Loads are transformed to local impulses using actual substep dt.

Logical poses govern physics; renderPose governs visuals. World enclosing AABBs cannot replace OBB tests. Native sea culling targets water atlas fragments rather than glass, and display-only water containers must never register physics-affecting occlusion. Internal structures inherit parent SKY/BLOCK averages while retaining own stronger block light; fullbright and water-column-top lighting are obsolete.

## Validation boundaries

Early successful selection, native gates/restoration, field exchange, water continuity, gas debug, and parent-light reports apply to their tested versions. Later takeover, swimming, pressure, and renderer changes require independent evidence. Geometry-only microbenchmarks omit world/native/render costs and are not TPS. See [handoff](../../HANDOFF.md) for the current verified/pending distinction.
