# Declared block occupancy and face passages

The approved scheme A defines the value as **occupied block volume**: default 1.0 with explicit exceptions. This definition applies to FIELD_LOW/FIELD_FULL only. It does not change LIGHTWEIGHT, native collision shapes, or rigid-body mass. Volume is not estimated from face panels or internal voxel samples.

## Built-in values

| Block/state | Occupied volume per unit block |
| --- | ---: |
| Any unlisted block, including nonfull/noncolliding blocks | 1.0 |
| Air, cave air, void air | 0 |
| LiquidBlock (vanilla water/lava) | 0 |
| kelp (all ages), kelp_plant | 0 |
| minecraft:snow (all layer states) | 0 |
| soul_sand, honey_block | 1.0 |
| Slab type=bottom/top | 0.5 |
| Slab type=double | 1.0 |
| Stair shape=straight | 0.75 |
| Stair shape=inner_left/inner_right | 0.875 |
| Stair shape=outer_left/outer_right | 0.625 |

Stair facing and half do not change volume, but change passage geometry. Waterlogging does not create field water mass or change occupancy. `snow_block` is not the snow-layer exception and remains 1.0. Kelp exceptions prevent collisionless root-world kelp entering a moving hull from spuriously removing a whole cell's capacity; this does not authorize measuring arbitrary blocks by collision volume.

SERVER overrides accept block IDs and legal state predicates, with values in [0, 1]:

```toml
[field]
blockVolumes = ["minecraft:oak_slab=0.5", "minecraft:oak_slab[type=double]=1.0", "minecraft:oak_stairs[half=top,shape=inner_left]=0.875"]
```

More state conditions win; equal specificity uses the later rule. User rules override built-ins. An empty list uses defaults. Invalid syntax/ranges are spec-validated; unknown IDs or invalid properties/values explicitly fail field preparation and suspend its forces rather than silently defaulting. SERVER edits require permission 2.

## Capacity and flow

For an owner cell or geometric boundary fragment:

```text
C = geometricCellVolume * (1 - declaredOccupancy)
```

Other moving bodies and root-world blocks subtract exact intersection volume of their unit block-envelope OBBs with the cell, multiplied by declared occupancy. Collision-shape volume and world-AABB overlap are not substitutes. Contributions from multiple bodies add and clamp capacity nonnegative; interpenetrating block envelopes can overcount obstruction. Unknown chunks suspend evaluation rather than load or guess.

Occupancy affects flow through capacity and local EOS, without a direct speed multiplier:

```text
C = W*B/(B + P - P0) + GasPV/P
visibleWaterVolume = W*B/(B + P - P0)
B = 10000; P0 = 101.325
q = (q_old + dt*A*deltaPressure/(density*L))
    / (1 + dt*(2 + 2*abs(q_old)/A))
waterRate = q * donorWetFraction
```

A is open panel area and L cell-center distance. Local pressure/head and wet fractions derive from each cell's state and visible volume. Gas exchange and internal countercurrent displacement retain their own existing formulas using the same available passages. A blocked face has no link and zero exchange regardless of capacity. There is no universal monotonic occupancy-to-speed factor. Preserve eight implicit substeps and two-phase conservation; external reservoirs do not use internal countercurrent displacement.

## Passage geometry and persistence

LOW samples 2 × 2 panels per face; FULL samples 4 × 4. At a shared panel center, probe actual collision shapes on both sides at offsets of 1e-6 blocks. Either side blocked means no passage. Preserve each panel's position, height, area, and wet/dry contact. Do not average the two sides' open fractions or infer capacity from the number of open panels.

Panel-density changes discard incompatible old face fluxes while retaining W/G and parked parcels. Owner analytic shape differences only locate V3 spatial shares and height intervals; normalize share weights to declared capacity. They do not determine capacity or add cell degrees of freedom. Moving rotated-body polyhedra are not saved as a new parcel format. Distinct disconnected cavities within one cell still share state; panel tests alone cannot establish their internal connectivity. Sub-panel cracks remain a resolution limit.

All shapes, declared values, intersections, and panels follow [lazy invalidation](lazy-geometry.md). Current pressure remains dynamically solved.

## Validation

Regression coverage includes real vanilla BlockStates/shapes, full/default nonfull blocks, snow/soul-sand/honey/kelp exceptions, all three slab states, and 40 stair combinations (4 facings × 2 halves × 5 shapes) at both panel densities. It checks sealed/open top and side faces, aligned/misaligned openings, override precedence, invalidation, capacity/EOS effects at the same opening, conservation, and parked mass retention.

Human follow-up: compare sealed/full blocks, top/bottom slabs, and oriented stairs in FIELD_LOW; verify flow only through open faces, reasonable water levels, rotation, and save/reload. Automated vanilla-state initialization does not validate every modded block or gameplay flow rate.
