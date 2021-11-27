package crimsonfluff.silkchests;

import crimsonfluff.silkchests.event.BlockBreakEvent;
import crimsonfluff.silkchests.event.BlockPlaceEvent;
import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtNull;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public class Silkchests implements ModInitializer {
    @Override
    public void onInitialize() {
        BlockBreakEvent.EVENT.register(((world, pos, state, player) -> {
            if(player.isCreative()) return ActionResult.PASS;

            Block block = state.getBlock();
            BlockEntity tileEntity = world.getBlockEntity(pos);
            if(tileEntity == null) return ActionResult.PASS;

            int lvl = EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, player.getMainHandStack());
            if(lvl != 0){
                ItemStack itemStack = new ItemStack(block);

                NbtCompound nbtTileEntity = tileEntity.writeNbt(new NbtCompound());
                nbtTileEntity.remove("x");
                nbtTileEntity.remove("y");
                nbtTileEntity.remove("z");

                NbtCompound nbtState = NbtHelper.fromBlockState(state);
                nbtState.getCompound("Properties").remove("facing");
                nbtState.getCompound("Properties").remove("waterlogged");
                nbtTileEntity.put("Blockstate", nbtState);

                itemStack.setTag(nbtTileEntity);

                Block.dropStack(world, pos, itemStack);
                world.removeBlockEntity(pos);
                world.removeBlock(pos,false);

                player.getMainHandStack().damage(1, player, plyr -> player.sendToolBreakStatus(player.getActiveHand()));

                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        }));

        BlockPlaceEvent.EVENT.register((world, pos, state, placer, itemStack) -> {
            if(placer instanceof ServerPlayerEntity){
                ServerPlayerEntity player = (ServerPlayerEntity) placer;
                if(!itemStack.hasTag()) return ActionResult.PASS;

                BlockEntity tileEntity = world.getBlockEntity(pos);
                if(tileEntity == null) return ActionResult.PASS;

                NbtCompound nbtTileEntity = itemStack.getTag();
                nbtTileEntity.putInt("x", pos.getX());
                nbtTileEntity.putInt("y", pos.getY());
                nbtTileEntity.putInt("z", pos.getZ());

                NbtCompound oldState = NbtHelper.fromBlockState(state);
                if(oldState.getCompound("Properties").contains("facing")) {
                    nbtTileEntity.getCompound("BlockState").getCompound("Properties").putString("facing", oldState.getCompound("Properties").getString("facing"));
                }

                BlockState newState = NbtHelper.toBlockState(nbtTileEntity.getCompound("BlockState"));
                tileEntity.fromTag(newState, nbtTileEntity);
                //world.setBlockState(pos, newState, 11);
            }
            return ActionResult.PASS;
        });
    }
}
