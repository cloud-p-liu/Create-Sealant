package com.sealant.chamber.client;

/** Particle factory tag, independent of renderer/sprite classes shared with lava or smoke. */
public interface ChamberWaterParticle {
    boolean sealant$isWaterVisual();
    void sealant$setWaterVisual(boolean water);
}
