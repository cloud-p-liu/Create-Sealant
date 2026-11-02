# Geometry/query microbenchmark

Historical run: 2026-10-29, fixed seed 1234, 30 warm-up rounds and 100 measured rounds, game closed. Execute `./gradlew benchmarkChamber --offline`. Each round rebuilds the index and queries all entity boxes. Chambers are 8 × 8 × 8; entities are 0.6 × 1.8 × 0.6. This benchmark uses identity poses; rotations have separate correctness checks.

| Chambers/entities | Distribution | Mean ms/round | p95 ms/round |
| --- | --- | ---: | ---: |
| 10/100 | 2048 × 2048 | 0.0536 | 0.0591 |
| 100/1000 | 2048 × 2048 | 0.2461 | 0.3478 |
| 1000/5000 | 2048 × 2048 | 1.6604 | 2.2928 |
| 10/100 | 32 × 32 | 0.1034 | 0.1093 |
| 100/1000 | 32 × 32 | 1.1615 | 1.1868 |
| 1000/5000 | 32 × 32 | 6.8290 | 7.3171 |

This measures production ChamberGrid.anyMatch plus inclusive SAT in synthetic distributions; most sparse queries do not contact a chamber. It excludes world loading, Sable broad phase, JNI collider changes, fluid field solving, networking, and rendering. JVM warm-up and machine load affect results. It cannot be converted to TPS or used to promise large-structure entry/exit costs. Use [real profiling](performance.md) for those questions.
