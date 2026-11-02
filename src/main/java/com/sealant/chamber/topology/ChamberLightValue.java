package com.sealant.chamber.topology;

/** Native light levels kept in separate channels; block floor never dims an object's own emission. */
public record ChamberLightValue(double sky,double block,boolean inherited) {
    public float shaderScale(float nativeScale) {
        if(!inherited)return nativeScale;
        // Reuse Sable's per-body scalar: [0,1] sky fraction + even integer block-light floor.
        // Matching shader decode is installed by ChamberLightShaderMixin. Values never enter world light data.
        return (float)(Math.clamp(sky,0,15)/15+2*Math.round(Math.clamp(block,0,15)));
    }
    public int packedBlockFloor(int packed) {
        if(!inherited)return packed;
        int local=(packed>>4)&15,ambient=(int)Math.round(Math.clamp(block,0,15));
        return (packed & ~0xf0) | (Math.max(local,ambient)<<4);
    }
}
