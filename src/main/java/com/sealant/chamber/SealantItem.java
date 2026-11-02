// Copied from Simulated 1.3.2 honey glue (MIT); see THIRD_PARTY_NOTICES.md.
package com.sealant.chamber;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class SealantItem extends Item {
   public SealantItem(Properties properties) {
      super(properties);
   }

   public boolean canAttackBlock(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer) {
      return false;
   }
}
