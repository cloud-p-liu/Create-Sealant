package com.sealant.chamber.physics;

import org.joml.Matrix3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import java.util.*;

/** Backward-Euler coupling to LOCAL EOS compliance, not a force/velocity cap.
 * For fixed inventories and affine cell volumes the convex EOS potential plus kinetic
 * energy cannot increase. Each cell keeps its own W/G/C; no pressure averaging. */
public final class ChamberPressureCoupling {
    public record Cell(double capacity,double water,double gas,double[] gradient) {}
    public record Impulse(Vector3d linear,Vector3d angular) {}
    private static double[] solve(double[][] matrix,double[] rhs){
        int n=rhs.length;double[][] a=new double[n][n+1];
        for(int i=0;i<n;i++){System.arraycopy(matrix[i],0,a[i],0,n);a[i][n]=rhs[i];}
        for(int p=0;p<n;p++){
            int best=p;for(int i=p+1;i<n;i++)if(Math.abs(a[i][p])>Math.abs(a[best][p]))best=i;
            if(Math.abs(a[best][p])<1e-14)throw new IllegalArgumentException("Pressure coupling singular inertia");
            var row=a[p];a[p]=a[best];a[best]=row;
            double d=a[p][p];for(int j=p;j<=n;j++)a[p][j]/=d;
            for(int i=0;i<n;i++)if(i!=p){double k=a[i][p];for(int j=p;j<=n;j++)a[i][j]-=k*a[p][j];}
        }
        double[] out=new double[n];for(int i=0;i<n;i++)out[i]=a[i][n];return out;
    }
    private static double residual(List<Cell> cells,double[][] mass,double[] old,double[] velocity,double dt,double[] result,double[][] jacobian){
        Arrays.fill(result,0);
        for(int i=0;i<6;i++)for(int j=0;j<6;j++){
            result[i]+=mass[i][j]*(velocity[j]-old[j]);if(jacobian!=null)jacobian[i][j]=mass[i][j];
        }
        for(var cell:cells){
            double capacity=cell.capacity;for(int i=0;i<6;i++)capacity+=dt*cell.gradient[i]*velocity[i];
            if(capacity<=0 || !Double.isFinite(capacity))return Double.POSITIVE_INFINITY;
            double pressure=ChamberField.pressure(capacity,cell.water,cell.gas);
            double compressibility=cell.water*ChamberField.BULK/Math.pow(ChamberField.BULK+pressure-ChamberFluid.AMBIENT,2)+cell.gas/(pressure*pressure);
            double stiffness=compressibility>0?1/compressibility:0;
            for(int i=0;i<6;i++){
                result[i]-=dt*(pressure-ChamberFluid.AMBIENT)*cell.gradient[i];
                if(jacobian!=null)for(int j=0;j<6;j++)jacobian[i][j]+=dt*dt*stiffness*cell.gradient[i]*cell.gradient[j];
            }
        }
        double norm=0;for(double x:result)norm+=x*x;return norm;
    }
    public static Impulse solve(double mass,Matrix3dc inertia,Vector3dc linear,Vector3dc angular,List<Cell> cells,double dt){
        if(cells.isEmpty() || dt<=0 || mass<=1e-8)return new Impulse(new Vector3d(),new Vector3d());
        double[][] m=new double[6][6];for(int i=0;i<3;i++)m[i][i]=mass;
        for(int i=0;i<3;i++)for(int j=0;j<3;j++)m[i+3][j+3]=inertia.get(j,i);
        double[] old={linear.x(),linear.y(),linear.z(),angular.x(),angular.y(),angular.z()};
        // Zero relative velocity is a feasible starting iterate even for a very fast piston.
        double[] velocity=new double[6],r=new double[6];double[][] jac=new double[6][6];
        double initial=residual(cells,m,old,velocity,dt,r,jac);
        if(!Double.isFinite(initial))throw new IllegalArgumentException("Invalid pressure coupling capacity");
        boolean converged=false;
        for(int iteration=0;iteration<64;iteration++){
            double norm=residual(cells,m,old,velocity,dt,r,jac);
            if(norm<=1e-18*Math.max(1,initial)){converged=true;break;}
            double[] rhs=new double[6];for(int i=0;i<6;i++)rhs[i]=-r[i];var direction=solve(jac,rhs);
            boolean accepted=false;
            for(double scale=1;scale>=Math.scalb(1.0,-40);scale*=.5){
                double[] trial=velocity.clone();for(int i=0;i<6;i++)trial[i]+=scale*direction[i];
                if(residual(cells,m,old,trial,dt,new double[6],null)<norm){velocity=trial;accepted=true;break;}
            }
            if(!accepted)break;
        }
        if(!converged && residual(cells,m,old,velocity,dt,r,null)>1e-14*Math.max(1,initial))throw new IllegalArgumentException("Pressure coupling did not converge");
        double[] impulse=new double[6];for(int i=0;i<6;i++)for(int j=0;j<6;j++)impulse[i]+=m[i][j]*(velocity[j]-old[j]);
        return new Impulse(new Vector3d(impulse[0],impulse[1],impulse[2]),new Vector3d(impulse[3],impulse[4],impulse[5]));
    }
    public static double[] gradient(Vector3dc normal,double area,Vector3dc relative){
        var linear=new Vector3d(normal).mul(-area);var angular=new Vector3d(relative).cross(linear);
        return new double[]{linear.x,linear.y,linear.z,angular.x,angular.y,angular.z};
    }
    private ChamberPressureCoupling(){}
}
