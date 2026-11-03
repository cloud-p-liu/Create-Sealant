# Current maintenance handoff

Updated for the initial public repository on 2026-11-03. Start with [AGENTS.md](AGENTS.md), [architecture](docs/architecture/chambers.md), and [configuration](docs/user-guide/configuration.md). Historical notes are curated English decision records, not current instructions.

## Implementation baseline

Java 21 / Minecraft 1.21.1 / NeoForge 21.1.247 / Create 6.0.10 / Aeronautics 1.3.2 / Sable 2.0.4. Network protocol 9, Field V3 persistence with V2/V1 migration. The main mod ID remains `sealant`; marker ID remains `sealant:chamber_structure`.

Three SERVER modes are implemented: default LIGHTWEIGHT, FIELD_LOW at 5 Hz, and FIELD_FULL at 20 Hz. Field modes retain local W/G/P and face fluxes. Lightweight mode pauses their data and assumes a sealed dry domain. Declared occupancy determines capacity; LOW/FULL face sampling is 2 × 2 / 4 × 4 solely for passage detection. Lazy intersections and external water-column tops avoid unnecessary scans.

Noncarrier bodies use current-substep implicit 6DOF EOS coupling plus separately held low-frequency hydrostatic loads. A global native-substep two-phase solver was rejected and removed from the source set after convergence failures and an approximately 300 ms average tick report. Do not restore it as a supposed fix. Current unresolved issues include excessive air reaction, sublevels pinned below internal water surfaces or near cell centers, bottom suction, and pressure behavior at partial domain exit. The earlier dry-cylinder ejection fix does not resolve these cases.

Rendering uses shared-vertex marching tetrahedra, animated vanilla water materials, biome tint, separate internal/external glass overlays, and camera-based underwater effects. Player swimming/breathing uses logical wet-cell/body/eye contact rather than camera position. Third-person swimming received a dedicated LocalPlayer hook correction; successful injection is not a gameplay pass.

## Validation evidence and limits

User-confirmed behavior includes rotated selection, native buoyancy/drag gates and restoration, auto takeover, parent lighting, field air/water exchange, continuous water surfaces, pink gas debug, original inspector UI, lightweight sea culling and exact-eye breathing, original inset-entry hysteresis, and corrected inner glass overlay placement.

The specific FIELD_LOW dry atmospheric cylinder test (3 × 3 × 4 interior, 3 × 3 × 1 wood plate dropped from roughly two blocks above, no outside water) no longer ejects the plate approximately 100 blocks upward; the user reports acceptable rebound. No measured rebound height or universal energy guarantee is claimed.

Removing kelp that entered the domain restored sleep in the user's dry chamber. Kelp now has explicit zero occupancy. The later generalized sleep-counter inheritance with other collision bodies still needs separate gameplay confirmation. Genuine compression must continue to wake exchange.

Pending human validation includes revised complete-entry/complete-exit takeover feel, latest FIELD swimming and breathing in both camera modes, rotation and mixed-fluid boundaries, multiplayer prediction, state-rule effects after reload, and the full lazy-invalidation/sleep scenario matrix. Some render additions were loaded successfully without full visual confirmation. Unsupported renderer adapters remain unsupported.

## Latest supplied performance sample

599 recorded server-work intervals, 150 field updates, 1,200 native substeps. Counts are calls, not TPS. Server-work mean 9.5209 ms, p95 22.6090 ms, maximum 43.5419 ms. Chamber-pressure mean 15.2505 ms per call, total 2,287.58 ms. Exchange mean 12.2545 ms and geometry mean 2.4412 ms dominate field work. Geometry rebuilt 150 times with other-body pose changes on every update; membership changes were zero. GC: 6 collections / 56 ms within recorded tick intervals.

This sample is usable on the user's machine; it does not establish controlled before/after attribution or a general performance guarantee. [Performance notes](docs/development/performance.md) preserve comparable totals and caveats.

## Publication changes

The project is prepared as **Create: Sealant**, with stable runtime IDs, MIT licensing for original work, preserved upstream notices, English public documentation, `src/regression` for custom checks, and public contribution/governance documents. Original local Chinese notes are retained in ignored `.local-notes/`; upstream references, experimental archives, worlds, and logs are not published. The item texture is replaced with an independently generated icon to avoid carrying uncertain upstream asset provenance.

## Next work

Investigate pressure transport/backpressure using small reproducible dry and wet cases and measured solver cost. Preserve conservation, local degrees of freedom, diving-bell trapping, and current-substep capacity coupling. Do not compensate with arbitrary force caps, gas-pressure reduction, or a native-rate global solve without a reviewed proposal and cost evidence.

Before any gameplay restart, coordinate with the user and confirm a normal save/shutdown. Complete automated checks first, then ask for narrow human observations. Update this handoff with actual evidence rather than treating compilation or log loading as gameplay success.

## Publication verification (2026-11-03)

`./gradlew clean build --offline --no-build-cache --no-configuration-cache` passed with all 14 tasks executed, including nested API extraction, Java compilation, resource guards, and the complete executable regression suite. Relative documentation links and a credential/local-path scan of public files passed. Upstream reference archives were not required. The built JAR was checked for MIT/third-party notices and absence of nested dependency JARs. No game was launched for this documentation/structure task. Existing NeoForge deprecated-API and Gradle future-version warnings remain; compatibility with Gradle 10 is not claimed.

The first Linux CI run exposed a previously cached Ponder API download failing upstream TLS certificate validation. The build now extracts Ponder 1.0.82 from the pinned Create 6.0.10 bundle, matching the existing runtime, instead of downloading the stale 1.0.69 compile API separately. TLS verification remains enabled; no Ponder dependency is repackaged.
