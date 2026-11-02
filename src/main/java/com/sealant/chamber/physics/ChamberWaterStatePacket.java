package com.sealant.chamber.physics;

import com.sealant.Sealant;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** One chamber-domain snapshot with distinct local cells; no room-wide surface or pressure. */
public record ChamberWaterStatePacket(List<Group> groups) implements CustomPacketPayload {
    public static final int MAX_GROUPS=128,MAX_BOXES=8192;
    public record Cell(AABB box,double water,double relativeSurface,double pressure) {}
    public record Group(UUID id,UUID owner,List<Cell> cells) {}
    public static final Type<ChamberWaterStatePacket> TYPE=new Type<>(Sealant.path("water_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ChamberWaterStatePacket> CODEC=StreamCodec.of((buf,p)->{
        buf.writeVarInt(p.groups.size());
        for(var g:p.groups){buf.writeUUID(g.id);buf.writeUUID(g.owner);buf.writeVarInt(g.cells.size());
            for(var c:g.cells){var b=c.box;buf.writeDouble(b.minX);buf.writeDouble(b.minY);buf.writeDouble(b.minZ);buf.writeDouble(b.maxX);buf.writeDouble(b.maxY);buf.writeDouble(b.maxZ);buf.writeDouble(c.water);buf.writeDouble(c.relativeSurface);buf.writeDouble(c.pressure);}}
    },buf->{
        int count=buf.readVarInt();if(count<0||count>MAX_GROUPS)throw new IllegalArgumentException("Water group count");
        var groups=new ArrayList<Group>();int total=0;
        for(int i=0;i<count;i++){
            UUID id=buf.readUUID(),owner=buf.readUUID();int n=buf.readVarInt();if(n<1 || (total+=n)>MAX_BOXES)throw new IllegalArgumentException("Water cell budget");
            var cells=new ArrayList<Cell>();
            for(int j=0;j<n;j++){
                double x=buf.readDouble(),y=buf.readDouble(),z=buf.readDouble(),X=buf.readDouble(),Y=buf.readDouble(),Z=buf.readDouble();
                double water=buf.readDouble(),surface=buf.readDouble(),pressure=buf.readDouble();
                if(!Double.isFinite(x+y+z+X+Y+Z+water+surface+pressure)||X<=x||Y<=y||Z<=z||water<0||pressure<0)throw new IllegalArgumentException("Invalid field cell");
                cells.add(new Cell(new AABB(x,y,z,X,Y,Z),water,surface,pressure));
            }
            groups.add(new Group(id,owner,List.copyOf(cells)));
        }return new ChamberWaterStatePacket(List.copyOf(groups));
    });
    @Override public Type<ChamberWaterStatePacket> type(){return TYPE;}
}
