package com.sealant.chamber;

import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import java.util.*;

/** Render-only polygon operations. Split at texture tile boundaries before atlas mapping;
 * interpolating fract(UV) across a wrap would sample unrelated sprites or smear the water. */
public final class ChamberWaterTiles {
    public record Tile(List<Vector3d> polygon,int u,int v,int originU,int originV) {}
    public record Face(AABB rectangle,int axis,int sign,double thickness) {}
    /** Actual solid/water contact face. The normal and probe point toward adjacent water. */
    public record Contact(List<Vector3d> polygon,Vector3d normal,Vector3d probe) {
        public boolean visibleFrom(Vector3d camera){return new Vector3d(camera).sub(probe).dot(normal)<0;}
    }
    public static Contact contact(Face face,List<Vector3d> part){
        var center=new Vector3d();for(var p:part)center.add(p);center.div(part.size());
        var normal=new Vector3d().setComponent(face.axis,face.sign);
        return new Contact(part.stream().map(p->new Vector3d(p).fma(.002,normal)).toList(),normal,new Vector3d(center).fma(.004,normal));
    }
    public static List<Tile> tiles(List<Vector3d> polygon){
        if(polygon.size()<3)return List.of();
        var n=new Vector3d();
        for(int i=1;i+1<polygon.size() && n.lengthSquared()<1e-20;i++)
            n.set(polygon.get(i)).sub(polygon.getFirst()).cross(new Vector3d(polygon.get(i+1)).sub(polygon.getFirst()));
        if(n.lengthSquared()<1e-20)return List.of();
        int normal=Math.abs(n.y)>=Math.abs(n.x) && Math.abs(n.y)>=Math.abs(n.z)?1:Math.abs(n.x)>=Math.abs(n.z)?0:2;
        int u=normal==1?0:(normal+1)%3,v=normal==1?2:(normal+2)%3;
        double minU=Double.POSITIVE_INFINITY,minV=minU,maxU=Double.NEGATIVE_INFINITY,maxV=maxU;
        for(var p:polygon){minU=Math.min(minU,p.get(u));minV=Math.min(minV,p.get(v));maxU=Math.max(maxU,p.get(u));maxV=Math.max(maxV,p.get(v));}
        int a=(int)Math.floor(minU),b=(int)Math.floor(minV),A=(int)Math.floor(maxU-1e-9),B=(int)Math.floor(maxV-1e-9);
        if((long)(A-a+1)*(B-b+1)>256)return List.of();
        List<Tile> result=new ArrayList<>();
        for(int x=a;x<=A;x++)for(int y=b;y<=B;y++){
            var cut=clip(clip(clip(clip(polygon,u,x,true),u,x+1,false),v,y,true),v,y+1,false);
            if(cut.size()>=3 && ChamberWaterMesh.area(cut)>1e-10)result.add(new Tile(cut,u,v,x,y));
        }return result;
    }
    public static List<Vector3d> clip(List<Vector3d> input,int axis,double plane,boolean greater){
        if(input.isEmpty())return List.of();List<Vector3d> out=new ArrayList<>();
        for(int i=0;i<input.size();i++){
            var a=input.get(i);var b=input.get((i+1)%input.size());
            double da=(a.get(axis)-plane)*(greater?1:-1),db=(b.get(axis)-plane)*(greater?1:-1);
            boolean ia=da>=-1e-10,ib=db>=-1e-10;if(ia)out.add(a);
            if(ia!=ib){var p=new Vector3d(a).lerp(b,Math.clamp(da/(da-db),0,1));p.setComponent(axis,plane);out.add(p);}
        }return out;
    }
    public static List<Vector3d> quad(AABB rectangle,int axis,int sign){
        int u=(axis+1)%3,v=(axis+2)%3;List<Vector3d> result=new ArrayList<>();
        int[] order=sign>0?new int[]{0,1,3,2}:new int[]{0,2,3,1};
        for(int corner:order)result.add(new Vector3d().setComponent(axis,min(rectangle,axis))
                .setComponent(u,(corner&1)==0?min(rectangle,u):max(rectangle,u)).setComponent(v,(corner&2)==0?min(rectangle,v):max(rectangle,v)));
        return result;
    }
    /** Remove actual neighboring solid coverage from a face, including partial panes. */
    public static List<Face> exposed(AABB shape,List<AABB> neighbors){
        List<Face> result=new ArrayList<>();
        for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1}){
            double plane=sign<0?min(shape,axis):max(shape,axis),probe=plane+sign*1e-5;
            double[] lo={shape.minX,shape.minY,shape.minZ},hi={shape.maxX,shape.maxY,shape.maxZ};lo[axis]=hi[axis]=plane;
            List<AABB> faces=new ArrayList<>(List.of(new AABB(lo[0],lo[1],lo[2],hi[0],hi[1],hi[2])));
            int u=(axis+1)%3,v=(axis+2)%3;
            for(var other:neighbors)if(probe>min(other,axis) && probe<max(other,axis)){
                List<AABB> next=new ArrayList<>();for(var face:faces)next.addAll(subtract(face,other,u,v));faces=next;
            }
            for(var face:faces)result.add(new Face(face,axis,sign,max(shape,axis)-min(shape,axis)));
        }return result;
    }
    private static List<AABB> subtract(AABB a,AABB b,int u,int v){
        double l=Math.max(min(a,u),min(b,u)),r=Math.min(max(a,u),max(b,u)),d=Math.max(min(a,v),min(b,v)),t=Math.min(max(a,v),max(b,v));
        if(r<=l || t<=d)return List.of(a);
        List<AABB> result=new ArrayList<>();
        if(min(a,u)<l)result.add(cut(a,u,min(a,u),l));if(r<max(a,u))result.add(cut(a,u,r,max(a,u)));
        var middle=cut(a,u,l,r);if(min(a,v)<d)result.add(cut(middle,v,min(a,v),d));if(t<max(a,v))result.add(cut(middle,v,t,max(a,v)));return result;
    }
    private static AABB cut(AABB b,int axis,double low,double high){double[] lo={b.minX,b.minY,b.minZ},hi={b.maxX,b.maxY,b.maxZ};lo[axis]=low;hi[axis]=high;return new AABB(lo[0],lo[1],lo[2],hi[0],hi[1],hi[2]);}
    public static double min(AABB b,int axis){return axis==0?b.minX:axis==1?b.minY:b.minZ;}
    public static double max(AABB b,int axis){return axis==0?b.maxX:axis==1?b.maxY:b.maxZ;}
    private ChamberWaterTiles(){}
}
