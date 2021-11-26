package crimsonfluff.silkchests.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public interface BlockBreakEvent {
    Event<BlockBreakEvent> EVENT = EventFactory.createArrayBacked(BlockBreakEvent.class,
            (listeners) -> (world, pos, state, player)-> {
                for (BlockBreakEvent listener : listeners){
                    ActionResult result = listener.blockBroken(world, pos, state, player);
                    if(result != ActionResult.PASS) {
                        return result;
                    }
                }
                return ActionResult.PASS;
            });
    ActionResult blockBroken(World world, BlockPos pos, BlockState state, PlayerEntity player);
}
