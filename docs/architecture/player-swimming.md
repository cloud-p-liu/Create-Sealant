# Player internal-water contact and underwater visuals

FIELD modes expose current local visible water as virtual NeoForge WATER without changing world fluid blocks or registering a physics-affecting Sable WaterOcclusionContainer. Virtual internal swimming currently targets Player only; other mobs and boats are not implemented. Finite oxygen, GasPV oxygen use, and vacuum suffocation are not modeled.

## Physical contact

Use actual player and wet-cell OBBs plus horizontal physical water planes for convex-polyhedron clipping. Positive wet volume establishes contact; derive immersion above the player's local feet. Never substitute a world enclosing AABB, and never reuse player-contact sampling as chamber capacity estimation.

When part of a player exits the domain, clip root-world wet blocks against the exact chamber union and count the remaining exterior water. Domain-interior water comes from local field state, avoiding the surrounding sea falsely flooding a dry interior.

After Sable's native fluid scan, replace Player WATER heights so vanilla/NeoForge handles swimming, drag, rising/diving, sprint posture, fall distance, and extinguishing. In pure-water cases, restore velocity before the native scan to remove exterior sea-flow acceleration inside a dry chamber. Preserve lava/other mod-fluid data and their acceleration. Use upstream EntityMovementExtension water-owner tracking only when no tracking already exists; do not add carrier velocity a second time.

Exact logical eye points determine WATER/AIR independently of body contact. NeoForge breathing preserves potions, creative behavior, and damage hooks. Dry spaces remain infinitely breathable. LIGHTWEIGHT remains assumed dry, with exact-eye breathing and domain-exterior contact handling.

LocalPlayer overrides `isUnderWater`; an Entity-only hook does not cover it. Both now use the same logical Player eye/body predicate. Third-person camera position must not interrupt a submerged player's swimming. Camera fog and local display preferences are never physical inputs. This correction has automated/hook-loading evidence, but still needs a separate gameplay feel report.

## Synchronization

Server interaction cells come from field updates; client receives their raw water/relative surfaces through the existing water-state snapshot channel (5 Hz). Both use current logical poses, never visual interpolation. Shared spatial indexes and per-player position/bounds/mode/snapshot-revision caches invalidate on motion or new snapshots. Disconnect/world changes clear client data. After 40 ticks, expired client data returns to the existing chamber dry fallback. Budgets apply to complete groups rather than partial water domains.

Protocol remains 9 and persistence remains Field V3; deploy the same build on client/server. Latency, multiplayer prediction, native Sable water overlaps, mixed mod fluids, and passenger behavior need explicit gameplay validation.

## Visual contact

Camera fog and water particles use renderPose and the same shared-vertex six-tetrahedron signed surface as visible water. They query a point, never the player's body AABB. Mesh/surface interpolation is display-only and does not write W/G or player physics. Snapshot latency can cause small logical/visual differences.

Screen water overlays follow camera FogType in relevant chamber contexts. Breathing follows the character's eye point. Preserve fire/block/nonwater overlays. Bubble particles survive in virtual water and disappear on exit. Filter only water particles in dry areas, retaining smoke/fire. When the camera is in virtual water without root-world fluid nearby, add at most three vanilla UNDERWATER particles per four ticks; do not duplicate sea particles.

Visible water retains vanilla animated atlas tiles, biome tint, lighting, unit-tile UV clipping, opacity, 3D solid subtraction, and soft normal-based shading (horizontal 1.0, vertical 0.6). Its private material pass is independent of the external-water mask. Native sea culling supports vanilla/Sable VANILLA only. Inner water overlay uses actual inner glass faces; outside water uses outer faces, with adjacent-water tests and no projection through walls. The corrected inner placement received a positive user report; this does not validate every transparent material/order or renderer.

## Human test procedure

1. In FIELD_LOW survival, walk through shallow water, swim in deep water, rise/dive, and sprint into swimming posture.
2. Keep body/eyes submerged while switching first/third person and moving the camera across water. Swimming must continue; only visuals follow the camera. Then move the character's eyes out and verify breathing.
3. Cross flat/sloping surfaces with camera and water particles; bubbles should survive in water, while smoke/fire stay unaffected.
4. Move/rotate the carrier and cross the chamber/sea boundary. Disable local visuals and confirm physics/breathing remains consistent.
5. Repeat in FULL, after save/reload, and with multiplayer latency. Report feel, visuals, and prediction separately.

Automatic checks cover shallow/full/zero-volume contact, exact exterior subtraction, union continuity, rotated far-plot geometry, NeoForge hooks, tracking contracts, LocalPlayer wiring, and visible sloping-surface signs. Build and injection success do not constitute swimming validation.
