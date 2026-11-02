package com.sealant.buoyancy;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;

/** Implemented only by the Rapier adapter, so unsupported backends cannot report fake success. */
public interface BuoyancyPipeline {
    int sealant$refreshBuoyancy(ServerSubLevel structure);
}
