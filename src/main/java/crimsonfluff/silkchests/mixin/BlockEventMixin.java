package crimsonfluff.silkchests.mixin;

import crimsonfluff.silkchests.event.BlockBreakEvent;
import crimsonfluff.silkchests.event.BlockPlaceEvent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class BlockEventMixin {
    @Inject(method = "onBreak", at = @At("HEAD"), cancellable = true)
    public void onBlockBreak(World world, BlockPos pos, BlockState state, PlayerEntity player, CallbackInfo ci){
        ActionResult result = BlockBreakEvent.EVENT.invoker().blockBroken(world, pos, state, player);
        if (result == ActionResult.FAIL){
            ci.cancel();
        }
    }
}
