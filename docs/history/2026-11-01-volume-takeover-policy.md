# Superseded adjustable overlap takeover

The original optional OBB-volume policy defaulted off and exposed `minimumOverlapFraction=0.01`. It used the complete other-body OBB including holes as denominator, exact convex clipping against nonoverlapping same-owner connected-union pieces, and original-box contact for retaining/releasing existing takeover. It did not blend forces or apply an MTV. Percentage, volume, centroid, and state diagnostics were available.

Tests covered known intersections, 45-degree rotation, containment, zero-volume contacts, far-plot invariance, symmetry/centroids, nonuniform scale, thin positive overlap, deduplicated unions, and boundary comparisons. The user reported successful takeover with slight acceptable oscillation. Unreported rotation/reload/performance scenarios were not presumed passed.

The user later preferred **complete entry before takeover and complete exit before release**, with roughly 1% boundary tolerance. Current fixed internal 99%/1% hysteresis replaced the adjustable threshold and removed percentage controls/reports. Prior success does not validate the revised feel. See [current takeover](../architecture/chambers.md).
