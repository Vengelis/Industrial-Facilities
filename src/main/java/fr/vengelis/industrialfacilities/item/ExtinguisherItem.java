package fr.vengelis.industrialfacilities.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;

import fr.vengelis.industrialfacilities.procedures.ExtinguisherOnPlayerStoppedUsingProcedure;
import fr.vengelis.industrialfacilities.procedures.ExtinguisherItemIsCraftedsmeltedProcedure;
import fr.vengelis.industrialfacilities.procedures.ExtinguisherEveryTickWhileUsingItemProcedure;

public class ExtinguisherItem extends Item {
	public ExtinguisherItem() {
		super(new Item.Properties().stacksTo(1));
	}

	@Override
	public int getUseDuration(ItemStack itemstack, LivingEntity livingEntity) {
		return 72000;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player entity, InteractionHand hand) {
		InteractionResultHolder<ItemStack> ar = super.use(world, entity, hand);
		entity.startUsingItem(hand);
		return ar;
	}

	@Override
	public void onCraftedBy(ItemStack itemstack, Level world, Player entity) {
		super.onCraftedBy(itemstack, world, entity);
		ExtinguisherItemIsCraftedsmeltedProcedure.execute(itemstack);
	}

	@Override
	public void releaseUsing(ItemStack itemstack, Level world, LivingEntity entity, int time) {
		ExtinguisherOnPlayerStoppedUsingProcedure.execute(itemstack);
	}

	@Override
	public void onUseTick(Level world, LivingEntity entity, ItemStack itemstack, int time) {
		ExtinguisherEveryTickWhileUsingItemProcedure.execute(world, entity.getX(), entity.getY(), entity.getZ(), entity, itemstack);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		double tank = stack
				.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
				.copyTag()
				.getDouble("tank");

		tank = Math.max(0.0, Math.min(6400.0, tank));
		int percentage = (int) Math.floor(tank / 64.0);
		tooltip.add(
				Component.translatable(
						"tooltip.industrial_facilities.extinguisher.tank",
						Component.literal(Integer.toString(percentage))
								.withStyle(ChatFormatting.GREEN)
				).withStyle(ChatFormatting.GRAY)
		);
	}
}