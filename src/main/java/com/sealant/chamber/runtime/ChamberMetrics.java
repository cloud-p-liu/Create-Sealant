package com.sealant.chamber.runtime;

import net.minecraft.world.level.Level;
import java.util.*;

/** Opt-in timing. No nanoTime or sample allocation when profiling is disabled. */
public final class ChamberMetrics {
    private static final Map<Level, Session> sessions = new java.util.concurrent.ConcurrentHashMap<>();
    private static final class Samples {
        final List<Long> nanos=new ArrayList<>();
        long total, max, calls;
        void add(long n) { calls++;total+=n;max=Math.max(max,n); if(nanos.size()<100000)nanos.add(n); }
        String summary() {
            var sorted=new ArrayList<>(nanos); Collections.sort(sorted);
            long p95=sorted.isEmpty()?0:sorted.get(Math.min(sorted.size()-1,(int)(sorted.size()*0.95)));
            return String.format(Locale.ROOT,"n=%d mean=%.4fms p95=%.4fms max=%.4fms total=%.2fms",
                    calls,calls==0?0:total/1e6/calls,p95/1e6,max/1e6,total/1e6);
        }
    }
    private static final class Session {
        final long expires; final Map<String,Samples> phases=new TreeMap<>(); final Map<String,Long> counts=new TreeMap<>();
        Session(long expires) { this.expires=expires; }
    }
    public static void start(Level level,int seconds) { sessions.put(level,new Session(level.getGameTime()+seconds*20L)); }
    public static boolean active(Level level) { Session s=sessions.get(level);return s!=null && level.getGameTime()<s.expires; }
    public static long begin(Level level) {
        Session s=sessions.get(level); return s!=null && level.getGameTime()<s.expires?System.nanoTime():0;
    }
    public static void end(Level level,String phase,long start) { if(start!=0) duration(level,phase,System.nanoTime()-start); }
    public static void duration(Level level,String phase,long nanos) {
        Session s=sessions.get(level); if(s!=null && level.getGameTime()<s.expires)s.phases.computeIfAbsent(phase,k->new Samples()).add(nanos);
    }
    public static void count(Level level,String key,long n) {
        Session s=sessions.get(level); if(s!=null && level.getGameTime()<s.expires)s.counts.merge(key,n,Long::sum);
    }
    public static List<String> report(Level level) {
        Session s=sessions.get(level); if(s==null)return List.of("尚無量測；使用 /sealant perf start 30");
        List<String> lines=new ArrayList<>();
        lines.add("chamber profiling "+(level.getGameTime()<s.expires?"進行中":"已完成")+"；各 phase 的 n 是呼叫次數，非 TPS。");
        s.phases.forEach((k,v)->lines.add(k+": "+v.summary()));
        lines.add(s.counts.toString()); return lines;
    }
    public static void clear(Level level) { sessions.remove(level); }
    private ChamberMetrics() {}
}
