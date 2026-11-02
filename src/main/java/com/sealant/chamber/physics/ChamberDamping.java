package com.sealant.chamber.physics;

import org.joml.Matrix3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import java.util.List;

/** Implicit distributed water drag. Solves (M + dt D) v_new = M v_old for 6 DOFs.
 * D is a sum of positive-semidefinite point drag matrices: damping cannot add kinetic energy,
 * reverse a single translational mode from too many panels, or lock the body's orientation.
 */
public final class ChamberDamping {
    public record Point(Vector3d relative,double coefficient) {}
    public record Impulse(Vector3d linear,Vector3d angular) {}
    public static Impulse solve(double mass,Matrix3dc inertia,Vector3dc linear,Vector3dc angular,List<Point> points,double dt) {
        if(mass<=1e-8 || dt<=0 || points.isEmpty())return new Impulse(new Vector3d(),new Vector3d());
        double[][] m=new double[6][6],a=new double[6][7];
        for(int i=0;i<3;i++)m[i][i]=mass;
        for(int row=0;row<3;row++)for(int col=0;col<3;col++)m[3+row][3+col]=inertia.get(col,row);
        double[] old={linear.x(),linear.y(),linear.z(),angular.x(),angular.y(),angular.z()};
        for(int row=0;row<6;row++)for(int col=0;col<6;col++){a[row][col]=m[row][col];a[row][6]+=m[row][col]*old[col];}
        for(Point point:points) {
            Vector3d r=point.relative;
            double vx=linear.x()+angular.y()*r.z-angular.z()*r.y;
            double vy=linear.y()+angular.z()*r.x-angular.x()*r.z;
            double vz=linear.z()+angular.x()*r.y-angular.y()*r.x;
            double speed=Math.sqrt(vx*vx+vy*vy+vz*vz);
            double c=dt*Math.max(0,point.coefficient)*(1+.25*speed);
            // J=[I,-cross(r)]. Analytic J^T J avoids per-panel matrix allocations.
            for(int i=0;i<3;i++)a[i][i]+=c;
            a[0][4]+=c*r.z;a[4][0]+=c*r.z;
            a[0][5]-=c*r.y;a[5][0]-=c*r.y;
            a[1][3]-=c*r.z;a[3][1]-=c*r.z;
            a[1][5]+=c*r.x;a[5][1]+=c*r.x;
            a[2][3]+=c*r.y;a[3][2]+=c*r.y;
            a[2][4]-=c*r.x;a[4][2]-=c*r.x;
            a[3][3]+=c*(r.y*r.y+r.z*r.z);
            a[4][4]+=c*(r.x*r.x+r.z*r.z);
            a[5][5]+=c*(r.x*r.x+r.y*r.y);
            a[3][4]-=c*r.x*r.y;a[4][3]-=c*r.x*r.y;
            a[3][5]-=c*r.x*r.z;a[5][3]-=c*r.x*r.z;
            a[4][5]-=c*r.y*r.z;a[5][4]-=c*r.y*r.z;
        }
        for(int pivot=0;pivot<6;pivot++) {
            int best=pivot;for(int row=pivot+1;row<6;row++)if(Math.abs(a[row][pivot])>Math.abs(a[best][pivot]))best=row;
            if(Math.abs(a[best][pivot])<1e-12)return new Impulse(new Vector3d(),new Vector3d());
            double[] swap=a[pivot];a[pivot]=a[best];a[best]=swap;
            double scale=a[pivot][pivot];for(int col=pivot;col<=6;col++)a[pivot][col]/=scale;
            for(int row=0;row<6;row++)if(row!=pivot) {
                double factor=a[row][pivot];for(int col=pivot;col<=6;col++)a[row][col]-=factor*a[pivot][col];
            }
        }
        double[] impulse=new double[6];
        for(int row=0;row<6;row++)for(int col=0;col<6;col++)impulse[row]+=m[row][col]*(a[col][6]-old[col]);
        return new Impulse(new Vector3d(impulse[0],impulse[1],impulse[2]),new Vector3d(impulse[3],impulse[4],impulse[5]));
    }
    private ChamberDamping() {}
}
