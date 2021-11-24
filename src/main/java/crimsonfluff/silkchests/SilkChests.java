package crimsonfluff.silkchests;

import io.netty.buffer.Unpooled;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.BarrelTileEntity;
import net.minecraft.tileentity.ChestTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

@Mod(SilkChests.MOD_ID)
public class SilkChests {
    public static final String MOD_ID = "silkchests";
    public static final int NBT_MAXIMUM = 1048576;

    public SilkChests() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent     // Server-side only
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        ServerWorld world = (ServerWorld) event.getWorld();
        ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
        if (player.isCreative()) return;

        Block block = event.getState().getBlock();
        TileEntity tileEntity = world.getBlockEntity(event.getPos());
        CompoundNBT spawnerDataNBT;

        if (block instanceof ChestBlock && tileEntity instanceof ChestTileEntity)
            spawnerDataNBT = ((ChestTileEntity) tileEntity).save(new CompoundNBT());

        else if (block instanceof BarrelBlock && tileEntity instanceof BarrelTileEntity)
            spawnerDataNBT = ((BarrelTileEntity) tileEntity).save(new CompoundNBT());

        else
            return;

        int lvl = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, player.getMainHandItem());
        if (lvl != 0) {
            ItemStack itemStack = new ItemStack(block);

            spawnerDataNBT.remove("x");
            spawnerDataNBT.remove("y");
            spawnerDataNBT.remove("z");

            itemStack.setTag(spawnerDataNBT);

            Block.popResource(world, event.getPos(), itemStack);
            world.removeBlockEntity(event.getPos());
            world.destroyBlock(event.getPos(), false);

            player.getMainHandItem().hurtAndBreak(1, player, plyr -> player.broadcastBreakEvent(player.getUsedItemHand()));

            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) event.getEntity();
            ItemStack itemStack = player.getItemInHand(player.getUsedItemHand());

            if (! itemStack.hasTag()) return;

            TileEntity tileEntity = event.getWorld().getBlockEntity(event.getPos());
            CompoundNBT spawnerDataNBT;

            if (itemStack.getItem() == Items.CHEST || itemStack.getItem() == Items.TRAPPED_CHEST || itemStack.getItem() == Items.BARREL) {
                spawnerDataNBT = itemStack.getTag();

                spawnerDataNBT.putInt("x", event.getPos().getX());
                spawnerDataNBT.putInt("y", event.getPos().getY());
                spawnerDataNBT.putInt("z", event.getPos().getZ());

                // Note: Keep the redundant cast !
                if (tileEntity instanceof ChestTileEntity)
                    ((ChestTileEntity) tileEntity).load(event.getState(), spawnerDataNBT);

                else if (tileEntity instanceof BarrelTileEntity)
                    ((BarrelTileEntity) tileEntity).load(event.getState(), spawnerDataNBT);

            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void onToolTipEvent(ItemTooltipEvent event) {
        if (event.getPlayer() == null) return;

        ItemStack itemStack = event.getItemStack();
        if (! itemStack.hasTag()) return;

        if (itemStack.getItem() == Items.CHEST || itemStack.getItem() == Items.TRAPPED_CHEST || itemStack.getItem() == Items.BARREL) {
            int nbtLength = getNBTSize(itemStack.getTag());
            int overflow = NBT_MAXIMUM - nbtLength;
            String extra = "";

            // https://www.reddit.com/r/technicalminecraft/comments/anjusp/nbt_data_too_big/
            if (overflow != 0) extra = " (" + overflow + ")";
            if (Screen.hasShiftDown())
                event.getToolTip().add(1, new TranslationTextComponent("tip.silkchests.nbt", nbtLength + " / " + NBT_MAXIMUM + extra).withStyle(nbtLength <= NBT_MAXIMUM ? TextFormatting.GREEN : TextFormatting.RED));
            else
                event.getToolTip().add(1, new TranslationTextComponent("tip.silkchests.nbt", nbtLength + extra).withStyle(nbtLength <= NBT_MAXIMUM ? TextFormatting.GREEN : TextFormatting.RED));

            // from ShulkerBoxBlock.appendHoverText
            if (itemStack.getTag().contains("LootTable", 8))
                event.getToolTip().add(new StringTextComponent("???????"));

            if (itemStack.getTag().contains("Items", 9)) {
                NonNullList<ItemStack> nonNullList = NonNullList.withSize(27, ItemStack.EMPTY);
                ItemStackHelper.loadAllItems(itemStack.getTag(), nonNullList);
                int i = 0;
                int j = 0;

                for(ItemStack itemstack : nonNullList) {
                    if (! itemstack.isEmpty()) {
                        ++j;
                        if (i <= 4) {
                            ++i;
                            IFormattableTextComponent iFormattableTextComponent = itemstack.getHoverName().copy();
                            iFormattableTextComponent.append(" x").append(String.valueOf(itemstack.getCount()));
                            event.getToolTip().add(iFormattableTextComponent);
                        }
                    }
                }

                if (j - i > 0)
                    event.getToolTip().add(new TranslationTextComponent("container.shulkerBox.more", j - i).withStyle(TextFormatting.ITALIC));
            }
        }
    }

    // Dank Storage @ DarkHax
    // License: Public Domain
    // saves on the processing that getTag().toString() does
    private int getNBTSize(@Nullable CompoundNBT nbt) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        buffer.writeNbt(nbt);
        buffer.release();
        return buffer.writerIndex();
    }
}
