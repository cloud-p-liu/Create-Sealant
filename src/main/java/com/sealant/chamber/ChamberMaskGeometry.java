package com.sealant.chamber;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Camera-relative OBB plane encoding shared with the fragment shader. No far-plot floats. */
public final class ChamberMaskGeometry {
    public static void encode(AABB box,Pose3dc pose,Vector3dc camera,float[] planes,float[] half,int index){
        var c=box.getCenter();var center=pose.transformPosition(new Vector3d(c.x,c.y,c.z));
        var relative=new Vector3d(camera).sub(center);
        for(int axis=0;axis<3;axis++){
            var direction=pose.orientation().transform(new Vector3d().setComponent(axis,1));
            int start=index*12+axis*4;
            planes[start]=(float)direction.x;planes[start+1]=(float)direction.y;planes[start+2]=(float)direction.z;
            planes[start+3]=(float)relative.dot(direction);
            half[index*4+axis]=(float)(size(box,axis)*pose.scale().get(axis)/2);
        }
    }
    private static double size(AABB b,int axis){return axis==0?b.getXsize():axis==1?b.getYsize():b.getZsize();}
    private ChamberMaskGeometry(){}
}
