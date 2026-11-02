package com.sealant.chamber.topology;

import com.sealant.chamber.physics.ChamberSampling;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import java.util.*;

/** Exact axis-aligned box union in ONE owner's immutable plot frame.
 * Each retained piece has one deterministic member owner for persistence shares.
 * Only exposed face rectangles are sampled; overlap/cut faces never become pressure/leak surfaces.
 */
public final class ChamberUnion {
    public record Piece(UUID member,AABB box) {}
    public record Cell(UUID member,ChamberSampling.Cell sample) {}
    public final List<Piece> pieces;
    public final List<Cell> cells;
    public final List<ChamberSampling.Panel> panels;
    public final AABB bounds;
    public final double volume;
    public ChamberUnion(List<ChamberTopology.Node> nodes) {
        List<Piece> built=new ArrayList<>();AABB enclosing=nodes.getFirst().box();
        for(var node:nodes) {
            enclosing=enclosing.minmax(node.box());List<AABB> todo=new ArrayList<>(List.of(node.box()));
            for(Piece old:built) {
                List<AABB> next=new ArrayList<>();for(AABB box:todo)next.addAll(subtract(box,old.box));todo=next;
                if(todo.isEmpty())break;
            }
            for(AABB box:todo)built.add(new Piece(node.id(),box));
            if(built.size()>4096)throw new IllegalArgumentException("Chamber union exceeds 4096 disjoint pieces");
        }
        pieces=List.copyOf(built);bounds=enclosing;
        volume=pieces.stream().mapToDouble(p->volume(p.box)).sum();
        List<Cell> samples=new ArrayList<>();List<ChamberSampling.Panel> faces=new ArrayList<>();
        for(Piece piece:pieces) {
            var sampling=new ChamberSampling(piece.box);
            for(var cell:sampling.cells)samples.add(new Cell(piece.member,cell));
            if(samples.size()>65536)throw new IllegalArgumentException("Chamber union exceeds 65536 samples");
            for(int axis=0;axis<3;axis++)for(int side=0;side<2;side++) {
                int u=(axis+1)%3,v=(axis+2)%3;double plane=side==0?min(piece.box,axis):max(piece.box,axis);
                List<AABB> rectangles=new ArrayList<>(List.of(piece.box));
                for(Piece other:pieces)if(other!=piece && Math.abs((side==0?max(other.box,axis):min(other.box,axis))-plane)<1e-8) {
                    List<AABB> next=new ArrayList<>();
                    for(AABB rect:rectangles)next.addAll(subtractFace(rect,other.box,u,v));rectangles=next;
                }
                for(AABB rect:rectangles) {
                    double du=max(rect,u)-min(rect,u),dv=max(rect,v)-min(rect,v);
                    int nu=Math.clamp((int)Math.ceil(du*2),1,12),nv=Math.clamp((int)Math.ceil(dv*2),1,12);
                    for(int i=0;i<nu;i++)for(int j=0;j<nv;j++) {
                        Vector3d point=new Vector3d();point.setComponent(axis,plane);
                        point.setComponent(u,min(rect,u)+(i+.5)*du/nu);point.setComponent(v,min(rect,v)+(j+.5)*dv/nv);
                        Vector3d normal=new Vector3d();normal.setComponent(axis,side==0?-1:1);
                        Vector3d faceSize=new Vector3d();faceSize.setComponent(u,du/nu);faceSize.setComponent(v,dv/nv);
                        faces.add(new ChamberSampling.Panel(point,normal,du*dv/nu/nv,faceSize));
                        if(faces.size()>65536)throw new IllegalArgumentException("Chamber union exceeds 65536 panels");
                    }
                }
            }
        }
        if(samples.size()>65536 || faces.size()>65536)throw new IllegalArgumentException("Chamber union exceeds 65536 samples/panels");
        cells=List.copyOf(samples);panels=List.copyOf(faces);
    }
    public boolean contains(double x,double y,double z){for(var piece:pieces)if(piece.box.contains(x,y,z))return true;return false;}
    public static double volume(AABB box){return box.getXsize()*box.getYsize()*box.getZsize();}
    public static double intersectionVolume(AABB a,AABB b) {
        return Math.max(0,Math.min(a.maxX,b.maxX)-Math.max(a.minX,b.minX))*Math.max(0,Math.min(a.maxY,b.maxY)-Math.max(a.minY,b.minY))*Math.max(0,Math.min(a.maxZ,b.maxZ)-Math.max(a.minZ,b.minZ));
    }
    public static List<AABB> subtract(AABB a,AABB b) {
        if(!a.intersects(b))return List.of(a);
        AABB i=a.intersect(b);List<AABB> out=new ArrayList<>();
        add(out,new AABB(a.minX,a.minY,a.minZ,i.minX,a.maxY,a.maxZ));
        add(out,new AABB(i.maxX,a.minY,a.minZ,a.maxX,a.maxY,a.maxZ));
        add(out,new AABB(i.minX,a.minY,a.minZ,i.maxX,i.minY,a.maxZ));
        add(out,new AABB(i.minX,i.maxY,a.minZ,i.maxX,a.maxY,a.maxZ));
        add(out,new AABB(i.minX,i.minY,a.minZ,i.maxX,i.maxY,i.minZ));
        add(out,new AABB(i.minX,i.minY,i.maxZ,i.maxX,i.maxY,a.maxZ));
        return out;
    }
    private static void add(List<AABB> out,AABB box){if(box.getXsize()>1e-8 && box.getYsize()>1e-8 && box.getZsize()>1e-8)out.add(box);}
    private static List<AABB> subtractFace(AABB a,AABB b,int u,int v) {
        double u0=Math.max(min(a,u),min(b,u)),u1=Math.min(max(a,u),max(b,u));
        double v0=Math.max(min(a,v),min(b,v)),v1=Math.min(max(a,v),max(b,v));
        if(u1<=u0 || v1<=v0)return List.of(a);
        List<AABB> out=new ArrayList<>();
        face(out,a,u,min(a,u),u0,v,min(a,v),max(a,v));face(out,a,u,u1,max(a,u),v,min(a,v),max(a,v));
        face(out,a,u,u0,u1,v,min(a,v),v0);face(out,a,u,u0,u1,v,v1,max(a,v));return out;
    }
    private static void face(List<AABB> out,AABB original,int u,double u0,double u1,int v,double v0,double v1) {
        if(u1-u0<=1e-8 || v1-v0<=1e-8)return;
        double[] lo={original.minX,original.minY,original.minZ},hi={original.maxX,original.maxY,original.maxZ};
        lo[u]=u0;hi[u]=u1;lo[v]=v0;hi[v]=v1;out.add(new AABB(lo[0],lo[1],lo[2],hi[0],hi[1],hi[2]));
    }
    private static double min(AABB b,int axis){return axis==0?b.minX:axis==1?b.minY:b.minZ;}
    private static double max(AABB b,int axis){return axis==0?b.maxX:axis==1?b.maxY:b.maxZ;}
}
