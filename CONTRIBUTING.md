# Contributing

Contributions are welcome through GitHub issues and pull requests. Read the [Code of Conduct](CODE_OF_CONDUCT.md), [governance](GOVERNANCE.md), [current status](HANDOFF.md), and [maintenance rules](AGENTS.md).

## Discuss the change

Report a concrete reproduction, expected behavior, actual behavior, pinned dependency versions, world mode, and relevant logs with personal information removed. For performance, include complete profile totals/counts and scene conditions; `n` is not TPS. Avoid uploading private worlds or credentials. Keep known physical issues visible rather than describing a rollback as a fix.

Open a proposal before changing conservation, persistence, world modes, occupancy semantics, native coupling, public configuration, or dependency compatibility. Explain the user-visible outcome, alternatives, validation plan, and performance budget. Small fixes and documentation corrections can proceed directly as pull requests.

## Development

Use Java 21 and the committed Gradle wrapper. Run `./gradlew build` (or `--offline` after caching dependencies). Main code is in `src/main`; custom executable checks are in `src/regression`. Required nested APIs are extracted from Gradle dependencies into build output; ignored local research archives are not prerequisites.

Keep mod/resource IDs stable. Preserve W/G conservation, parked mass, cell-local freedoms, and manual/native gate intent. Add focused regression coverage when changing numerical/geometry behavior. Do not add tests that only repeat trivial implementation details. Rendering/coordinate changes also need rotated gameplay checks. Clearly mark human checks pending until reported.

Update English current documentation and HANDOFF when behavior or validation changes. In-game localization may use its target language. Do not publish local saves, logs with personal data, upstream reserved assets, or rejected experimental code. Include provenance and license notices for new third-party material.

## Pull request review

Describe the problem and resulting behavior, scope, automated checks, human evidence, and remaining limits. A maintainer reviews ordinary contributions; substantial decisions follow GOVERNANCE. Address review comments publicly. Where feasible, a maintainer other than the author approves implementation changes. During the single-maintainer bootstrap, self-merged work must document checks and any independent-review gap.

Contributions are accepted under the project's MIT license for original work; preserve applicable third-party notices. Do not submit material you lack permission to contribute. No separate copyright assignment or CLA is required. Maintainer membership and voting rules are defined in GOVERNANCE.
