# Rejected global native-substep transport solver

The user reported air resistance exceeding buoyancy/water drag, structures pinned below water, and stronger reaction at cell centers than boundaries. A global solver attempted to couple per-face water/gas transport, per-cell EOS, and all candidate noncarrier 6DOF velocities at each native substep. LOW geometry/support/hydrostatic preparation and internal countercurrent displacement would remain low frequency. Its goal was to avoid treating open cells as sealed pistons while W/G waited for the next field update.

Synthetic cases covered cell crossing, small buoyancy, open/closed water paths, phase/velocity combinations, vacuum/trapped air, tiny inventories, historical flux, and piston energy. These checks did not establish real-world stability or cost.

Real runs failed successively with “No positive phase momentum predictor”, nonlinear nonconvergence near vacuum pressure bounds, and “Phase inventory roundoff has no conservative correction”. Captured numeric cases included 72 cells/781 faces and 90 cells/965 faces. Attempts added supply bounds and derivatives, projected Newton/continuation, compensated summation, and corrections along actual phase-open paths without cross-wall transport or fabricated water. Passing these offline reproductions still did not establish a usable game build.

The user ultimately reported an average tick near **300 ms**. After normal saves/shutdown, the entire global solve/retry execution path was withdrawn and the low-frequency exchange plus local per-body EOS baseline restored. Rejected source/numeric archives remain local research outside Gradle source sets and are not published as active implementation. Do not reintroduce the experiment as a solved pressure issue or interpret its synthetic tests as TPS evidence.

Current excessive backpressure/grid trapping remain open. A future proposal needs stable real-scene reproductions, conservation/positivity, credible boundaries, and an explicit measured performance budget. See [current coupling](../architecture/pressure-coupling.md) and [performance](../development/performance.md).
