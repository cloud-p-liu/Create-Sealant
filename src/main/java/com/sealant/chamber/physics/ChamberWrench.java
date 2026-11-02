package com.sealant.chamber.physics;

import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Pressure loads accumulated about the body's COM in world space. Mutates only owned vectors. */
public final class ChamberWrench {
    public final Vector3d force=new Vector3d(),torque=new Vector3d();
    public static Vector3d pressureForce(Vector3dc outward,double area,double outsideGauge,double insideGauge){
        return new Vector3d(outward).mul((insideGauge-outsideGauge)*area);
    }
    public void add(Vector3dc worldPoint,Vector3dc worldForce,Vector3dc worldCom) {
        force.add(worldForce);
        torque.add(new Vector3d(worldPoint).sub(worldCom).cross(worldForce));
    }
    public void toLocalImpulse(Quaterniondc orientation,double dt) {
        orientation.transformInverse(force).mul(dt);
        orientation.transformInverse(torque).mul(dt);
    }
}
