package com.sealant.chamber.physics;

import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import java.util.*;

/** Actual solid-surface quadrature, independent of the chamber grid alignment.
 * Two Gauss points per tangent integrate linear hydrostatic force and quadratic moments.
 * These are body-local samples; never upload them to Sable or mutate collision shapes.
 */
public final class ChamberBodyPressure {
    public record Sample(Vector3d point,Vector3d outward,double area) {}
    public static List<Sample> surface(AABB box){
        var samples=new ArrayList<Sample>(24);
        for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1}){
            int u=(axis+1)%3,v=(axis+2)%3;
            double area=ChamberCellGrid.size(box,u)*ChamberCellGrid.size(box,v)/4;
            if(area<=1e-14)continue;
            for(int i:new int[]{-1,1})for(int j:new int[]{-1,1}){
                var p=new Vector3d().setComponent(axis,sign<0?ChamberCellGrid.min(box,axis):ChamberCellGrid.max(box,axis));
                p.setComponent(u,(ChamberCellGrid.min(box,u)+ChamberCellGrid.max(box,u))/2+i*ChamberCellGrid.size(box,u)/(2*Math.sqrt(3)));
                p.setComponent(v,(ChamberCellGrid.min(box,v)+ChamberCellGrid.max(box,v))/2+j*ChamberCellGrid.size(box,v)/(2*Math.sqrt(3)));
                samples.add(new Sample(p,new Vector3d().setComponent(axis,sign),area));
            }
        }return samples;
    }
    public static Vector3d force(Vector3d outward,double area,double gauge){return new Vector3d(outward).mul(-area*gauge);}
    private ChamberBodyPressure(){}
}
