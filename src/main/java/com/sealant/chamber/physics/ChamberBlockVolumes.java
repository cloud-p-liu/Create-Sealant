package com.sealant.chamber.physics;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import java.util.*;

/** Declared solid volume per unit block. Collision shapes never determine this value. */
public final class ChamberBlockVolumes {
    private record Rule(String block,Map<String,String> states,double volume) {
        boolean matches(BlockState state){
            if(!BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(block))return false;
            for(var e:states.entrySet()){
                var property=state.getBlock().getStateDefinition().getProperty(e.getKey());
                if(property==null || !value(state,property).equals(e.getValue()))return false;
            }
            return true;
        }
    }
    private static List<String> signature=List.of();
    private static List<Rule> rules=List.of();
    private static final Map<BlockState,Double> cache=new HashMap<>();
    private static <T extends Comparable<T>> String value(BlockState state,Property<T> property){return property.getName(state.getValue(property));}
    private static Rule parse(String text){
        int split=text.lastIndexOf('=');if(split<1)throw new IllegalArgumentException("Block volume rule needs selector=value: "+text);
        double volume=Double.parseDouble(text.substring(split+1).trim());
        if(!Double.isFinite(volume)||volume<0||volume>1)throw new IllegalArgumentException("Block volume outside [0,1]: "+text);
        String selector=text.substring(0,split).trim(),block=selector;Map<String,String> states=new LinkedHashMap<>();
        int bracket=selector.indexOf('[');
        if(bracket>=0){
            if(!selector.endsWith("]"))throw new IllegalArgumentException("Invalid block state selector: "+text);
            block=selector.substring(0,bracket).trim();
            for(String pair:selector.substring(bracket+1,selector.length()-1).split(",")){
                String[] parts=pair.trim().split("=",-1);
                if(parts.length!=2 || parts[0].isBlank() || parts[1].isBlank() || states.put(parts[0].trim(),parts[1].trim())!=null)throw new IllegalArgumentException("Invalid state selector: "+text);
            }
        }
        if(ResourceLocation.tryParse(block)==null)throw new IllegalArgumentException("Invalid block ID: "+text);
        return new Rule(ResourceLocation.parse(block).toString(),Map.copyOf(states),volume);
    }
    public static boolean validRule(Object text){try{if(!(text instanceof String s))return false;parse(s);return true;}catch(IllegalArgumentException failure){return false;}}
    public static double solid(BlockState state,List<? extends String> configured){
        if(!signature.equals(configured)){
            List<Rule> next=new ArrayList<>();
            for(String text:configured){
                var rule=parse(text);var id=ResourceLocation.parse(rule.block);
                if(!BuiltInRegistries.BLOCK.containsKey(id))throw new IllegalArgumentException("Unknown volume block: "+id);
                var block=BuiltInRegistries.BLOCK.get(id);
                for(var e:rule.states.entrySet()){
                    var p=block.getStateDefinition().getProperty(e.getKey());
                    if(p==null || p.getValue(e.getValue()).isEmpty())throw new IllegalArgumentException("Invalid volume state: "+text);
                }
                next.add(rule);
            }
            // More specific selectors win; later rules win ties.
            next.sort(Comparator.comparingInt(r->r.states.size()));
            signature=List.copyOf(configured);rules=List.copyOf(next);cache.clear();
        }
        return cache.computeIfAbsent(state,key->{double result=builtin(key);for(var rule:rules)if(rule.matches(key))result=rule.volume;return result;});
    }
    public static double builtin(BlockState state){
        if(state.isAir() || state.getBlock() instanceof LiquidBlock)return 0;
        // Explicit table exceptions, not a collision-shape-derived volume policy.
        if(state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT) || state.is(Blocks.SNOW))return 0;
        if(state.is(Blocks.SOUL_SAND) || state.is(Blocks.HONEY_BLOCK))return 1;
        if(state.getBlock() instanceof SlabBlock)return state.getValue(SlabBlock.TYPE)==SlabType.DOUBLE?1:.5;
        if(state.getBlock() instanceof StairBlock)return switch(state.getValue(StairBlock.SHAPE)){
            case STRAIGHT -> .75;case INNER_LEFT,INNER_RIGHT -> .875;case OUTER_LEFT,OUTER_RIGHT -> .625;
        };
        return 1;
    }
    public static double capacity(double cellVolume,double declaredSolidVolume){return Math.max(0,cellVolume-Math.min(cellVolume,declaredSolidVolume));}
    private ChamberBlockVolumes(){}
}
