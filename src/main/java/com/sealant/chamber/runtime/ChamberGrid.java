package com.sealant.chamber.runtime;

import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import java.util.*;
import java.util.function.Function;

/** 16-block world-space buckets. Oversized boxes fall back safely instead of exploding memory. */
public final class ChamberGrid<T> {
    private final Map<Long, List<T>> cells = new HashMap<>();
    private final List<T> oversized = new ArrayList<>(), all = new ArrayList<>();
    private static final long MAX_CELLS = 4096;
    public void rebuild(Collection<T> values, Function<T, AABB> bounds) {
        cells.clear(); oversized.clear(); all.clear(); all.addAll(values);
        for (T value : values) {
            AABB box = bounds.apply(value);
            int x0 = cell(box.minX), y0 = cell(box.minY), z0 = cell(box.minZ);
            int x1 = cell(box.maxX), y1 = cell(box.maxY), z1 = cell(box.maxZ);
            if (count(x0,y0,z0,x1,y1,z1) > MAX_CELLS) { oversized.add(value); continue; }
            for (int x=x0;x<=x1;x++) for (int y=y0;y<=y1;y++) for (int z=z0;z<=z1;z++)
                cells.computeIfAbsent(SectionPos.asLong(x,y,z), ignored -> new ArrayList<>()).add(value);
        }
    }
    public Collection<T> query(AABB box) {
        if (all.isEmpty()) return List.of();
        int x0=cell(box.minX), y0=cell(box.minY), z0=cell(box.minZ);
        int x1=cell(box.maxX), y1=cell(box.maxY), z1=cell(box.maxZ);
        if (count(x0,y0,z0,x1,y1,z1) > MAX_CELLS) return all;
        Set<T> result = new LinkedHashSet<>(oversized);
        for(int x=x0;x<=x1;x++) for(int y=y0;y<=y1;y++) for(int z=z0;z<=z1;z++) {
            var bucket=cells.get(SectionPos.asLong(x,y,z));
            if(bucket!=null) result.addAll(bucket);
        }
        return result;
    }
    /** Lazy membership query: stop at first hit, without materializing all overlapping chambers. */
    public boolean anyMatch(AABB box, java.util.function.Predicate<T> predicate) {
        if(all.isEmpty())return false;
        int x0=cell(box.minX),y0=cell(box.minY),z0=cell(box.minZ);
        int x1=cell(box.maxX),y1=cell(box.maxY),z1=cell(box.maxZ);
        if(count(x0,y0,z0,x1,y1,z1)>MAX_CELLS) {
            for(T value:all)if(predicate.test(value))return true;
            return false;
        }
        Set<T> seen=new HashSet<>();
        for(T value:oversized)if(seen.add(value) && predicate.test(value))return true;
        for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++) {
            var bucket=cells.get(SectionPos.asLong(x,y,z));
            if(bucket!=null)for(T value:bucket)if(seen.add(value) && predicate.test(value))return true;
        }
        return false;
    }
    private static int cell(double v) { return Mth.floor(v) >> 4; }
    private static long count(int x0,int y0,int z0,int x1,int y1,int z1) {
        long x=(long)x1-x0+1,y=(long)y1-y0+1,z=(long)z1-z0+1;
        if(x>MAX_CELLS || y>MAX_CELLS || z>MAX_CELLS)return MAX_CELLS+1;
        return x*y*z;
    }
}
