package com.sealant.chamber.client;

import com.sealant.chamber.*;
import dev.ryanhcode.sable.companion.math.Pose3d;
import io.github.ocelot.glslprocessor.api.GlslParser;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** GPU compilation still needs a game. These checks validate the actual GLSL parser,
 * inverse projection and float plane encoding across rotations/distant plot coordinates. */
public final class WaterMaskShaderChecks {
    public static void run(){
        try {
            var tree=GlslParser.parse("#version 150\nin vec2 texCoord0;\nout vec4 fragColor;\nvoid main(){fragColor=vec4(1.0);}");
            ChamberWaterMaskShader.inject(tree);GlslParser.parse(tree.toSourceString());
            check(tree.field("SealantDryPlanes").isPresent(),"Clip plane uniforms must survive shader processing");
        }catch(Exception error){throw new AssertionError("Water-mask GLSL preprocessing failed",error);}
        var box=new AABB(2048000,62,2048000,2048004,65,2048006);
        for(int i=0;i<25;i++){
            var pose=new Pose3d();pose.orientation().rotateXYZ(.09*i,.17*i,-.11*i);
            var c=box.getCenter();var center=pose.transformPosition(new Vector3d(c.x,c.y,c.z));
            var camera=new Vector3d(center).add(7,3,-5);float[] planes=new float[12],half=new float[4];
            ChamberMaskGeometry.encode(box,pose,camera,planes,half,0);
            for(var local:new Vector3d[]{new Vector3d(c.x,c.y,c.z),new Vector3d(box.maxX+.02,c.y,c.z)}){
                var world=pose.transformPosition(new Vector3d(local));var relative=new Vector3d(world).sub(camera);boolean inside=true;
                for(int axis=0;axis<3;axis++){
                    double q=planes[axis*4]*relative.x+planes[axis*4+1]*relative.y+planes[axis*4+2]*relative.z+planes[axis*4+3];
                    inside&=Math.abs(q)<half[axis];
                }
                check(inside==box.contains(local.x,local.y,local.z),"Float shader planes must agree with exact rotated OBB");
            }
            var vp=new Matrix4f().perspective((float)Math.toRadians(75),1.6f,.05f,256).rotateX(.3f).rotateY(.2f);
            var relative=new Vector3f(1,.5f,-6);var clip=new Vector4f(relative,1).mul(vp);var ndc=new Vector4f(clip).div(clip.w);
            var restored=new Vector4f(ndc.x,ndc.y,ndc.z,1).mul(new Matrix4f(vp).invert());restored.div(restored.w);
            check(new Vector3f(restored.x,restored.y,restored.z).distance(relative)<.0002,"Fragment depth reconstruction uses the matching global view/projection");
        }
        System.out.println("Water-only clip GLSL parser, inverse projection and rotated camera-relative OBB checks passed.");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
