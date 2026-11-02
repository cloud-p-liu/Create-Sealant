# Chamber architecture

This specification covers default sealed displacement and the optional Field V3 local air/water model. Operational controls are documented in [configuration](../user-guide/configuration.md). Maintenance invariants are in [AGENTS.md](../../AGENTS.md).

## World modes

`simulation.mode` is a SERVER setting. LIGHTWEIGHT is default, FIELD_LOW prepares/advances at 5 Hz, and FIELD_FULL at 20 Hz. LOW uses eight implicit substeps over the normal 0.2 s advance; the first advance after loading/mode switching is 0.05 s. FULL retains eight substeps per field advance. Rigid-body impulses always use each actual physics-substep dt; do not multiply forces to compensate for a lower field rate.

LIGHTWEIGHT builds no cell grid and performs no leakage/EOS/remapping solve. It deduplicates the same-owner union into displacement boxes, each at most 4³ blocks, with a 4,096-box group budget. Where the budget permits, axes have at least two samples to support distributed rotational damping. Each box's OBB is clipped against a sampled horizontal external-water surface. Convex-polyhedron integration computes submerged volume and its first moment relative to the box center, avoiding far-plot cancellation.

```text
F_y = waterDensity * abs(gravity) * submergedVolume
```

Apply each force at its true buoyancy centroid. Distributed implicit 6DOF drag uses the existing coefficient `0.85 * wetVolume / characteristicLength`, with minimum length 0.5. Missing external-water chunks suspend the group rather than guess partial forces. The prototype supports vertical gravity and unit scale for this path. Shorelines/discontinuous water remain finitely sampled; domain-exterior carrier solids are not separately added to displacement. Managed internal bodies transfer load through collision rather than receiving duplicated chamber displacement.

Lightweight chambers are deliberately sealed and dry even without walls. Retain old W/G and parcels without advancing or rendering them, then resume in a field mode without catch-up. Reset is the explicit destructive operation that clears group data.

## Geometry and coordinates

ChamberEntity is a massless, noncolliding marker with stable ID `sealant:chamber_structure`. Its local bounds never resize after creation. Inclusive selected maximum block indices become outer edges with +1. Two-point selection, Level.clip, plot coordinates, and Outliner behavior derive from the verified upstream honey-glue workflow. Both Sable lifecycle tags must retain the marker ID.

Logical poses govern physics and hits; renderPose governs visual projection. Outliner receives the local box once. Blue #59B5E8 SAT outlines show actual OBB contact, exclude the carrier from other-body overlap outlines, and never apply an MTV.

Same-owner chambers group transitively through positive-volume overlap or positive-area coplanar contact. Edge/corner contact and different owners do not group. The union is decomposed into nonoverlapping pieces, removing duplicate volume and shared internal faces. A geometric group is a computation domain, not a uniform room.

## Native-water takeover

Manual per-body buoyancy and drag flags are persistent user_data. Automatic management is a transient OR overlay. Exit, marker deletion, unload, and auto-off clear the overlay and restore manual intent. The carrier is managed; other Sable bodies use the configured OBB entry/exit rule. Vanilla entities/boats suppress exterior-water buoyancy on contact.

Default entry contacts an OBB inset by `takeover.entryMargin` (0.125 blocks, range 0–0.5); retained takeover ends outside the original OBB. Narrow axes cap inset at 49%. This margin never changes dimensions, eye dryness, or camera masking.

Optional `takeover.intersectionVolume=false` enables complete entry and complete exit for other Sable bodies only. Clip the complete body OBB against deduplicated pieces of a connected same-owner union. Fixed comparisons of 99% for entry and 1% or less for release supply approximately 1% boundary tolerance; intermediate overlap preserves state. No ratio controls or percentage UI remain. Holes count within the body OBB. Different groups/owners are not added together. Carrier, vanilla entities, eyes, and camera are unchanged. Disabled means no volume clipping. Volumes and centroids are diagnostic/gating data, never proportional forces.

Union-budget failure pauses new volume-based takeover with a warning. Existing takeover falls back to original-OBB retention until exit. Cache failing signatures to avoid repeated rebuilds. Keep actual contact outlines distinct from takeover status.

Native buoyancy uses an independent per-plot collider bakery/cache and only changes displacement volume. Global BlockState shapes, mass, friction, collision response, and callbacks remain intact. Check state before native prePhysicsTicks because Sable loads plots before user_data. Drag gating masks velocity only during native force collection and restores it in finally; native stepping always runs.

## Local field state and exchange

The union is partitioned into plot-aligned block cells and boundary fragments. Capacity comes from [declared occupancy](block-volumes.md), while actual collision shapes determine independent face passages. Each cell retains reference water mass-volume W, conserved GasPV, and EOS pressure. Open face links retain signed water flux in runtime; flux is not persisted.

```text
C = W*B/(B + P - P0) + GasPV/P
B = 10000; P0 = 101.325; waterDensity = 1.05; minimum P = 1e-5
```

These are game-prototype units/constants, not a claim of physical material accuracy. Visible compressed water volume differs from W. The local analytic EOS uses double precision, with stable-root and extreme-value safeguards; see [performance](../development/performance.md).

Active exchange uses eight substeps, alternating face traversal, and bounded backward-Euler roots (28 iterations). Each internal transfer subtracts from one cell and adds to the other for both phases. Brackets respect direction and available quantities, including tiny water volumes. Rectangular wet-face coverage preserves thin wetted strips. External boundaries read root-world reservoir wetness/head.

Internal countercurrent displacement addresses water-above-air gas locking: wet contact and receiving dry contact across an open internal face drive paired volume exchange under local gravity head. Transfer masses use pre-exchange density/pressure, then local EOS closes each cell. The current internal coefficient is 1.0 (previously 0.25). The two-cell synthetic 1 s water imbalance improved from 0.15208889 to 0.00000045; this is not a gameplay flow-rate guarantee. Never apply this internal model to external reservoirs: preserve bottom-open diving-bell trapping. This is a coarse subcell model, not resolved bubbles or full VOF/CFD.

[Sleep](fluid-sleep.md) skips exchange only after current per-face checks and consecutive rest. It preserves local states, hydrostatic gradients, live EOS, buoyancy, and drag.

## Persistence, forces, and rendering

V3 spatial parcels retain W/G in original locations. New obstruction parks the affected share rather than transporting mass through walls. V2/V1 migration remains supported. Mode changes do not reset W/G; panel-density changes clear incompatible face flux indexing. No room-average state is authoritative.

Carrier gauge loads act inward from exterior pressure and outward from neighboring internal cells. Do not add the obsolete solid-volume gradient. Noncarrier bodies integrate their own collision surfaces without duplicate grid loads, combining held low-frequency hydrostatic load with current-substep implicit EOS. Uniform gas pressure must produce zero force and torque. Transform world loads to local impulses once and multiply by actual dt. [Pressure coupling](pressure-coupling.md) records formulas and unresolved backpressure/boundary issues.

Shared-vertex marching tetrahedra produces continuous visible water surfaces, with 3D polygon subtraction against solids. Rendering interpolation never feeds physical state. Vanilla atlas animation, tile-clipped UVs, biome tint, and private water material passes remain. Actual exposed glass faces carry inner water overlays on the inner face and external sea overlays on the outer face. No through-wall projection or physics-affecting water occlusion container is allowed.

The vanilla/Sable VANILLA shader adapter culls native external-water atlas fragments within chamber OBBs; it does not clip glass. It supports up to 16 visible unit-scale markers. Sodium/Iris adapters are not implemented. Camera/particles use the same visible tetrahedral surface and renderPose; player body/eyes use raw logical state. Parent-group average SKY/BLOCK lighting is inherited by internal structures while preserving stronger own block light and outer native sky attenuation.

## Known limits and validation

Same-cell disconnected cavities share a cell state. Thin passages below panel spacing, overlapping-body occupancy, rotating parcel representation, contact/exterior pressure boundaries, nested-owner fluid coupling, finite oxygen, and universal renderer compatibility are incomplete. FIELD virtual water currently targets players, not mobs/boats. See [player swimming](player-swimming.md) and [HANDOFF](../../HANDOFF.md) for exact human-validation scope. Compilation, successful mixin loading, synthetic energy checks, and a usable profile are distinct evidence and cannot substitute for gameplay validation.
