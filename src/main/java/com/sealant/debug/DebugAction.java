package com.sealant.debug;

/** Closed action set. Client packets can never supply arbitrary commands. */
public enum DebugAction {
    REFRESH(null), WATER_TOGGLE(null), AIR_TOGGLE(null),
    STATUS("sealant chamber status"), AUTO_TOGGLE(null), PHYSICS_TOGGLE(null),
    BUOYANCY_ON("sealant buoyancy on"), BUOYANCY_OFF("sealant buoyancy off"), BUOYANCY_STATUS("sealant buoyancy status"),
    DRAG_ON("sealant drag on"), DRAG_OFF("sealant drag off"), DRAG_STATUS("sealant drag status"),
    PROFILE_START(null), PROFILE_REPORT("sealant perf report"), RESET(null);
    public final String command;
    DebugAction(String command){this.command=command;}
}
