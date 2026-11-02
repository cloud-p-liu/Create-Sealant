package com.sealant.chamber.topology;

import net.minecraft.world.phys.AABB;
import java.util.*;

/** Conservative remapping between connected components. Liquid and gas use separate spatial weights. */
public final class ChamberInventory {
    public record Parcel(AABB box,double liquid,double gas) {}
    public record Snapshot(UUID owner,Set<UUID> members,List<ChamberUnion.Piece> domain,List<Parcel> parcels,double water,double gasPV) {}
    public record Amount(double water,double gasPV) {}
    public static List<Amount> remap(List<Snapshot> old,List<ChamberTopology.Group> groups) {
        double[] water=new double[groups.size()],gas=new double[groups.size()];
        for(Snapshot source:old) {
            double[] liquidWeight=new double[groups.size()],gasWeight=new double[groups.size()],fallback=new double[groups.size()];
            for(int i=0;i<groups.size();i++) {
                var group=groups.get(i);
                if(!group.owner().equals(source.owner))continue;
                for(var piece:source.domain)for(var target:group.union().pieces)fallback[i]+=ChamberUnion.intersectionVolume(piece.box(),target.box());
                for(Parcel parcel:source.parcels)for(var target:group.union().pieces) {
                    double ratio=ChamberUnion.intersectionVolume(parcel.box,target.box())/ChamberUnion.volume(parcel.box);
                    liquidWeight[i]+=parcel.liquid*ratio;gasWeight[i]+=parcel.gas*ratio;
                }
            }
            if(sum(fallback)==0)for(int i=0;i<groups.size();i++)
                fallback[i]=groups.get(i).members().stream().filter(n->source.members.contains(n.id())).count();
            distribute(source.water,sum(liquidWeight)>1e-12?liquidWeight:fallback,water);
            distribute(source.gasPV,sum(gasWeight)>1e-12?gasWeight:fallback,gas);
        }
        List<Amount> result=new ArrayList<>();for(int i=0;i<water.length;i++)result.add(new Amount(water[i],gas[i]));return result;
    }
    public static double uncovered(AABB box,List<ChamberUnion.Piece> covered) {
        List<AABB> pieces=new ArrayList<>(List.of(box));
        for(var old:covered) {
            List<AABB> next=new ArrayList<>();for(var part:pieces)next.addAll(ChamberUnion.subtract(part,old.box()));pieces=next;
            if(pieces.isEmpty())return 0;
        }
        return pieces.stream().mapToDouble(ChamberUnion::volume).sum();
    }
    private static double sum(double[] values){return Arrays.stream(values).sum();}
    private static void distribute(double amount,double[] weight,double[] target) {
        double total=sum(weight);if(total<=0)return; // No surviving domain/member: region was removed entirely.
        int last=-1;for(int i=0;i<weight.length;i++)if(weight[i]>0)last=i;
        double assigned=0;
        for(int i=0;i<weight.length;i++)if(weight[i]>0) {
            double share=i==last?amount-assigned:amount*weight[i]/total;target[i]+=share;assigned+=share;
        }
    }
    private ChamberInventory() {}
}
