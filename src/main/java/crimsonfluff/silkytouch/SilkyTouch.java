package crimsonfluff.silkytouch;

import io.netty.buffer.Unpooled;
import net.minecraft.block.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

@Mod(SilkyTouch.MOD_ID)
public class SilkyTouch {
    public static final String MOD_ID = "silkytouch";
    public static final int NBT_MAXIMUM = 1048576;

    public SilkyTouch() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent     // Server-side only
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        ServerWorld world = (ServerWorld) event.getWorld();
        ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
        if (player.isCreative()) return;

        Block block = event.getState().getBlock();
        TileEntity tileEntity = world.getBlockEntity(event.getPos());
        if (tileEntity == null) return;

        int lvl = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, player.getMainHandItem());
        if (lvl != 0) {
            ItemStack itemStack = new ItemStack(block);

            CompoundNBT nbtTileEntity = tileEntity.save(new CompoundNBT());
            nbtTileEntity.remove("x");
            nbtTileEntity.remove("y");
            nbtTileEntity.remove("z");

            CompoundNBT nbtState = NBTUtil.writeBlockState(event.getState());
            nbtState.getCompound("Properties").remove("facing");
            nbtState.getCompound("Properties").remove("waterlogged");
            nbtTileEntity.put("BlockState", nbtState);
//            player.displayClientMessage(new StringTextComponent("NBTSTATE: " + nbtState), false);

            itemStack.setTag(nbtTileEntity);

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
            if (tileEntity == null) return;

            CompoundNBT nbtTileEntity = itemStack.getTag();
            nbtTileEntity.putInt("x", event.getPos().getX());
            nbtTileEntity.putInt("y", event.getPos().getY());
            nbtTileEntity.putInt("z", event.getPos().getZ());

            // better way ??
//            BlockState state2 = NBTUtil.readBlockState(nbtTileEntity.getCompound("BlockState"));
//
//            event.getState().getValues().forEach((property, comparable) -> {
//                if (property.getName().equals("facing")) {
//                    Optional<?> optional = property.getValue(property.getName());
//                    if (optional.isPresent()) {
//                        state2.setValue(property, optional.get());
//                        player.displayClientMessage(new StringTextComponent("Found Property: " + optional.get()), false);
//                    }
//                }
//            });

            // NBT Way
            CompoundNBT oldState = NBTUtil.writeBlockState(event.getState());
            if (oldState.getCompound("Properties").contains("facing")) {
                nbtTileEntity.getCompound("BlockState").getCompound("Properties").putString("facing", oldState.getCompound("Properties").getString("facing"));
            }

            BlockState state = NBTUtil.readBlockState(nbtTileEntity.getCompound("BlockState"));
            tileEntity.load(state, nbtTileEntity);
            event.getWorld().setBlock(event.getPos(), state, Constants.BlockFlags.DEFAULT_AND_RERENDER);
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void onToolTipEvent(ItemTooltipEvent event) {
        if (event.getPlayer() == null) return;

        ItemStack itemStack = event.getItemStack();
        if (! itemStack.hasTag()) return;

        int nbtLength = getNBTSize(itemStack.getTag());
        int overflow = NBT_MAXIMUM - nbtLength;
        String extra = "";

        // https://www.reddit.com/r/technicalminecraft/comments/anjusp/nbt_data_too_big/
        if (overflow != 0) extra = " (" + overflow + ")";
        if (Screen.hasShiftDown())
            event.getToolTip().add(1, new TranslationTextComponent("tip.silkytouch.nbt", nbtLength + " / " + NBT_MAXIMUM + extra).withStyle(nbtLength <= NBT_MAXIMUM ? TextFormatting.GREEN : TextFormatting.RED));
        else
            event.getToolTip().add(1, new TranslationTextComponent("tip.silkytouch.nbt", nbtLength + extra).withStyle(nbtLength <= NBT_MAXIMUM ? TextFormatting.GREEN : TextFormatting.RED));

        // from ShulkerBoxBlock.appendHoverText
        if (itemStack.getTag().contains("LootTable", 8))
            event.getToolTip().add(new StringTextComponent("???????"));

        if (itemStack.getTag().contains("Items", 9)) {
            ListNBT listnbt = itemStack.getTag().getList("Items", 10);
            int listsize = Integer.min(5, listnbt.size());

            for(int i = 0; i < listsize; ++i) {
                CompoundNBT compoundnbt = listnbt.getCompound(i);
                ItemStack item = ItemStack.of(compoundnbt);

                IFormattableTextComponent iFormattableTextComponent = item.getHoverName().copy();
                iFormattableTextComponent.append(" x").append(String.valueOf(item.getCount()));
                event.getToolTip().add(iFormattableTextComponent);
            }

            if (listnbt.size() > 5)
                event.getToolTip().add(new TranslationTextComponent("container.shulkerBox.more", listnbt.size() - 5).withStyle(TextFormatting.ITALIC));
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
