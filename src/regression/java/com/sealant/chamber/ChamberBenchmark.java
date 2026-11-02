package com.sealant.chamber;

import com.sealant.chamber.runtime.ChamberGrid;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.AABB;
import java.util.*;
import java.nio.file.*;

/** Synthetic geometry only: deliberately does not claim to measure native physics, FPS or TPS. */
public final class ChamberBenchmark {
    private static volatile long sink;
    public static void main(String[] args) throws Exception {
        List<String> lines=new ArrayList<>();
        lines.add("chambers,entities,layout,mean_ms,p95_ms,sat_per_iteration");
        for(boolean dense:new boolean[]{false,true}) for(int[] size:new int[][]{{10,100},{100,1000},{1000,5000}}) {
            var boxes=new ArrayList<AABB>(); var queries=new ArrayList<AABB>(); var random=new Random(1234);
            double area=dense?32:2048;
            for(int i=0;i<size[0];i++) { double x=random.nextDouble()*area,z=random.nextDouble()*area;boxes.add(new AABB(x,0,z,x+8,8,z+8)); }
            for(int i=0;i<size[1];i++) { double x=random.nextDouble()*area,z=random.nextDouble()*area;queries.add(new AABB(x,2,z,x+.6,3.8,z+.6)); }
            var grid=new ChamberGrid<AABB>(); var sat=new ChamberIntersection();var pose=new Pose3d();
            long[] times=new long[100];long tests=0;
            for(int round=-30;round<100;round++) {
                long start=System.nanoTime();long[] n={0};long hits=0;
                grid.rebuild(boxes,b->b);
                for(var query:queries)if(grid.anyMatch(query,chamber -> {
                    if(!chamber.intersects(query))return false;
                    n[0]++;return sat.touches(chamber,pose,query,pose);
                }))hits++;
                sink=hits;
                if(round>=0){times[round]=System.nanoTime()-start;tests=n[0];}
            }
            double mean=Arrays.stream(times).average().orElse(0)/1e6;Arrays.sort(times);
            String row=String.format(Locale.ROOT,"%d,%d,%s,%.4f,%.4f,%d",size[0],size[1],dense?"dense":"sparse",mean,times[95]/1e6,tests);
            lines.add(row);System.out.println(row);
        }
        Path out=Path.of("build/reports/chamber-benchmark.csv");Files.createDirectories(out.getParent());Files.write(out,lines);
    }
}
