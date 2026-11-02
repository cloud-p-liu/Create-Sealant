# Create: Sealant

Sealant adds selectable chambers to moving Create: Aeronautics / Sable structures. A chamber defines a fixed local simulation domain for displaced water, air, internal water surfaces, and player interaction.

This is an experimental Minecraft mod. The default lightweight mode provides sealed displacement buoyancy; optional field modes simulate local air and water exchange. Excessive air backpressure, grid trapping, bottom suction, and some boundary forces remain open issues in field modes. See [current status](HANDOFF.md) before evaluating a build.

## Supported development stack

| Component | Pinned version |
| --- | --- |
| Minecraft | 1.21.1 |
| Java | 21 |
| NeoForge | 21.1.247 |
| Create | 6.0.10 |
| Create: Aeronautics | 1.3.2 |
| Sable | 2.0.4 |

Adapters target these versions. Changing dependency versions does not establish compatibility. Client and server must use the same Sealant build (network protocol 9).

## Getting started

Obtain the Sealant selection item from the creative tab and select two corners on the same Sable structure using the inherited honey-glue selection workflow. The selected block range includes both endpoints. The chamber follows its owner and retains its local dimensions after creation.

Open the chamber inspector with its item, **F8**, or `/sealanttools`. Open configuration through the Mods menu or `/sealantconfig`. World operations require permission level 2; display preferences are local. See the [configuration and command guide](docs/user-guide/configuration.md).

| World mode | Behavior |
| --- | --- |
| `LIGHTWEIGHT` (default) | Assumes a sealed, dry chamber; displaced-water buoyancy and distributed drag, without leakage or air exchange. |
| `FIELD_LOW` | Local 3D air/water field at 5 Hz; 2 × 2 face panels. |
| `FIELD_FULL` | Local 3D air/water field at 20 Hz; 4 × 4 face panels. |

Switching to lightweight mode pauses and preserves field data. It does not empty or average the cells. Switching back resumes the saved local state without simulating the paused interval.

## Build and verification

```sh
./gradlew build
# With dependencies already cached:
./gradlew build --offline
# Optional geometry/query microbenchmark:
./gradlew benchmarkChamber --offline
```

Use Java 21. Build artifacts are written to `build/libs/`. The first build needs network access to Gradle and the pinned dependency repositories. Gradle extracts required nested APIs from downloaded dependencies into `build/upstream/`; no local reference archive is required. `build` runs resource guards and the custom regression suite through `check`.

Automated checks cover geometry, declared block volumes, face passages, conservation, pressure coupling, fluid sleep, water meshes, player contact, and native force gates. They do not establish gameplay feel, visual correctness, multiplayer prediction, or TPS. Human validation steps and known limits are recorded in [HANDOFF.md](HANDOFF.md).

## Repository layout

```text
src/main/           Mod implementation, resources, and metadata templates
src/regression/     Executable regression checks and microbenchmark
docs/              User guide, architecture, development notes, and history
gradle/            Wrapper and build support
licenses/          Preserved upstream license notices
tools/             Reproducible asset tooling
.github/            Build workflow and contribution templates
```

Local worlds, logs, research archives, and private development notes are excluded from Git. Documentation has been rewritten in English and consolidated around the current implementation; history records describe superseded decisions rather than acting as current specifications. In-game Traditional Chinese localization remains supported alongside English.

## Contributing and governance

Join the [community discussions](https://github.com/cloud-p-liu/Create-Sealant/discussions). Read [CONTRIBUTING.md](CONTRIBUTING.md), [GOVERNANCE.md](GOVERNANCE.md), and the [Code of Conduct](CODE_OF_CONDUCT.md). The project uses open proposals and community maintainer governance. `cloud-p-liu` is the initial maintainer; no larger governing body is implied. Changes to simulation contracts require public discussion and evidence. See the [documentation index](docs/README.md) and [maintenance rules](AGENTS.md).

## License

Original Sealant work is licensed under the [MIT License](LICENSE). Copied Simulated code and NeoForged template files retain their notices in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [licenses/](licenses/). Upstream dependencies and their reserved assets are not relicensed by this repository.
