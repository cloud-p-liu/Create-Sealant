package com.sealant.chamber.physics;

import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

/** Surface quadrature ONLY. No cell capacity or solid volume is inferred here. */
public final class ChamberFacePassage {
    public static int resolution(com.sealant.config.SealantServerConfig.Simulation mode){
        return mode==com.sealant.config.SealantServerConfig.Simulation.FIELD_FULL?4:2;
    }
    public static final double SIDE_OFFSET=1e-6;
    public static Vector3d point(AABB rectangle,int axis,double plane,int i,int j,int resolution){
        int u=(axis+1)%3,v=(axis+2)%3;
        return new Vector3d().setComponent(axis,plane)
                .setComponent(u,ChamberCellGrid.min(rectangle,u)+(i+.5)*ChamberCellGrid.size(rectangle,u)/resolution)
                .setComponent(v,ChamberCellGrid.min(rectangle,v)+(j+.5)*ChamberCellGrid.size(rectangle,v)/resolution);
    }
    private ChamberFacePassage(){}
}
