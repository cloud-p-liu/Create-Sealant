package com.sealant.chamber.physics;

import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import java.util.ArrayList;
import java.util.List;

/** Fixed plot-space quadrature, bounded at 12^3 cells and 6*12^2 face panels per chamber.
 * Dimensions never change; world projection is temporary. Thin geometry is approximate.
 */
public final class ChamberSampling {
    public record Cell(Vector3d point,Vector3d size,double volume) {}
    public record Panel(Vector3d point,Vector3d outward,double area,Vector3d size) {}
    public final List<Cell> cells=new ArrayList<>();
    public final List<Panel> panels=new ArrayList<>();
    public ChamberSampling(AABB box) {
        int[] n={count(box.getXsize()),count(box.getYsize()),count(box.getZsize())};
        double[] min={box.minX,box.minY,box.minZ};
        double[] size={box.getXsize()/n[0],box.getYsize()/n[1],box.getZsize()/n[2]};
        for(int x=0;x<n[0];x++)for(int y=0;y<n[1];y++)for(int z=0;z<n[2];z++) {
            cells.add(new Cell(new Vector3d(min[0]+(x+.5)*size[0],min[1]+(y+.5)*size[1],min[2]+(z+.5)*size[2]),
                    new Vector3d(size[0],size[1],size[2]),size[0]*size[1]*size[2]));
        }
        for(int axis=0;axis<3;axis++)for(int side=0;side<2;side++) {
            int a=(axis+1)%3,b=(axis+2)%3;
            for(int i=0;i<n[a];i++)for(int j=0;j<n[b];j++) {
                double[] p=min.clone();p[axis]+=side*n[axis]*size[axis];p[a]+=(i+.5)*size[a];p[b]+=(j+.5)*size[b];
                Vector3d normal=new Vector3d();normal.setComponent(axis,side==0?-1:1);
                Vector3d faceSize=new Vector3d();faceSize.setComponent(a,size[a]);faceSize.setComponent(b,size[b]);
                panels.add(new Panel(new Vector3d(p),normal,size[a]*size[b],faceSize));
            }
        }
    }
    private static int count(double length){return Math.clamp((int)Math.ceil(length*2),1,12);}
}
