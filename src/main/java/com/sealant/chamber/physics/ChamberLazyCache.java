package com.sealant.chamber.physics;

import java.util.*;
import java.util.function.Supplier;

/** Bounded geometry memo. Invalidations are explicit, never driven by wall-clock polling. */
public final class ChamberLazyCache<K,V> {
    private final int limit;
    private final Map<K,V> values=new LinkedHashMap<>(64,.75f,true);
    public long hits,misses;
    public ChamberLazyCache(int limit){this.limit=limit;}
    public V get(K key,Supplier<V> create){
        V value=values.get(key);if(value!=null){hits++;return value;}
        misses++;value=create.get();values.put(key,value);
        if(values.size()>limit)values.remove(values.keySet().iterator().next());return value;
    }
    public void clear(){values.clear();}
}
