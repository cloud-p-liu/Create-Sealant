// Copied from Simulated 1.3.2 honey glue (MIT); see THIRD_PARTY_NOTICES.md.
package com.sealant.chamber;

import com.simibubi.create.AllKeys;
import net.minecraft.network.chat.Component;
import com.simibubi.create.foundation.utility.RaycastHelper;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import com.sealant.Sealant;

import dev.simulated_team.simulated.mixin.aabb.AABBMixin;
import com.sealant.chamber.RemoveChamberPacket;
import com.sealant.chamber.CreateChamberPacket;
import dev.simulated_team.simulated.service.SimConfigService;
import dev.simulated_team.simulated.util.SimColors;
import dev.simulated_team.simulated.util.SimDistUtil;
import dev.simulated_team.simulated.util.click_interactions.InteractCallback;
import com.sealant.SealantNetwork;
import java.util.List;
import java.util.Optional;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adapted directly from Simulated honey glue, verified in-game on 2026-10-29.
 * Preserve Level.clip, plot-space corners, inverse-pose hover clipping and the
 * Sable-aware Outliner path. See docs/CHAMBER.md before changing coordinates.
 * Chamber changes: blue outlines, same-structure validation, no resizing.
 */
public class SealantSelectionHandler implements InteractCallback {
   private SealantSelectionHandler.State currentState = SealantSelectionHandler.State.UNBOUND;
   private BlockPos selectedPos;
   private ChamberEntity hoveredChamber;
   private Direction hoveredFace;

   public boolean selectPos(BlockPos pos, Player player, ItemStack sealantStack) {
      // Keep plot-space BlockPos from the original honey glue path. Never project
      // these positions to world space: Sable's Outliner handles that at render time.
      var owner = Sable.HELPER.getContainingClient(pos);
      if (owner == null || (this.selectedPos != null && owner != Sable.HELPER.getContainingClient(this.selectedPos))) {
         player.displayClientMessage(net.minecraft.network.chat.Component.translatable("item.sealant.sealant.same_structure"), true);
         return false;
      }
      if (this.currentState == SealantSelectionHandler.State.UNBOUND) {
         this.selectedPos = pos;
         this.currentState = SealantSelectionHandler.State.BINDING;
         player.displayClientMessage(Component.translatable("item.sealant.sealant.first_point"), true);
         player.level().playSound(player, this.selectedPos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.75F, 1.0F);
      } else {
         if (!this.checkBBValidity(player, sealantStack, pos, false)) {
            return false;
         }

         SealantNetwork.sendToServer(new CreateChamberPacket(this.selectedPos, pos));
         player.level().playSound(player, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.75F, 1.0F);
         this.selectedPos = null;
         this.currentState = SealantSelectionHandler.State.UNBOUND;
      }

      return true;
   }

   @Override
   public InteractCallback.Result onUse(int modifiers, int action, KeyMapping rightKey) {
      if (action == 1) {
         Player player = SimDistUtil.getClientPlayer();
         InteractionHand hand = this.getSealantHand(player);
         if (hand == null) {
            return InteractCallback.Result.empty();
         }

         if (player.isShiftKeyDown()) {
            this.clearAndSwing(player);
            return new InteractCallback.Result(true);
         }

         BlockHitResult bhr = this.getHitResult();
         if (AllKeys.altDown() && bhr.getType() == Type.MISS) {
            boolean success = this.selectPos(bhr.getBlockPos(), player, player.getItemInHand(hand));
            if (success) {
               player.swing(InteractionHand.MAIN_HAND);
               return new InteractCallback.Result(true);
            }

            return InteractCallback.Result.empty();
         }
      }

      return InteractCallback.super.onUse(modifiers, action, rightKey);
   }

   @Override
   public InteractCallback.Result onAttack(int modifiers, int action, KeyMapping leftKey) {
      if (action == 1) {
         Player player = SimDistUtil.getClientPlayer();
         if (this.getSealantHand(player) == null) {
            return InteractCallback.Result.empty();
         }

         if (this.hoveredChamber != null) {
            SealantNetwork.sendToServer(new RemoveChamberPacket(this.hoveredChamber.getUUID()));
            this.clearAndSwing(player);
            return new InteractCallback.Result(true);
         }
      }

      return InteractCallback.super.onAttack(modifiers, action, leftKey);
   }

   @Override
   public void clientTick(Level level, LocalPlayer player) {
      InteractionHand hand = this.getSealantHand(player);
      if (hand == null) {
         this.hoveredChamber = null;
         this.selectedPos = null;
         this.currentState = SealantSelectionHandler.State.UNBOUND;
      } else {
         BlockHitResult bhr = this.getHitResult();
         this.renderSelection(bhr, player.isShiftKeyDown() ? SimColors.DISCARDABLE_ORANGE : ChamberStyle.COLOR);
         this.updateHovered();
         this.renderChambers();
         this.checkBBValidity(player, player.getItemInHand(hand), bhr.getBlockPos(), AllKeys.altDown() || bhr.getType() != Type.MISS);
      }
   }

   private void renderSelection(BlockHitResult bhr, int color) {
      if (AllKeys.altDown() && !AllKeys.shiftDown() && this.currentState == SealantSelectionHandler.State.UNBOUND) {
         Outliner.getInstance()
            .showAABB("sealant:chamber", new AABB(bhr.getBlockPos()))
            .colored(color)
            .clearTextures()
            .disableLineNormals()
            .lineWidth(0.0625F);
      } else if ((AllKeys.altDown() || bhr.getType() != Type.MISS) && this.currentState == SealantSelectionHandler.State.BINDING) {
         if (Sable.HELPER.getContainingClient(bhr.getBlockPos()) != Sable.HELPER.getContainingClient(this.selectedPos)) {
            return;
         }

         Outliner.getInstance()
            .showAABB("sealant:chamber", AABB.encapsulatingFullBlocks(bhr.getBlockPos(), this.selectedPos))
            .colored(color)
            .clearTextures()
            .disableLineNormals()
            .lineWidth(0.0625F);
      }
   }

   public void updateHovered() {
      Player player = SimDistUtil.getClientPlayer();
      Vec3 baseOrigin = player.getEyePosition();
      Vec3 baseTarget = RaycastHelper.getTraceTarget(player, player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) * 5.0, baseOrigin);
      ChamberEntity closestChamber = null;
      double distance = Double.MAX_VALUE;
      Direction closestDir = null;

      for (ChamberEntity entity : getChambers(player)) {
         AABB toClip = entity.getBoundingBox();
         SubLevel subLevel = Sable.HELPER.getContainingClient(toClip.getCenter());
         Vec3 subLevelOrigin = baseOrigin;
         Vec3 subLevelTarget = baseTarget;
         if (subLevel != null) {
            subLevelOrigin = subLevel.logicalPose().transformPositionInverse(baseOrigin);
            subLevelTarget = subLevel.logicalPose().transformPositionInverse(baseTarget);
         }

         boolean contains = toClip.contains(subLevelOrigin);
         Optional<Vec3> clip;
         if (contains) {
            clip = toClip.clip(subLevelTarget, subLevelOrigin);
         } else {
            clip = toClip.clip(subLevelOrigin, subLevelTarget);
         }

         if (clip.isPresent()) {
            double hitDist = clip.get().distanceToSqr(subLevelOrigin);
            if (hitDist < distance) {
               distance = hitDist;
               closestChamber = entity;
               closestDir = this.getDirectionFromAABBClip(toClip, subLevelOrigin, subLevelTarget, contains);
            }
         }
      }

      this.hoveredFace = closestDir;
      this.hoveredChamber = closestChamber;
   }

   private void renderChambers() {
      if (this.currentState == SealantSelectionHandler.State.UNBOUND) {
         for (ChamberEntity entity : getChambers(SimDistUtil.getClientPlayer())) {
            if (entity != this.hoveredChamber) {
               Outliner.getInstance()
                  .showAABB("sealant:chamberPassive" + entity, entity.getBoundingBox())
                  .colored(ChamberStyle.COLOR)
                  .disableLineNormals()
                  .lineWidth(0.015625F);
            }
         }

         if (this.hoveredChamber != null) {
            Outliner.getInstance()
               .chaseAABB("sealant:chamberActive" + this.hoveredChamber.getId(), this.hoveredChamber.getBoundingBox())
               .colored(ChamberStyle.COLOR)
               .clearTextures()
               .highlightFace(this.hoveredFace)
               .disableLineNormals()
               .lineWidth(0.0625F);
         }
      }
   }

   private boolean checkBBValidity(Player player, ItemStack sealantStack, BlockPos hoveredPos, boolean showText) {
      int color = ChamberStyle.COLOR;
      if (this.currentState == SealantSelectionHandler.State.BINDING) {
         String key = "item.sealant.sealant.confirm";
         DataComponentMap components = sealantStack.getComponents();
         AABB bb = AABB.encapsulatingFullBlocks(this.selectedPos, hoveredPos);
         boolean showDimensions = true;
         if (ChamberSizing.checkBBMax(bb)) {
            key = "item.sealant.sealant.too_far";
            color = SimColors.DISCARDABLE_ORANGE;
         } else if (!components.has(DataComponents.MAX_DAMAGE)) {
            key = "item.sealant.sealant.not_enough";
            showDimensions = false;
            color = SimColors.DISCARDABLE_ORANGE;
         } else if (player.isShiftKeyDown()) {
            key = "item.sealant.sealant.discard";
            showDimensions = false;
            color = SimColors.DISCARDABLE_ORANGE;
         }

         if (showText) {
            String dimensions = getDimensionalText(bb);
            final int statusColor = color;
            player.displayClientMessage(Component.translatable(key).append(showDimensions ? dimensions : ".").withStyle(style -> style.withColor(statusColor)), true);
         }
      }

      return color == ChamberStyle.COLOR;
   }

   public BlockHitResult getHitResult() {
      Player player = SimDistUtil.getClientPlayer();
      Level level = player.level();
      ClipContext clipContext = new ClipContext(
         player.getEyePosition(),
         player.getEyePosition().add(player.getViewVector(SimDistUtil.getPartialTick()).scale(player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE))),
         Block.COLLIDER,
         Fluid.NONE,
         CollisionContext.empty()
      );
      return level.clip(clipContext);
   }

   public Direction getDirectionFromAABBClip(AABB aabb, Vec3 origin, Vec3 end, boolean inside) {
      if (inside) {
         Vec3 temp = origin;
         origin = end;
         end = temp;
      }

      double d = end.x - origin.x;
      double e = end.y - origin.y;
      double f = end.z - origin.z;
      return AABBMixin.invokeGetDirection(aabb, origin, new double[]{1.0}, null, d, e, f);
   }

   @NotNull
   private static String getDimensionalText(AABB bb) {
      return " (" + (int)bb.getXsize() + " × " + (int)bb.getYsize() + " × " + (int)bb.getZsize() + ")";
   }

   @NotNull
   private static List<ChamberEntity> getChambers(Player player) {
      return player.level()
         .getEntities(
            Sealant.CHAMBER.get(),
            player.getBoundingBox().inflate((double)((Integer)SimConfigService.INSTANCE.server().assembly.honeyGlueRange.get()).intValue()),
            e -> true
         );
   }

   @Nullable
   public InteractionHand getSealantHand(Player player) {
      return player.getItemInHand(InteractionHand.MAIN_HAND).is(Sealant.SEALANT.get())
         ? InteractionHand.MAIN_HAND
         : (player.getItemInHand(InteractionHand.OFF_HAND).is(Sealant.SEALANT.get()) ? InteractionHand.OFF_HAND : null);
   }

   public void reset() {
      this.selectedPos = null;
      this.hoveredChamber = null;
      this.hoveredFace = null;
      this.currentState = State.UNBOUND;
   }

   private void clearAndSwing(Player player) {
      reset();
      player.swing(InteractionHand.MAIN_HAND);
   }

   public static enum State {
      UNBOUND,
      BINDING;
   }
}
