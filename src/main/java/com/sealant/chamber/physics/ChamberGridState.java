package com.sealant.chamber.physics;

import net.minecraft.world.phys.AABB;
import net.minecraft.nbt.*;
import java.util.*;

/** Spatial phase inventories, not one aggregate per room. Field V3 water is reference volume (mass/density); gas is isothermal PV. V2 is accepted for migration. */
public final class ChamberGridState {
    public record Cell(AABB box,double water,double gasPV) {}
    public static ListTag write(List<Cell> cells){
        var list=new ListTag();for(var c:cells){var t=new CompoundTag();var b=c.box;
            t.putDouble("x",b.minX);t.putDouble("y",b.minY);t.putDouble("z",b.minZ);t.putDouble("X",b.maxX);t.putDouble("Y",b.maxY);t.putDouble("Z",b.maxZ);
            t.putDouble("Water",c.water);t.putDouble("GasPV",c.gasPV);list.add(t);
        }return list;
    }
    public static List<Cell> read(ListTag list){
        if(list.size()>65536)throw new IllegalArgumentException("Saved grid cell budget");
        List<Cell> cells=new ArrayList<>();
        for(int i=0;i<list.size();i++){
            var t=list.getCompound(i);double x=t.getDouble("x"),y=t.getDouble("y"),z=t.getDouble("z"),X=t.getDouble("X"),Y=t.getDouble("Y"),Z=t.getDouble("Z");
            double w=t.getDouble("Water"),g=t.getDouble("GasPV");
            if(!Double.isFinite(x+y+z+X+Y+Z+w+g)||X<=x||Y<=y||Z<=z||w<0||g<0)throw new IllegalArgumentException("Invalid saved grid inventory");
            cells.add(new Cell(new AABB(x,y,z,X,Y,Z),w,g));
        }return List.copyOf(cells);
    }
    private ChamberGridState(){}
}
