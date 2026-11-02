# Superseded inner-water projection

An earlier overlay projected internal water to exterior shell surfaces, producing the wrong side of glass. The user corrected the intended behavior: internal water belongs on the glass's inner surface and should be visible through the glass from outside.

The correction uses actual exposed collision-shape faces and adjacent-water tests, with internal water clipped by the shared tetrahedral surface and exterior sea water by its own water line. It forbids through-wall projection. The user confirmed the corrected placement. This is not blanket validation of all transparent materials, sort order, or renderer adapters. See [current visual behavior](../architecture/player-swimming.md).
