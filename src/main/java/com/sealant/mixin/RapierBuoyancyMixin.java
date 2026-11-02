package com.sealant.mixin;

import com.sealant.buoyancy.BuoyancyPipeline;
import com.sealant.buoyancy.NoBuoyancyColliderBakery;
import com.sealant.buoyancy.StructureBuoyancy;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline;
import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderBakery;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Per-plot native collider IDs; no force cancellation, no world/block edits and no mass edits.
 * Keep low 16 bits (Sable voxel neighborhood) intact. High 16 bits are collider handle + 1.
 */
@Mixin(value = RapierPhysicsPipeline.class, remap = false)
public abstract class RapierBuoyancyMixin implements BuoyancyPipeline {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private Int2ObjectMap<ServerSubLevel> activeSubLevels;
    @Unique private final Set<UUID> sealant$zeroedPlots = new HashSet<>();
    @Shadow public abstract void handleBlockChange(SectionPos sectionPos, LevelChunkSection section,
            int x, int y, int z, BlockState oldState, BlockState newState);
    @Unique private RapierVoxelColliderBakery sealant$noBuoyancyBakery;

    @Unique private boolean sealant$disabled(int blockX, int blockZ) {
        return Sable.HELPER.getContaining(level, (double)blockX, (double)blockZ) instanceof ServerSubLevel owner
                && StructureBuoyancy.isDisabled(owner);
    }
    @Unique private int sealant$replaceCollider(int original, BlockState state) {
        if ((original >>> 16) == 0) return original; // Keep empty/non-collidable cells empty.
        if (sealant$noBuoyancyBakery == null) sealant$noBuoyancyBakery = new NoBuoyancyColliderBakery(level);
        var replacement = sealant$noBuoyancyBakery.getPhysicsDataForBlock(state);
        if (replacement == null) throw new IllegalStateException("Missing zero-buoyancy collider for " + state);
        return (original & 0xffff) | ((replacement.handle() + 1) << 16);
    }

    @Inject(method="handleBlockChange",at=@At("HEAD"),require=1)
    private void sealant$invalidateInternalSurface(SectionPos sectionPos,LevelChunkSection section,
            int x,int y,int z,BlockState oldState,BlockState newState,CallbackInfo ci){
        if(oldState!=newState)com.sealant.chamber.physics.ChamberPhysics.invalidateBodySurface(level,
                sectionPos.minBlockX()+(x&15),sectionPos.minBlockZ()+(z&15));
    }

    @Inject(method="handleChunkSectionAddition",at=@At("HEAD"),require=1)
    private void sealant$invalidateLoadedSurface(LevelChunkSection section,int x,int y,int z,boolean uploadDataIfGlobal,CallbackInfo ci){
        com.sealant.chamber.physics.ChamberPhysics.invalidateBodySurface(level,x<<4,z<<4);
    }

    @ModifyArgs(method = "handleChunkSectionAddition", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;addChunk(JIII[IZI)V"), require = 1)
    private void sealant$uploadChunk(Args args, LevelChunkSection section, int x, int y, int z, boolean uploadDataIfGlobal) {
        if (!sealant$disabled(x << 4, z << 4)) return;
        int[] packed = args.get(4);
        for (int bx = 0; bx < 16; bx++) for (int bz = 0; bz < 16; bz++) for (int by = 0; by < 16; by++) {
            int index = bx + (bz << 4) + (by << 8);
            packed[index] = sealant$replaceCollider(packed[index], section.getBlockState(bx, by, bz));
        }
    }

    @ModifyArgs(method = "handleBlockChange", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;changeBlock(JIIII)V"), require = 2)
    private void sealant$uploadBlock(Args args, SectionPos sectionPos, LevelChunkSection section,
            int x, int y, int z, BlockState oldState, BlockState newState) {
        int nativeX = args.get(1), nativeY = args.get(2), nativeZ = args.get(3);
        if (!sealant$disabled(nativeX, nativeZ)) return;
        // This method also uploads six neighbors, which can belong to another plot.
        // Decide ownership for EACH native position, not just the original changed block.
        boolean changed = nativeX == sectionPos.minBlockX() + (x & 15)
                && nativeY == sectionPos.minBlockY() + (y & 15)
                && nativeZ == sectionPos.minBlockZ() + (z & 15);
        BlockState state = changed ? newState : level.getBlockState(new BlockPos(nativeX, nativeY, nativeZ));
        args.set(4, sealant$replaceCollider(args.get(4), state));
    }

    @Override public int sealant$refreshBuoyancy(ServerSubLevel structure) {
        long timing = com.sealant.chamber.runtime.ChamberMetrics.begin(level);
        int blocks = 0;
        // Use upstream's incremental native update, preserving octree/collision bookkeeping.
        // old == new deliberately: only collider metadata changes; no Minecraft block/mass update.
        for (var holder : structure.getPlot().getLoadedChunks()) {
            var chunk = holder.getChunk();
            var sections = chunk.getSections();
            for (int index = 0; index < sections.length; index++) {
                LevelChunkSection section = sections[index];
                if (section.hasOnlyAir()) continue;
                SectionPos pos = SectionPos.of(chunk.getPos(), chunk.getSectionYFromSectionIndex(index));
                for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                    BlockState state = section.getBlockState(x, y, z);
                    if (state.isAir()) continue;
                    handleBlockChange(pos, section, x, y, z, state, state);
                    blocks++;
                }
            }
        }
        if (StructureBuoyancy.isDisabled(structure)) sealant$zeroedPlots.add(structure.getUniqueId());
        else sealant$zeroedPlots.remove(structure.getUniqueId());
        com.sealant.chamber.runtime.ChamberMetrics.end(level,"collider_refresh",timing);
        com.sealant.chamber.runtime.ChamberMetrics.count(level,"refreshed_blocks",blocks);
        return blocks;
    }

    @Inject(method = "prePhysicsTicks", at = @At("HEAD"), require = 1)
    private void sealant$restoreSavedMode(CallbackInfo ci) {
        com.sealant.chamber.runtime.ChamberRuntime.update(level);
        // Sable 2.0.4 loads plot chunks BEFORE restoring user_data. Chunk upload alone
        // therefore cannot restore a saved disabled flag. Reconcile before native buoyancy
        // runs, and refresh only on an actual mode transition (never every physics tick).
        for (ServerSubLevel structure : activeSubLevels.values()) {
            if (!structure.isRemoved() && StructureBuoyancy.isDisabled(structure)
                    != sealant$zeroedPlots.contains(structure.getUniqueId())) {
                sealant$refreshBuoyancy(structure);
                ((RapierPhysicsPipeline)(Object)this).wakeUp(structure);
            }
        }
    }

    @Inject(method = "remove(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)V", at = @At("HEAD"), require = 1)
    private void sealant$forgetUnloadedPlot(ServerSubLevel structure, CallbackInfo ci) {
        sealant$zeroedPlots.remove(structure.getUniqueId());
    }
}
