package com.sealant.chamber.client;

import com.sealant.chamber.ChamberIntersection;
import com.sealant.chamber.runtime.ChamberRuntime;
import com.sealant.chamber.topology.*;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import java.util.*;

/** Geometric chamber-group lighting (fluid grid partitions are currently separate). Root bodies keep native water attenuation.
 * Each component has its own sampled ambient; no per-sublevel global room average.
 * Parent-body queries never include their own components; cycles fall back to native light.
 */
public final class ChamberLighting {
    private static ClientLevel level;private static long tick=Long.MIN_VALUE;
    private static List<ChamberTopology.Node> signature=List.of();
    private static List<ChamberTopology.Group> groups=List.of();
    private static final Map<UUID,ClientSubLevel> owners=new HashMap<>();
    private static final Map<UUID,ChamberLightValue> bodyCache=new HashMap<>(),groupCache=new HashMap<>();
    private static final Set<UUID> resolving=new HashSet<>();
    private static final ChamberIntersection sat=new ChamberIntersection();
    public static void clear(){level=null;tick=Long.MIN_VALUE;signature=List.of();groups=List.of();owners.clear();bodyCache.clear();groupCache.clear();resolving.clear();}
    public static void invalidate(){tick=Long.MIN_VALUE;bodyCache.clear();groupCache.clear();}
    private static boolean refresh() {
        var current=Minecraft.getInstance().level;
        if(current==null){clear();return false;}
        if(current!=level){clear();level=current;}
        if(!ChamberVisuals.enabled() || !ChamberVisuals.lighting() || !ChamberRuntime.enabled(level))return false;
        if(tick==level.getGameTime())return true;
        tick=level.getGameTime();bodyCache.clear();groupCache.clear();resolving.clear();owners.clear();
        List<ChamberTopology.Node> nodes=new ArrayList<>();
        for(var chamber:ChamberRuntime.chambers(level))if(chamber.isAlive() && Sable.HELPER.getContainingClient(chamber) instanceof ClientSubLevel owner && !owner.isRemoved()) {
            owners.put(owner.getUniqueId(),owner);nodes.add(new ChamberTopology.Node(chamber.getUUID(),owner.getUniqueId(),chamber.getBoundingBox()));
        }
        nodes.sort(Comparator.comparing(ChamberTopology.Node::id));
        if(!signature.equals(nodes)) {
            try{groups=ChamberTopology.build(nodes);}catch(IllegalArgumentException tooComplex){groups=List.of();}
            signature=List.copyOf(nodes);
        }
        return true;
    }
    public static ChamberLightValue ambient(ClientSubLevel body) {
        if(!refresh())return new ChamberLightValue(0,0,false);
        var cached=bodyCache.get(body.getUniqueId());if(cached!=null)return cached;
        if(!resolving.add(body.getUniqueId()))return new ChamberLightValue(body.computeSubLevelSkyLight(body.logicalPose()),0,false);
        try {
            var b=body.getPlot().getBoundingBox();
            if(b==null)return new ChamberLightValue(0,0,false);
            AABB local=new AABB(b.minX(),b.minY(),b.minZ(),(double)b.maxX()+1,(double)b.maxY()+1,(double)b.maxZ()+1);
            double ownVolume=groups.stream().filter(g->g.owner().equals(body.getUniqueId())).mapToDouble(g->g.union().volume).max().orElse(0);
            var candidates=groups.stream().filter(group->!group.owner().equals(body.getUniqueId()) && (ownVolume==0 || group.union().volume>ownVolume+1e-8))
                    .sorted(Comparator.comparingDouble((ChamberTopology.Group group)->group.union().volume).thenComparing(ChamberTopology.Group::id)).toList();
            Map<ChamberTopology.Group,Double> weights=new LinkedHashMap<>();
            // A body spanning rooms gets a spatially weighted DEFAULT; rooms remain separate fields.
            for(int x=0;x<3;x++)for(int y=0;y<3;y++)for(int z=0;z<3;z++) {
                Vec3 point=body.renderPose().transformPosition(new Vec3(local.minX+(x+.5)*local.getXsize()/3,local.minY+(y+.5)*local.getYsize()/3,local.minZ+(z+.5)*local.getZsize()/3));
                for(var group:candidates) {
                    var owner=owners.get(group.owner());if(owner==null)continue;
                    Vec3 p=owner.renderPose().transformPositionInverse(point);
                    if(group.union().contains(p.x,p.y,p.z)){weights.merge(group,1.0,Double::sum);break;}
                }
            }
            if(weights.isEmpty())for(var group:candidates) {
                var owner=owners.get(group.owner());if(owner==null)continue;
                for(var piece:group.union().pieces)if(sat.touches(local,body.renderPose(),piece.box(),owner.renderPose())){weights.put(group,1.0);break;}
            }
            double sky=0,block=0,total=0;
            for(var entry:weights.entrySet()){var value=average(entry.getKey());sky+=value.sky()*entry.getValue();block+=value.block()*entry.getValue();total+=entry.getValue();}
            var result=total>0?new ChamberLightValue(sky/total,block/total,true):new ChamberLightValue(0,0,false);
            bodyCache.put(body.getUniqueId(),result);return result;
        } finally {resolving.remove(body.getUniqueId());}
    }
    private static ChamberLightValue average(ChamberTopology.Group group) {
        var cached=groupCache.get(group.id());if(cached!=null)return cached;
        var owner=owners.get(group.owner());if(owner==null)return new ChamberLightValue(0,0,false);
        var parent=ambient(owner);
        double ownerSky=parent.inherited()?parent.sky():owner.computeSubLevelSkyLight(owner.logicalPose());
        double sky=0,block=0,total=0;
        var samples=group.union().cells;int stride=Math.max(1,(int)Math.ceil(samples.size()/1024.0));
        for(int i=0;i<samples.size();i+=stride) {
            var sample=samples.get(i).sample();var p=sample.point();BlockPos pos=BlockPos.containing(p.x,p.y,p.z);
            if(!level.hasChunkAt(pos))continue;
            boolean blocked=false;
            for(var box:level.getBlockState(pos).getCollisionShape(level,pos).toAabbs())if(box.move(pos).contains(p.x,p.y,p.z)){blocked=true;break;}
            if(blocked)continue;
            double weight=sample.volume()*Math.min(stride,samples.size()-i);
            sky+=level.getBrightness(LightLayer.SKY,pos)*ownerSky/15*weight;
            block+=Math.max(level.getBrightness(LightLayer.BLOCK,pos),parent.inherited()?parent.block():0)*weight;total+=weight;
        }
        var result=total>0?new ChamberLightValue(sky/total,block/total,true):new ChamberLightValue(ownerSky,parent.inherited()?parent.block():0,true);
        groupCache.put(group.id(),result);return result;
    }
    public static int sky(ClientSubLevel body,int original){var value=ambient(body);return value.inherited()?(int)Math.round(Math.clamp(value.sky(),0,15)):original;}
    public static float shader(ClientSubLevel body,float original){return ambient(body).shaderScale(original);}
    public static int packed(ClientSubLevel body,int original){return ambient(body).packedBlockFloor(original);}
    public static List<String> report() {
        if(!refresh())return List.of("Chamber 光照繼承未開啟或尚無世界。");
        List<String> result=new ArrayList<>();
        for(var group:groups){var value=average(group);result.add(String.format(Locale.ROOT,"group %s owner %s：%d chamber，平均 SKY=%.2f BLOCK=%.2f",group.id(),group.owner(),group.members().size(),value.sky(),value.block()));}
        if(result.isEmpty())result.add("沒有已載入 chamber 連通群。");return result;
    }
    private ChamberLighting() {}
}
