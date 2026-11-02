package com.sealant.chamber;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import java.util.*;

/** Render-only shared-vertex interface reconstruction. Simulation inventories are never averaged.
 * Six consistent tetrahedra per cell rasterize a continuous piecewise-linear phase interface.
 */
public final class ChamberWaterMesh {
    public record Cell(AABB box,double surface) {}
    public record Triangle(Vector3d a,Vector3d b,Vector3d c) {public List<Vector3d> points(){return List.of(a,b,c);}}
    private record Key(long x,long y,long z) {
        static Key of(Vector3d p){return new Key(Math.round(p.x*1e7),Math.round(p.y*1e7),Math.round(p.z*1e7));}
    }
    private static final class Vertex {final Vector3d world;double sum;int count;Vertex(Vector3d world){this.world=world;}}
    private static final int[][] TETS={{0,1,3,7},{0,3,2,7},{0,2,6,7},{0,6,4,7},{0,4,5,7},{0,5,1,7}};
    public static final class Surface {
        private final List<Vertex[]> cubes;private final List<Cell> cells;private final Pose3dc pose;
        private final com.sealant.chamber.runtime.ChamberGrid<Integer> spatial=new com.sealant.chamber.runtime.ChamberGrid<>();
        private List<Triangle> mesh;
        private Surface(List<Vertex[]> cubes,List<Cell> cells,Pose3dc pose){
            this.cubes=cubes;this.cells=List.copyOf(cells);this.pose=new dev.ryanhcode.sable.companion.math.Pose3d(pose);
            spatial.rebuild(java.util.stream.IntStream.range(0,cells.size()).boxed().toList(),i->cells.get(i).box);
        }
        public List<Triangle> mesh(){if(mesh==null)mesh=List.copyOf(triangles(cubes));return mesh;}
        /** Render-only tetrahedral sign interpolation, identical to the drawn interface. */
        public Optional<Boolean> wet(Vector3d world){
            var local=pose.transformPositionInverse(new Vector3d(world));
            for(int i=0;i<cells.size();i++){
                var b=cells.get(i).box;if(!b.contains(local.x,local.y,local.z))continue;
                var q=new Vector3d((local.x-b.minX)/b.getXsize(),(local.y-b.minY)/b.getYsize(),(local.z-b.minZ)/b.getZsize());
                for(var tet:TETS){
                    var a=corner(tet[0]);var e1=corner(tet[1]).sub(a);var e2=corner(tet[2]).sub(a);var e3=corner(tet[3]).sub(a);var offset=new Vector3d(q).sub(a);
                    double det=e1.dot(new Vector3d(e2).cross(e3));
                    double u=offset.dot(new Vector3d(e2).cross(e3))/det,v=offset.dot(new Vector3d(e3).cross(e1))/det,w=offset.dot(new Vector3d(e1).cross(e2))/det,t=1-u-v-w;
                    if(Math.min(Math.min(u,v),Math.min(w,t))< -1e-9)continue;
                    var cube=cubes.get(i);double phi=t*cube[tet[0]].sum/cube[tet[0]].count+u*cube[tet[1]].sum/cube[tet[1]].count+v*cube[tet[2]].sum/cube[tet[2]].count+w*cube[tet[3]].sum/cube[tet[3]].count;
                    return Optional.of(phi>1e-10);
                }
                return Optional.of(false);
            }return Optional.empty();
        }
        /** Exact polygon intersection with the rendered wet tetrahedra, for hull overlays.
         * Inputs/outputs are world points; no horizontal-waterline approximation or physics writes. */
        public List<List<Vector3d>> clipWet(List<Vector3d> polygon){
            if(polygon.size()<3)return List.of();
            var local=polygon.stream().map(p->pose.transformPositionInverse(new Vector3d(p))).toList();
            double[] lo={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
            double[] hi={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
            for(var p:local)for(int a=0;a<3;a++){lo[a]=Math.min(lo[a],p.get(a));hi[a]=Math.max(hi[a],p.get(a));}
            var bounds=new AABB(lo[0],lo[1],lo[2],hi[0],hi[1],hi[2]).inflate(1e-8);
            List<List<Vector3d>> result=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(int index:spatial.query(bounds)){
                var b=cells.get(index).box;var cube=cubes.get(index);
                if(!b.intersects(bounds))continue;
                var normalized=local.stream().map(p->new Vector3d((p.x-b.minX)/b.getXsize(),(p.y-b.minY)/b.getYsize(),(p.z-b.minZ)/b.getZsize())).toList();
                for(var tet:TETS){
                    var a=corner(tet[0]);var e1=corner(tet[1]).sub(a);var e2=corner(tet[2]).sub(a);var e3=corner(tet[3]).sub(a);
                    double det=e1.dot(new Vector3d(e2).cross(e3));
                    Vector3d[] gradients={new Vector3d(),new Vector3d(e2).cross(e3).div(det),new Vector3d(e3).cross(e1).div(det),new Vector3d(e1).cross(e2).div(det)};
                    gradients[0].sub(gradients[1]).sub(gradients[2]).sub(gradients[3]);
                    double[] offsets={1-gradients[0].dot(a),-gradients[1].dot(a),-gradients[2].dot(a),-gradients[3].dot(a)};
                    List<Vector3d> cut=normalized;
                    var wetGradient=new Vector3d();double wetOffset=0;
                    for(int k=0;k<4;k++){
                        cut=clipPlane(cut,gradients[k],offsets[k]);
                        double phi=cube[tet[k]].sum/cube[tet[k]].count;
                        wetGradient.fma(phi,gradients[k]);wetOffset+=phi*offsets[k];
                    }
                    // Match wet(): a zero-valued interface alone is dry.
                    cut=clipPlane(cut,wetGradient,wetOffset-1e-10);
                    if(cut.size()<3)continue;
                    var world=cut.stream().map(p->pose.transformPosition(new Vector3d(b.minX+p.x*b.getXsize(),b.minY+p.y*b.getYsize(),b.minZ+p.z*b.getZsize()))).toList();
                    if(area(world)<1e-10)continue;
                    // A polygon on a shared tet/cell face belongs to both closed half spaces.
                    // Submit identical pieces once, avoiding doubled alpha at those boundaries.
                    String key=world.stream().map(Key::of).distinct().map(Object::toString).sorted().collect(java.util.stream.Collectors.joining(";"));
                    if(seen.add(key))result.add(world);
                }
            }
            return result;
        }
        private static List<Vector3d> clipPlane(List<Vector3d> input,Vector3d normal,double offset){
            List<Vector3d> out=new ArrayList<>();
            for(int i=0;i<input.size();i++){
                var a=input.get(i);var b=input.get((i+1)%input.size());double da=normal.dot(a)+offset,db=normal.dot(b)+offset;
                boolean ia=da>=0,ib=db>=0;if(ia)out.add(a);
                if(ia!=ib)out.add(new Vector3d(a).lerp(b,Math.clamp(da/(da-db),0,1)));
            }return out;
        }
        private static Vector3d corner(int i){return new Vector3d((i&1)==0?0:1,(i&2)==0?0:1,(i&4)==0?0:1);}
    }
    public static List<Triangle> build(List<Cell> cells,Pose3dc pose){return prepare(cells,pose).mesh();}
    public static Surface prepare(List<Cell> cells,Pose3dc pose){
        Map<Key,Vertex> vertices=new HashMap<>();List<Vertex[]> cubes=new ArrayList<>(cells.size());
        for(var cell:cells){Vertex[] cube=new Vertex[8];var b=cell.box;
            for(int i=0;i<8;i++){
                var local=new Vector3d((i&1)==0?b.minX:b.maxX,(i&2)==0?b.minY:b.maxY,(i&4)==0?b.minZ:b.maxZ);
                var key=Key.of(local);var vertex=vertices.get(key);
                if(vertex==null){vertex=new Vertex(pose.transformPosition(local));vertices.put(key,vertex);}
                vertex.sum+=cell.surface-vertex.world.y;vertex.count++;cube[i]=vertex;
            }cubes.add(cube);
        }
        return new Surface(cubes,cells,pose);
    }
    private static List<Triangle> triangles(List<Vertex[]> cubes){
        List<Triangle> mesh=new ArrayList<>();
        for(var cube:cubes)for(var tet:TETS){
            List<Vector3d> polygon=new ArrayList<>(4);boolean wet=false,dry=false;
            for(int k:tet){if(cube[k].sum/cube[k].count>1e-10)wet=true;else dry=true;}
            if(!wet||!dry)continue;
            for(int i=0;i<4;i++)for(int j=i+1;j<4;j++){
                var a=cube[tet[i]];var b=cube[tet[j]];double fa=a.sum/a.count,fb=b.sum/b.count;
                if((fa>1e-10)==(fb>1e-10))continue;
                var point=new Vector3d(a.world).lerp(b.world,Math.clamp(fa/(fa-fb),0,1));
                if(polygon.stream().noneMatch(p->p.distanceSquared(point)<1e-16))polygon.add(point);
            }
            if(polygon.size()<3)continue;
            var center=new Vector3d();for(var p:polygon)center.add(p);center.div(polygon.size());
            var normal=new Vector3d(polygon.get(1)).sub(polygon.getFirst()).cross(new Vector3d(polygon.get(2)).sub(polygon.getFirst()));
            if(normal.lengthSquared()<1e-20)continue;normal.normalize();
            var u=new Vector3d(polygon.getFirst()).sub(center).normalize();var v=new Vector3d(normal).cross(u);
            polygon.sort(Comparator.comparingDouble(p->Math.atan2(new Vector3d(p).sub(center).dot(v),new Vector3d(p).sub(center).dot(u))));
            for(int i=1;i+1<polygon.size();i++)if(area(List.of(polygon.getFirst(),polygon.get(i),polygon.get(i+1)))>1e-10)
                mesh.add(new Triangle(new Vector3d(polygon.getFirst()),new Vector3d(polygon.get(i)),new Vector3d(polygon.get(i+1))));
        }
        return mesh;
    }
    public static double area(List<Vector3d> polygon){
        if(polygon.size()<3)return 0;double area=0;var origin=polygon.getFirst();
        for(int i=1;i+1<polygon.size();i++)area+=new Vector3d(polygon.get(i)).sub(origin).cross(new Vector3d(polygon.get(i+1)).sub(origin)).length()/2;
        return area;
    }
    /** Exact convex polygon minus transformed collision box, including tilted wave triangles. */
    public static List<List<Vector3d>> subtractBox(List<Vector3d> polygon,AABB box,Pose3dc pose){
        List<Vector3d> remaining=polygon.stream().map(p->pose.transformPositionInverse(new Vector3d(p))).toList();
        List<List<Vector3d>> output=new ArrayList<>();
        for(int axis=0;axis<3 && remaining.size()>=3;axis++)for(int side=0;side<2 && remaining.size()>=3;side++){
            double boundary=axis==0?(side==0?box.minX:box.maxX):axis==1?(side==0?box.minY:box.maxY):(side==0?box.minZ:box.maxZ);
            var outside=clip(remaining,axis,boundary,side,false);
            if(area(outside)>1e-10)output.add(outside.stream().map(p->pose.transformPosition(new Vector3d(p))).toList());
            remaining=clip(remaining,axis,boundary,side,true);
        }return output;
    }
    private static List<Vector3d> clip(List<Vector3d> polygon,int axis,double boundary,int side,boolean inside){
        List<Vector3d> result=new ArrayList<>();
        for(int i=0;i<polygon.size();i++){
            var p=polygon.get(i);var q=polygon.get((i+1)%polygon.size());double sign=side==0?1:-1;
            double d=(p.get(axis)-boundary)*sign,e=(q.get(axis)-boundary)*sign;
            boolean pin=inside?d>=-1e-9:d< -1e-9,qin=inside?e>=-1e-9:e< -1e-9;
            if(pin)result.add(new Vector3d(p));
            if(pin!=qin && Math.abs(d-e)>1e-15)result.add(new Vector3d(p).lerp(q,Math.clamp(d/(d-e),0,1)));
        }return result;
    }
    private ChamberWaterMesh(){}
}
