# Profiling and measured performance

Use `/sealant perf start 30`, wait for completion, then `/sealant perf report`. A capture spans game ticks, not fixed wall time. Every phase `n` is a **call count**, never TPS. Compare totals divided by the same recorded tick count for amortized cost. Timers can nest; do not sum all rows.

`server_tick_work` measures ServerTickEvent.Pre → Post work, across dimensions, excluding normal tick waiting. Some packet work occurs outside that interval. `player_move_packet` measures synchronous server-thread movement processing, not Netty queue time or the whole player tick. GC counts/time cover JVM changes within the recorded intervals, not Sealant-exclusive or server-thread-exclusive allocation.

`native_step` measures each native physics step and always runs normally. `native_water_collect` measures native water-force collection. `drag_gate` excludes that collection time. `live_body_pressure` is nested in `chamber_impulses`; `internal_body_pressure` is nested in low-frequency `chamber_pressure`. `obb_volume` is nested in `auto_tick`.

Field breakdown: geometry, remap, build, exchange, loads, save, snapshot, and sync are components of field preparation/update. `field_geometry_rebuilds` diagnoses invalidation; accompanying reason counters can overlap and must not be added as unique rebuilds. Cache hit/miss counts and grid/world reads are interval increments. Body/cell/group counters often accumulate visits rather than unique objects.

## Latest user-supplied sample

599 recorded work intervals, 150 field updates, 1,200 native steps. This is observational evidence on the user's machine, not a controlled benchmark or general performance promise.

| Phase | Mean ms/call | p95 ms | Max ms | Total ms |
| --- | ---: | ---: | ---: | ---: |
| server_tick_work | 9.5209 | 22.6090 | 43.5419 | 5703.04 |
| chamber_pressure | 15.2505 | 18.6282 | 27.4944 | 2287.58 |
| field_exchange | 12.2545 | 15.1187 | 24.0078 | 1838.17 |
| field_geometry | 2.4412 | 3.7271 | 6.8397 | 366.18 |
| live_body_pressure | 0.1318 | 0.2059 | 9.3543 | 158.11 |
| native_step | 0.0995 | 0.1676 | 0.8193 | 119.43 |
| native_water_collect | 0.2730 | 0.3708 | 1.0655 | 163.78 |

Chamber pressure amortizes to about 3.819 ms/recorded tick; exchange is about 80.35% and geometry 16.01% of chamber-pressure total. Live EOS and native step amortize to about 0.264 and 0.199 ms/tick. All recorded server-work intervals in this sample were under 50 ms; this is not proof of sustained 20 TPS outside the sample.

Geometry rebuilt 150 times; `geometry_other_body_pose=150`, `geometry_owner_pose=67`, `geometry_collision_revision=5`. Exact moving poses naturally miss lazy keys. Membership changes were zero. `fluid_exchange_updates=150`, so this capture did not demonstrate sleep savings. Pressure cells=10,815, panels=423,000, root-world pressure reads=0. GC=6 collections/56 ms.

The previous sample had server mean 54.1259 ms, p95 149.3939, max 374.5286; chamber mean 79.0553 ms, native-step mean 1.9156 ms, and GC=19/793 ms. Pressure cells differed (13,500), as did activity/physical state. Do not attribute the entire reduction to a single optimization.

## EOS numerical optimization

The local EOS remains double precision. The analytic positive-root path uses finite `sqrt(b*b + y*y)` where safe and `hypot` for overflow/subnormal cases, with a cancellation-resistant quadratic root. A 100,000-case randomized comparison plus extreme values found a maximum 3 ULP difference from the previous evaluation. Synthetic EOS throughput improved approximately 2.2–2.5×; this is not a TPS multiplier. Keep finite/positive and conservation regressions; never replace double state with float merely to match rendering.

## Earlier panel-density observations

At the same reported 7,950 pressure cells, reducing 4 × 4 to 2 × 2 panels reduced accumulated panels 1,692,000 → 423,000 and chamber-pressure mean 50.7898 → 28.6149 ms/call. Native-step total simultaneously rose 521.41 → 6668.56 ms, GC rose 125 → 580 ms, and server-work mean rose 24.1097 → 37.3098 ms. These were not controlled A/B captures; less panel work did not imply a faster whole tick. Player movement and debug display also differed.

## Reproduction discipline

Keep world, bodies, poses, mode, camera/debug display, activity, JVM warm-up, and capture length comparable. Separate standing and moving samples. Compare auto-off, auto-on/physics-off, and active physics only with explicit setup. Inspect shape refresh, geometry rebuild reasons, exchange, live pressure, native stepping, allocation/GC, and rendering independently. The [geometry microbenchmark](benchmarks.md) omits the actual world/native/render pipeline.

The rejected global native-substep transport solver reached approximately 300 ms average tick and failed convergence in real scenes. It is excluded from current source. Do not reintroduce native-rate global coupling without a separate design review, stable real-scene reproductions, and a measured cost budget.
