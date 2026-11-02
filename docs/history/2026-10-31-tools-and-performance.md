# Inspector and profiling decisions

Create/Catnip BaseConfigScreen was reused instead of copying upstream UI. The inspector item, F8, client commands, persistent local display options, permission-2 world controls, server-validated enum actions, and target-confirmed group reset were introduced. World configuration uses SERVER spec and ValueSpec checks; CLIENT preferences never control physics.

The user confirmed the original interface. This does not imply every later permission/reload/changed-target scenario was individually exercised. Native-body UUIDs and chamber marker UUIDs remain distinct.

Profiling added native water-force collection, native stepping, synchronous player movement packets, server tick work, and interval JVM GC accounting. Call counts are not TPS, nested phases are not additive, and a game-tick capture can exceed its nominal wall-clock duration. Synthetic query timings remain separate. See [configuration](../user-guide/configuration.md) and [performance](../development/performance.md).
