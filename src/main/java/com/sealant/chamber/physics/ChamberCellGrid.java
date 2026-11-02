package com.sealant.chamber.physics;

import com.sealant.chamber.topology.ChamberUnion;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** One-block local finite volumes. Faces are explicit, so a shared chamber marker never
 * implies a fluid path. Partial boxes at union cuts retain their true volume and face area. */
public final class ChamberCellGrid {
    public static final int MAX_CELLS=8192,MAX_FACES=65536;
    public record Cell(UUID member,AABB box) {}
    public record Face(int a,int b,int axis,double plane,AABB rectangle) {
        public double area(){int u=(axis+1)%3,v=(axis+2)%3;return size(rectangle,u)*size(rectangle,v);}
    }
    public final List<Cell> cells;
    public final List<Face> faces;
    private record Key(int axis,long plane,int u,int v) {}
    private record Side(int cell,int axis,int sign,double plane,AABB box) {}
    public ChamberCellGrid(List<ChamberUnion.Piece> pieces){
        List<Cell> cells=new ArrayList<>();
        for(var piece:pieces){var b=piece.box();
            for(double x=b.minX;x<b.maxX-1e-9;){double X=Math.min(b.maxX,Math.floor(x+1e-8)+1);
                for(double y=b.minY;y<b.maxY-1e-9;){double Y=Math.min(b.maxY,Math.floor(y+1e-8)+1);
                    for(double z=b.minZ;z<b.maxZ-1e-9;){double Z=Math.min(b.maxZ,Math.floor(z+1e-8)+1);
                        cells.add(new Cell(piece.member(),new AABB(x,y,z,X,Y,Z)));if(cells.size()>MAX_CELLS)throw new IllegalArgumentException("Grid exceeds "+MAX_CELLS+" cells");z=Z;
                    }y=Y;
                }x=X;
            }
        }
        this.cells=List.copyOf(cells);
        Map<Key,List<Side>> planes=new LinkedHashMap<>();
        for(int i=0;i<cells.size();i++)for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1}){
            var b=cells.get(i).box();double h=sign<0?min(b,axis):max(b,axis);
            planes.computeIfAbsent(new Key(axis,Math.round(h*1e7),(int)Math.floor(min(b,(axis+1)%3)+1e-8),(int)Math.floor(min(b,(axis+2)%3)+1e-8)),k->new ArrayList<>()).add(new Side(i,axis,sign,h,b));
        }
        List<Face> faces=new ArrayList<>();
        for(var sides:planes.values())for(var a:sides){
            List<AABB> remaining=new ArrayList<>(List.of(a.box));
            for(var b:sides)if(b.sign!=a.sign){
                int u=(a.axis+1)%3,v=(a.axis+2)%3;
                double U=Math.min(max(a.box,u),max(b.box,u)),V=Math.min(max(a.box,v),max(b.box,v));
                double l=Math.max(min(a.box,u),min(b.box,u)),m=Math.max(min(a.box,v),min(b.box,v));
                if(U-l<=1e-9 || V-m<=1e-9)continue;
                AABB rect=rectangle(a.axis,a.plane,l,U,m,V);
                if(a.sign>0)faces.add(new Face(a.cell,b.cell,a.axis,a.plane,rect));
                List<AABB> next=new ArrayList<>();for(var r:remaining)next.addAll(subtractFace(r,rect,u,v));remaining=next;
            }
            for(var r:remaining)faces.add(new Face(a.sign>0?a.cell:-1,a.sign>0?-1:a.cell,a.axis,a.plane,rectangle(a.axis,a.plane,min(r,(a.axis+1)%3),max(r,(a.axis+1)%3),min(r,(a.axis+2)%3),max(r,(a.axis+2)%3))));
            if(faces.size()>MAX_FACES)throw new IllegalArgumentException("Grid exceeds face budget");
        }
        this.faces=List.copyOf(faces);
    }
    private static List<AABB> subtractFace(AABB a,AABB b,int u,int v){
        double l=Math.max(min(a,u),min(b,u)),r=Math.min(max(a,u),max(b,u)),d=Math.max(min(a,v),min(b,v)),t=Math.min(max(a,v),max(b,v));
        if(r<=l || t<=d)return List.of(a);
        List<AABB> result=new ArrayList<>();
        if(min(a,u)<l)result.add(cut(a,u,min(a,u),l));if(r<max(a,u))result.add(cut(a,u,r,max(a,u)));
        AABB middle=cut(a,u,l,r);
        if(min(a,v)<d)result.add(cut(middle,v,min(a,v),d));if(t<max(a,v))result.add(cut(middle,v,t,max(a,v)));
        return result;
    }
    private static AABB cut(AABB b,int axis,double l,double h){double[] lo={b.minX,b.minY,b.minZ},hi={b.maxX,b.maxY,b.maxZ};lo[axis]=l;hi[axis]=h;return new AABB(lo[0],lo[1],lo[2],hi[0],hi[1],hi[2]);}
    public static AABB rectangle(int axis,double h,double l,double r,double d,double t){double[] lo=new double[3],hi=new double[3];lo[axis]=hi[axis]=h;lo[(axis+1)%3]=l;hi[(axis+1)%3]=r;lo[(axis+2)%3]=d;hi[(axis+2)%3]=t;return new AABB(lo[0],lo[1],lo[2],hi[0],hi[1],hi[2]);}
    public static double min(AABB b,int axis){return axis==0?b.minX:axis==1?b.minY:b.minZ;}
    public static double max(AABB b,int axis){return axis==0?b.maxX:axis==1?b.maxY:b.maxZ;}
    public static double size(AABB b,int axis){return max(b,axis)-min(b,axis);}
    private ChamberCellGrid(){throw new AssertionError();}
}
