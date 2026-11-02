# Lazy geometry and external water-column cache

Cache geometry rather than fixed physical forces. Declared volumes remain authoritative; no internal capacity sampling or room averaging is introduced.

## Cached data

| Data | Bound and key |
| --- | --- |
| Collision shapes / declared occupancy | Persistent per dimension, up to 131,072 entries per table; rebuildable without losing physical state. |
| Exact block-envelope intersections | 8,192-entry LRU; cell box plus full poses of both sides, including position/rotation/scale. |
| Field capacity shares / face passages | Per group; relevant collision revision, SERVER rules, panel density, candidate bodies and exact poses. |
| Live noncarrier surface ownership / 6DOF gradients | 256-entry LRU; body pose/center of mass, candidate domains/bodies, geometry/shape revisions. |

Exact pose keys intentionally miss on real small motion. Never quantize movement or freeze intersections by a velocity threshold to improve hit rate. Each physics substep still reads current W/G, capacity, and velocity and performs implicit EOS coupling.

## External pressure

Cache **continuous segments** of root-world water columns by (x,z), retaining their top world Y including the fractional top-fluid height:

```text
depth = max(0, cachedTopY - sampleWorldY)
gaugePressure = density * abs(gravity) * depth
```

Different sample heights in the same segment do not rescan upward. Dry gaps separate pools; never borrow pressure from water above a gap. First scans may reach world height, without the old 64-block truncation. Unknown chunks yield NaN and are not cached as dry. The whole force evaluation pauses where required, rather than guessing. The 4,096-column LRU is shared per dimension by lightweight and field paths; counters count actual reads in the current interval.

This cache serves external root-world water only. Internal sampling retains each cell's own `Pcell + density*g*max(0, localSurfaceY-sampleY)` and never forms a shared column/room pressure. The cache does not change lighting, shaders, or rendering.

## Invalidation

Actual LevelChunk.setBlockState changes invalidate the column and surrounding 3 × 3 columns for neighboring fluid-height dependence. Geometry is dirtied only for edits intersecting relevant body/domain bounds plus the probe margin, preventing distant random ticks from invalidating every group. The shared geometry revision can still rebuild multiple groups; this is not minimal per-face invalidation.

Sable block-change and section-addition hooks invalidate actual shapes. Same-state buoyancy metadata refreshes do not. Chunk load/unload invalidates affected geometry and water-column borders without forcing loads. SERVER rule changes clear declared values/intersections and advance revisions. Panel-density changes rebuild links and clear old flux indexing. Marker topology changes clear old surface-ownership caches. Dimension unload releases runtime caches. None are persisted or alter V3 parcel semantics.

Third-party tools that edit palettes without these hooks are not comprehensively validated.

## Verification

Checks include repeated-height hits, partial top height, dry-gap separation, raised/drained dirty columns, unknown-chunk retry, a 200-block water column, chunk replacement, exact-pose hits/misses, LRU eviction, and hook signatures. Gameplay follow-up should include edits to slabs/stairs, rule changes, water-height changes, rotation, chunk reload, and the dry-cylinder rebound regression.

Counters include `field_geometry_rebuilds/cache_hits`, `volume_cache_hits/misses`, `live_volume_cache_hits/misses`, and `live_face_cache_hits/misses` (full names use the corresponding prefix). `grid_collision_blocks` counts actual shape-cache construction in the interval. Cache correctness checks do not establish TPS or every external editing tool's behavior.
