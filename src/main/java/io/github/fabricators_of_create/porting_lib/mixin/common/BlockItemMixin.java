package io.github.fabricators_of_create.porting_lib.mixin.common;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.fabricators_of_create.porting_lib.event.common.BlockPlaceCallback;
import io.github.fabricators_of_create.porting_lib.extensions.BlockItemExtensions;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin implements BlockItemExtensions {
	@Shadow
	public abstract Block getBlock();

	@Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
	private void port_lib$useOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		InteractionResult result = BlockPlaceCallback.EVENT.invoker().onBlockPlace(new BlockPlaceContext(context));
		if (result != InteractionResult.PASS) {
			cir.setReturnValue(result);
		}
	}
}
