package com.sealant.chamber.physics;

import com.sealant.chamber.topology.ChamberUnion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Conservative spatial remap. Blocked contents stay parked at their old location, never jump a wall. */
public final class ChamberGridInventory {
    public record Target(int cell,AABB box) {}
    public record Result(double[] water,double[] gas,List<ChamberGridState.Cell> parked) {}
    public static Result assign(List<ChamberGridState.Cell> source,List<Target> target,int cells,List<AABB> domain){
        double[] water=new double[cells],gas=new double[cells];List<ChamberGridState.Cell> parked=new ArrayList<>();
        Map<Long,List<Integer>> index=new HashMap<>();
        for(int i=0;i<target.size();i++){var b=target.get(i).box;for(int x=(int)Math.floor(b.minX);x<Math.ceil(b.maxX);x++)for(int y=(int)Math.floor(b.minY);y<Math.ceil(b.maxY);y++)for(int z=(int)Math.floor(b.minZ);z<Math.ceil(b.maxZ);z++)index.computeIfAbsent(BlockPos.asLong(x,y,z),k->new ArrayList<>()).add(i);}
        for(var old:source){
            double volume=ChamberUnion.volume(old.box());List<AABB> remaining=new ArrayList<>(List.of(old.box()));Set<Integer> candidates=new TreeSet<>();
            var b=old.box();
            for(int x=(int)Math.floor(b.minX);x<Math.ceil(b.maxX);x++)for(int y=(int)Math.floor(b.minY);y<Math.ceil(b.maxY);y++)for(int z=(int)Math.floor(b.minZ);z<Math.ceil(b.maxZ);z++)candidates.addAll(index.getOrDefault(BlockPos.asLong(x,y,z),List.of()));
            for(int id:candidates){var t=target.get(id);double fraction=ChamberUnion.intersectionVolume(b,t.box)/volume;if(fraction<=0)continue;
                double w=old.water()*fraction,g=old.gasPV()*fraction;water[t.cell]+=w;gas[t.cell]+=g;
                List<AABB> next=new ArrayList<>();for(var part:remaining)next.addAll(ChamberUnion.subtract(part,t.box));remaining=next;
            }
            for(var part:remaining)for(var d:domain){
                double overlap=ChamberUnion.intersectionVolume(part,d);if(overlap<=0)continue;
                AABB rest=part.intersect(d);double fraction=overlap/volume;
                if(old.water()>0 || old.gasPV()>0)parked.add(new ChamberGridState.Cell(rest,old.water()*fraction,old.gasPV()*fraction));
                if(parked.size()>65536)throw new IllegalArgumentException("Parked grid inventory budget");
            }
        }
        return new Result(water,gas,List.copyOf(parked));
    }
    private ChamberGridInventory(){}
}
