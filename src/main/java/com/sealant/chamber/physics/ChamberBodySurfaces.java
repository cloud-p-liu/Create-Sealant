package com.sealant.chamber.physics;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import java.util.*;

/** Cached exposed collision samples for one body. Real block changes invalidate it;
 * buoyancy metadata refresh (oldState == newState) must not trigger mesh rebuilding.
 */
final class ChamberBodySurfaces {
    static final int LIMIT=65536;
    static List<ChamberBodyPressure.Sample> build(ServerLevel level,ServerSubLevel body){
        var shapes=new HashMap<Long,List<AABB>>();var all=new ArrayList<AABB>();
        for(var holder:body.getPlot().getLoadedChunks()){
            var chunk=holder.getChunk();var sections=chunk.getSections();
            for(int i=0;i<sections.length;i++){
                var section=sections[i];if(section.hasOnlyAir())continue;
                int by=chunk.getSectionYFromSectionIndex(i)<<4;
                for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++){
                    var state=section.getBlockState(x,y,z);if(state.isAir())continue;
                    var pos=new BlockPos(chunk.getPos().getMinBlockX()+x,by+y,chunk.getPos().getMinBlockZ()+z);
                    var boxes=state.getCollisionShape(level,pos).toAabbs().stream().map(b->b.move(pos)).toList();
                    shapes.put(pos.asLong(),boxes);all.addAll(boxes);
                    if(all.size()>262144)throw new IllegalArgumentException("body collision geometry budget exceeded");
                }
            }
        }
        var exposed=new ArrayList<ChamberBodyPressure.Sample>();
        for(var box:all)for(var sample:ChamberBodyPressure.surface(box)){
            var outside=new Vector3d(sample.point()).fma(.003,sample.outward());
            var pos=BlockPos.containing(outside.x,outside.y,outside.z);
            boolean blocked=false;
            // Neighbor-dependent/extended shapes can cross a block boundary.
            for(int x=-1;x<=1 && !blocked;x++)for(int y=-1;y<=1 && !blocked;y++)for(int z=-1;z<=1 && !blocked;z++)
                for(var other:shapes.getOrDefault(pos.offset(x,y,z).asLong(),List.of()))
                    if(other.contains(outside.x,outside.y,outside.z)){blocked=true;break;}
            if(!blocked){exposed.add(sample);if(exposed.size()>LIMIT)throw new IllegalArgumentException("body surface exceeds "+LIMIT+" samples");}
        }
        return List.copyOf(exposed);
    }
    private ChamberBodySurfaces(){}
}
