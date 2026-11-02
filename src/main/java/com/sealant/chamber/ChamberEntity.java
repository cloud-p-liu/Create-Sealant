// Adapted from Simulated 1.3.2 HoneyGlueEntity (MIT); see THIRD_PARTY_NOTICES.md.
package com.sealant.chamber;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

/**
 * Fixed chamber volume, stored in the same Sable plot coordinates as copied honey glue.
 * CRITICAL: sealant:chamber_structure must remain in sable:retain_in_sub_level.
 * Otherwise addFreshEntity kicks it into world space and the OBB stops rotating.
 * See docs/CHAMBER.md and the user-verified baseline before changing this contract.
 * The marker has no collision or mass. ChamberPhysics owns its virtual fluid and applies loads to Sable bodies.
 */
public final class ChamberEntity extends Entity implements IEntityWithComplexSpawn {
    private ChamberBounds chamber;
    private final com.sealant.chamber.physics.ChamberFluid fluid=new com.sealant.chamber.physics.ChamberFluid();
    private java.util.List<com.sealant.chamber.physics.ChamberGridState.Cell> gridState=java.util.List.of();
    private boolean gridInitialized;
    public java.util.List<com.sealant.chamber.physics.ChamberGridState.Cell> gridState(){return gridState;}
    public boolean gridInitialized(){return gridInitialized;}
    public void setGridState(java.util.List<com.sealant.chamber.physics.ChamberGridState.Cell> cells){gridState=java.util.List.copyOf(cells);gridInitialized=true;}
    private boolean fluidShareInitialized;
    public void resetFluidShare(){fluidShareInitialized=false;gridInitialized=false;gridState=java.util.List.of();}
    public boolean fluidShareInitialized(){return fluidShareInitialized;}
    public void markFluidShareInitialized(){fluidShareInitialized=true;}
    public com.sealant.chamber.physics.ChamberFluid fluid(){return fluid;}

    public static ChamberEntity create(EntityType<?> type, Level level) { return new ChamberEntity(type, level); }
    public ChamberEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    /** Only creation / NBT restoration / initial tracking spawn may initialize this volume. */
    void initializeBounds(AABB box) {
        if (chamber != null) throw new IllegalStateException("A chamber cannot be resized after creation");
        chamber = new ChamberBounds(box);
        setBoundingBox(box);
        // Copied honey glue anchor: X/Z center, bottom Y. Keep it in plot space.
        setPosRaw(box.getCenter().x, box.minY, box.getCenter().z);
    }

    @Override public void tick() {
        // Keep the original lightweight tick: Entity.tick would add unrelated physics.
        xRotO = getXRot(); yRotO = getYRot(); walkDistO = walkDist;
        xo = getX(); yo = getY(); zo = getZ();
        if (chamber != null) setBoundingBox(chamber.box());
    }

    @Override public void setPos(double x, double y, double z) {
        // Preserve the copied anchor and extents during vanilla load/spawn positioning.
        // Sable motion does NOT call this each frame; its pose projects the local box.
        AABB box = getBoundingBox();
        setPosRaw(x, y, z);
        Vec3 center = box.getCenter();
        AABB translated = box.move(-center.x, -box.minY, -center.z).move(x, y, z);
        setBoundingBox(translated);
        if (chamber != null) chamber = chamber.atPosition(x, y, z);
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        // Preserve upstream From/To/Pos format so verified prototype chambers still load.
        Vec3 pos = position();
        AABB relative = (chamber == null ? getBoundingBox() : chamber.box()).move(pos.scale(-1));
        tag.put("From", VecHelper.writeNBT(relative.getMinPosition()));
        tag.put("To", VecHelper.writeNBT(relative.getMaxPosition()));
        CompoundTag fluidTag=new CompoundTag();
        fluidTag.putDouble("Water",fluid.totalWater());fluidTag.putDouble("GasPV",fluid.gasPV());
        fluidTag.putBoolean("GroupShare",fluidShareInitialized);
        tag.put("ChamberFluidV1",fluidTag);
        if(gridInitialized)tag.put("ChamberFieldV3",com.sealant.chamber.physics.ChamberGridState.write(gridState));
        if (!tag.contains("Pos")) tag.put("Pos", VecHelper.writeNBT(pos));
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        Vec3 pos = VecHelper.readNBT(tag.getList("Pos", 6));
        Vec3 from = VecHelper.readNBT(tag.getList("From", 6));
        Vec3 to = VecHelper.readNBT(tag.getList("To", 6));
        initializeBounds(new AABB(from, to).move(pos));
        CompoundTag saved=tag.getCompound("ChamberFluidV1");
        fluid.restore(saved.getDouble("Water"),saved.getDouble("GasPV"));
        gridInitialized=tag.contains("ChamberFieldV3",9) || tag.contains("ChamberGridV2",9);
        String gridKey=tag.contains("ChamberFieldV3",9)?"ChamberFieldV3":"ChamberGridV2";
        gridState=gridInitialized?com.sealant.chamber.physics.ChamberGridState.read(tag.getList(gridKey,10)):java.util.List.of();
        fluidShareInitialized=saved.getBoolean("GroupShare") || saved.getDouble("GasPV")>0 || saved.getDouble("Water")>0;
    }
    @Override public void writeSpawnData(RegistryFriendlyByteBuf buf) {
        // Upstream HoneyGlueEntityMixin contract: normal entity packets omit large bounds.
        // Initial tracking (including rejoin) must send the complete box, not default 0.1m dimensions.
        CompoundTag tag = new CompoundTag();
        addAdditionalSaveData(tag);
        tag.remove("ChamberGridV2");tag.remove("ChamberFieldV3"); // Large server-only spatial state travels via bounded water snapshots, not entity spawn.
        buf.writeNbt(tag);
    }
    @Override public void readSpawnData(RegistryFriendlyByteBuf buf) { readAdditionalSaveData(buf.readNbt()); }
    @Override protected boolean repositionEntityAfterLoad() { return false; }
    @Override public void refreshDimensions() {} // Never replace the fixed volume with EntityType's 0.1m box.
    @Override public void move(MoverType type, Vec3 movement) {} // Marker, never a physics participant.
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public void thunderHit(ServerLevel level, LightningBolt bolt) {}
    @Override public boolean isIgnoringBlockTriggers() { return true; }
    @Override public PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }
    @Override public double getEyeY() { return 0; }
    @Override public EntityDimensions getDimensions(Pose pose) { return super.getDimensions(pose).withEyeHeight(0); }
    public boolean contains(BlockPos pos) { return chamber != null && chamber.contains(Vec3.atCenterOf(pos)); }
}
