package com.sealant.debug;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Common-side inert tool; the client opens its Create UI via interaction events. */
public final class ChamberInspectorItem extends Item {
    public ChamberInspectorItem(){super(new Properties().stacksTo(1));}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){return InteractionResultHolder.success(player.getItemInHand(hand));}
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){return InteractionResult.SUCCESS;}
    @Override public boolean canAttackBlock(BlockState state,Level level,BlockPos pos,Player player){return false;}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){tooltip.add(Component.translatable("item.sealant.chamber_inspector.description"));}
}
