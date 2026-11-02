# Superseded strict sleep inheritance

An earlier sleep-inheritance policy demanded strict geometry/pose/state identity and restricted cases involving other collision bodies. This prevented physically balanced chambers from sleeping merely because geometry objects rebuilt or a chamber moved.

Current behavior inherits only consecutive-rest observations for the compatible runtime group, then rechecks actual current openings, wet head, shared dry gas pressure, transportable flux, and countercurrent exchange. Never copy the sleep flag or W/G to manufacture rest. No carrier-only or zero-collision-body exception remains. See [current sleep](../architecture/fluid-sleep.md).
