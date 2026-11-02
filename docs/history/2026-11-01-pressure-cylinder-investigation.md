# Dry-cylinder ejection investigation

The user reported abnormal drag/rebound on lightweight structures entering dry chambers, bottom/edge suction, and outward ejection on partial exit. Moving block envelopes reduce cell capacity before local transport catches up. Missing pressure contributions at solid contacts or outside the domain may also break cancellation. These mechanisms were identified without claiming a unique cause for every case.

A concrete FIELD_LOW reproduction used a dry atmospheric 3 × 3 × 4 cylinder, no outside water, and a 3 × 3 × 1 wood plate dropped from about two blocks above. Half-entry ejected it approximately 100 blocks. This was an anomaly, not validated normal air buoyancy.

A synthetic fixed half-entry audit retained 36 × P0 GasPV across 36 cells, with the top nine capacities reduced to 0.5. Top pressure started at 202.650000; after 0.2 s LOW gave 156.360447, and four 0.05 s FULL updates gave 154.926667. Long-time equilibrium was 115.8 (=36/31.5 × 101.325). Water remained zero and gas conserved. The equilibrium plate gauge load was 130.275 and isothermal relative free energy 31.119961 in prototype units. This audit did not include Sable collision, moving remap, or takeover and did not reproduce actual launch dynamics.

Held LOW loads could continue applying a compressed pressure force for approximately 0.2 s after retreat/exit, motivating current-substep implicit EOS coupling. That correction later received a positive human result for the original dry cylinder. Contact/boundary pressure and broader grid-trapping issues remain unresolved. See [current coupling](../architecture/pressure-coupling.md).
