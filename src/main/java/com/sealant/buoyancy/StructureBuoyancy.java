package com.sealant.buoyancy;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.nbt.CompoundTag;

/** Explicit per-structure water-buoyancy switch. Automatic chamber membership is a transient overlay. */
public final class StructureBuoyancy {
    private static final String DISABLED = "sealant:water_buoyancy_disabled";
    public static boolean isDisabled(ServerSubLevel structure) {
        return isManuallyDisabled(structure) || com.sealant.chamber.runtime.ChamberRuntime.isManaged(structure);
    }
    public static boolean isManuallyDisabled(ServerSubLevel structure) {
        CompoundTag tag = structure.getUserDataTag();
        return tag != null && tag.getBoolean(DISABLED);
    }
    public static int setEnabled(ServerSubLevel structure, boolean enabled) {
        var system = SubLevelPhysicsSystem.get(structure.getLevel());
        if (system == null || !(system.getPipeline() instanceof BuoyancyPipeline adapter))
            throw new IllegalStateException("Water buoyancy switching requires the Sable 2.0.4 Rapier adapter");
        boolean previous = isManuallyDisabled(structure);
        if (previous == !enabled) return 0;
        writeDisabled(structure, !enabled);
        try {
            // Refresh existing native voxels as well as the uploads intercepted in the future.
            int count = adapter.sealant$refreshBuoyancy(structure);
            system.getPipeline().wakeUp(structure);
            return count;
        } catch (RuntimeException failure) {
            writeDisabled(structure, previous);
            try { adapter.sealant$refreshBuoyancy(structure); }
            catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
    }
    private static void writeDisabled(ServerSubLevel structure, boolean disabled) {
        CompoundTag tag = structure.getUserDataTag();
        if (tag == null) { tag = new CompoundTag(); structure.setUserDataTag(tag); }
        if (disabled) tag.putBoolean(DISABLED, true); else tag.remove(DISABLED);
    }
    private StructureBuoyancy() {}
}
