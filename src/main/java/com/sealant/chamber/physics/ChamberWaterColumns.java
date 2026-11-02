package com.sealant.chamber.physics;

import java.util.*;

/** Lazy root-water column segments. Dry gaps keep separate heads; no chamber state here. */
public final class ChamberWaterColumns {
    @FunctionalInterface public interface Source {
        /** -1 = dry, NaN = unavailable, otherwise local fluid height in [0,1]. */
        double height(int x,int y,int z);
    }
    private record Span(int low,int high,double top) {}
    private static final class Column {
        final NavigableMap<Integer,Span> spans=new TreeMap<>();final Set<Integer> dry=new HashSet<>();
        Double top(int y){var e=spans.floorEntry(y);return e!=null && y<=e.getValue().high?e.getValue().top:null;}
    }
    private final Source source;private final int minY,maxY;
    private final Map<Long,Column> columns=new LinkedHashMap<>(64,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<Long,Column> e){return size()>4096;}
    };
    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    public ChamberWaterColumns(Source source,int minY,int maxY){this.source=source;this.minY=minY;this.maxY=maxY;}
    public double depth(int x,double y,int z){
        int blockY=(int)Math.floor(y);if(blockY<minY || blockY>=maxY)return 0;
        var column=columns.computeIfAbsent(key(x,z),k->new Column());
        Double top=column.top(blockY);if(top!=null)return Math.max(0,top-y);
        if(column.dry.contains(blockY))return 0;
        int last=blockY-1;double surface=Double.NaN;
        for(int cursor=blockY;cursor<maxY;cursor++){
            Double cached=column.top(cursor);
            if(cached!=null){surface=cached;var span=column.spans.floorEntry(cursor).getValue();last=span.high;break;}
            if(column.dry.contains(cursor))break;
            double height=source.height(x,cursor,z);
            if(!Double.isFinite(height))return Double.NaN; // Never persist an unknown column.
            if(height<0){column.dry.add(cursor);break;}
            surface=cursor+height;last=cursor;
        }
        if(last<blockY)return 0;
        column.spans.put(blockY,new Span(blockY,last,surface));
        return Math.max(0,surface-y);
    }
    public void invalidate(int x,int z){columns.remove(key(x,z));}
    public void invalidateChunk(int chunkX,int chunkZ){
        int minX=(chunkX<<4)-1,maxX=(chunkX<<4)+16,minZ=(chunkZ<<4)-1,maxZ=(chunkZ<<4)+16;
        columns.keySet().removeIf(k->{int x=(int)(k>>32),z=(int)(long)k;return x>=minX&&x<=maxX&&z>=minZ&&z<=maxZ;});
    }
    public void clear(){columns.clear();}
}
