package com.sealant.chamber.physics;

import com.sealant.Sealant;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.ArrayList;
import java.util.List;

/** Bounded, opt-in diagnostics only. Coordinates remain in the owner's plot for Sable Outliner. */
public record ChamberWaterDebugPacket(List<Point> points) implements CustomPacketPayload {
    public static final int LIMIT=1024;
    public record Point(double x,double y,double z,int color,float radius) {}
    public static final Type<ChamberWaterDebugPacket> TYPE=new Type<>(Sealant.path("water_debug"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ChamberWaterDebugPacket> CODEC=StreamCodec.of((buf,p)->{
        if(p.points.size()>LIMIT)throw new IllegalArgumentException("Too many debug points");
        buf.writeVarInt(p.points.size());
        for(var v:p.points){buf.writeDouble(v.x);buf.writeDouble(v.y);buf.writeDouble(v.z);buf.writeInt(v.color);buf.writeFloat(v.radius);}
    },buf->{
        int size=buf.readVarInt();if(size<0 || size>LIMIT)throw new IllegalArgumentException("Invalid debug point count");
        var points=new ArrayList<Point>(size);
        for(int i=0;i<size;i++)points.add(new Point(buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readInt(),buf.readFloat()));
        return new ChamberWaterDebugPacket(List.copyOf(points));
    });
    @Override public Type<ChamberWaterDebugPacket> type(){return TYPE;}
}
