package com.sealant.buoyancy;

import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderBakery;
import net.minecraft.world.level.BlockGetter;

/**
 * Separate upstream cache for zero-displacement colliders. The bakery mixin changes
 * ONLY createVoxelColliderEntry's volume argument for this subclass. Never change the
 * ordinary cached collider: the same BlockState can exist on many unrelated structures.
 */
public final class NoBuoyancyColliderBakery extends RapierVoxelColliderBakery {
    public NoBuoyancyColliderBakery(BlockGetter level) { super(level); }
}
