# Sleeping closed fluid exchange

FIELD_LOW/FIELD_FULL can skip exchange for a closed group at rest. LIGHTWEIGHT does not advance the field at all. Sleep is a scheduling decision, never cell merging or pressure averaging.

## Entry conditions

Require three consecutive field observations with finite state and fluxes:

- No link to an external reservoir, including a currently dry opening.
- Water-head pressure difference at the same effective wetted face height is at most 1e-3. Do not compare vertically separated cell base pressures as if hydrostatic gradients were an error.
- Where both sides share a dry opening, gas base-pressure difference is at most 1e-5.
- Whole-group predicted absolute exchange residual is at most **0.000400 block³/game tick**.

For each face, conservatively take the maximum of transportable historical water-flux magnitude and predicted water rate, add countercurrent displacement and gas-volume rate, then sum absolute quantities over all faces and multiply by 0.05 s. Convert GasPV rate to volume using the lower local pressure. Do not cancel opposite flows or scale tolerance by face count. Historical water flux counts only when its donor has wet contact; dry-donor momentum is not movable water. Predicted reverse flow still counts.

The tolerance covers reported residuals near 0.000018 and 0.000310. It must not be repeatedly raised for unexplained peaks. It neither clears small water quantities nor modifies W/G/P/flux to manufacture rest.

## Retained work and wake-up

Every update rechecks actual current openings, pressure/head differences, flux, and displacement. Failed checks immediately wake and solve in that same update. Sleep skips the eight-substep bounded air/water exchange and internal displacement only. Preserve W/G, pressure gradients, historical face fluxes, EOS, carrier buoyancy, held hydrostatic loads, live noncarrier EOS impulses, drag, synchronization, debug, and saving. The current actual displaced-volume report is zero while skipped.

When rebuilding a runtime field in the same group with compatible cell count/gravity, inherit only consecutive-rest observations. Never copy old W/G, pressure, or a sleep flag as the decision. Body count, a changed geometry key, pose identity, or the presence of a noncarrier collision body alone cannot prevent sleep; no carrier-only exception exists. Current physical compression/openings/head changes still wake immediately. Balanced geometry changes need not restart the count. New topology, mode changes, or changed gravity require appropriate reconfirmation. Unknown chunks are not evidence of closed rest.

Field wake-up follows the selected 5/20 Hz cadence. Live physics-substep EOS continues independently. Sleep flags/counters are runtime only, not Field V3 or packet data.

## Diagnostics and interpretation

Report water flow, gas flow, and countercurrent displacement separately in block³/game tick, exterior path count, maximum effective head/gas differences, and blocking reason. Reasons distinguish openings, water head, gas pressure, excessive residual, nonfinite state, and pending consecutive observations. Evaluate all links before reporting. The current report uses a single post-solve state; pre-update residual is labeled separately. Reading diagnostics must not change sleep decisions.

A dry chamber can have genuine gas flow due to capacity changes; gas residual is not evidence of water ingress. A sealed uniform dry chamber must not acquire internal flow just because the exterior is submerged. One reported peak was 0.00299189; a later dry case had water/displacement zero and gas residual 0.00353932 with no exterior link. The chamber moved in water. The user found kelp entering the domain; removing it restored sleep. Kelp had incorrectly fallen under default occupancy 1 and now has explicit occupancy 0. Other actual collision bodies must still be able to rest, while real compression remains active.

Older LOW status mistakenly labeled a whole 0.2 s advance's displacement as per tick. Current reporting uses `displacedVolume * 0.05 / advancedTime`; do not compare old and new values without this conversion.

Counters `fluid_exchange_skips`, `fluid_exchange_updates`, `fluid_sleep_groups`, and `fluid_sleep_wakes` are cumulative group events, not current body/group counts or TPS.

## Verification

Checks cover accepted 0.000018/0.000310 and rejected 0.000800 residuals, no cancellation, per-tick normalization, 100 quiet rebuilds, noncarrier obstruction with small balanced pose/capacity changes, real head/compression/opening wake-up, dry-donor momentum, consistent diagnostics, conserved tiny W/G, hydrostatic gradients, countercurrent exchange, and unchanged mass while sleeping. Existing diving-bell, lazy-cache, and cylinder checks remain.

The user confirmed sleep after removing kelp. Later generalized collision-body inheritance still needs gameplay follow-up: closed wet/dry chambers should sleep and retain buoyancy; holes, actual compression, and tilting-induced flow should wake them. Verify the dry-cylinder rebound separately.
