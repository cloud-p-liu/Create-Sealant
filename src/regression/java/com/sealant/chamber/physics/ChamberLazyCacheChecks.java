package com.sealant.chamber.physics;

import java.util.*;

public final class ChamberLazyCacheChecks {
    public static void run(){
        try {
            var type=net.minecraft.world.level.chunk.LevelChunk.class;
            check(java.lang.reflect.Modifier.isFinal(type.getDeclaredField("level").getModifiers()),"Dirty hook LevelChunk.level field contract");
            check(type.getDeclaredMethod("setBlockState",net.minecraft.core.BlockPos.class,net.minecraft.world.level.block.state.BlockState.class,boolean.class).getReturnType()==net.minecraft.world.level.block.state.BlockState.class,"Dirty hook block setter contract");
        }catch(ReflectiveOperationException failure){throw new AssertionError("Cache invalidation mixin target changed",failure);}

        int[] reads={0};Map<Integer,Double> fluid=new HashMap<>();
        // Two disconnected pools in one x/z column, with a partial top block.
        for(int y=0;y<10;y++)fluid.put(y,1.0);fluid.put(10,.5);
        fluid.put(20,1.0);fluid.put(21,.75);
        boolean[] loaded={true};
        var columns=new ChamberWaterColumns((x,y,z)->{reads[0]++;return loaded[0]?fluid.getOrDefault(y,-1.0):Double.NaN;},-64,320);
        near(columns.depth(0,2.25,0),8.25,"Top coordinate gives hydrostatic depth");
        int scanned=reads[0];near(columns.depth(0,7.25,0),3.25,"Same water column, new sample height");
        check(reads[0]==scanned,"Cached segment must not rescan fluid");
        near(columns.depth(0,10.75,0),0,"Above partial fluid surface is dry");
        near(columns.depth(0,15.5,0),0,"Dry gap must not borrow upper pool pressure");
        near(columns.depth(0,20.25,0),1.5,"Disconnected pool has its own head");
        near(columns.depth(0,.25,0),10.25,"Lower query joins already cached upper segment");
        // Raise surface: explicit dirty notification is required and changes all pressures.
        fluid.put(10,1.0);fluid.put(11,1.0);columns.invalidate(0,0);
        near(columns.depth(0,2.25,0),9.75,"Fluid edit invalidates top coordinate");
        // Drain: cached water must not survive a real column edit.
        fluid.clear();columns.invalidate(0,0);near(columns.depth(0,2.25,0),0,"Drained column clears pressure");
        // Unknown chunks are never committed as dry or as a guessed truncated water head.
        loaded[0]=false;columns.invalidateChunk(0,0);
        check(Double.isNaN(columns.depth(0,0,0)),"Unknown column propagates unavailable");
        loaded[0]=true;for(int y=0;y<200;y++)fluid.put(y,1.0);
        near(columns.depth(0,.5,0),199.5,"Tall water column does not silently truncate at 64 blocks");
        columns.invalidateChunk(0,0);fluid.clear();near(columns.depth(0,.5,0),0,"Chunk replacement invalidates cached head");
        var cache=new ChamberLazyCache<String,Integer>(2);int[] builds={0};
        for(int i=0;i<100;i++)check(cache.get("same-pose-and-state",()->++builds[0])==1,"Resting geometry reuses lazy memo");
        check(builds[0]==1 && cache.hits==99,"No per-step geometry rebuild at rest");
        cache.get("moved-pose",()->++builds[0]);check(builds[0]==2,"Movement rebuilds geometry");
        cache.clear();cache.get("same-pose-and-state",()->++builds[0]);check(builds[0]==3,"Shape/config/chunk invalidation rebuilds geometry");
        cache.get("third-pose",()->++builds[0]);cache.get("fourth-pose",()->++builds[0]);int before=builds[0];
        cache.get("same-pose-and-state",()->++builds[0]);check(builds[0]==before+1,"Bounded memo evicts least recently used key");
        System.out.println("Lazy geometry memo and water-column head/depth, dry gaps, dirty edits, unknown chunks and tall-column checks passed.");
    }
    private static void near(double actual,double expected,String message){check(Double.isFinite(actual)&&Math.abs(actual-expected)<1e-9,message+": "+actual+" != "+expected);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
