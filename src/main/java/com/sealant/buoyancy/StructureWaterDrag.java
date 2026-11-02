package com.sealant.buoyancy;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.nbt.CompoundTag;

/** Independent water-drag mode, stored with the structure and read each native force-collection tick. */
public final class StructureWaterDrag {
    private static final String DISABLED = "sealant:water_drag_disabled";
    public static boolean isDisabled(ServerSubLevel structure) {
        return isManuallyDisabled(structure) || com.sealant.chamber.runtime.ChamberRuntime.isManaged(structure);
    }
    public static boolean isManuallyDisabled(ServerSubLevel structure) {
        CompoundTag tag = structure.getUserDataTag();
        return tag != null && tag.getBoolean(DISABLED);
    }
    public static void setEnabled(ServerSubLevel structure, boolean enabled) {
        var system = SubLevelPhysicsSystem.get(structure.getLevel());
        if (system == null || !(system.getPipeline() instanceof BuoyancyPipeline))
            throw new IllegalStateException("Water drag switching requires the Sable 2.0.4 Rapier adapter");
        CompoundTag tag = structure.getUserDataTag();
        if (tag == null) { tag = new CompoundTag(); structure.setUserDataTag(tag); }
        if (enabled) tag.remove(DISABLED); else tag.putBoolean(DISABLED, true);
        system.getPipeline().wakeUp(structure);
    }
    private StructureWaterDrag() {}
}
