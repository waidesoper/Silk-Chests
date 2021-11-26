package crimsonfluff.silkytouch;

import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
        ServerLevel world = (ServerLevel) event.getWorld();
        ServerPlayer player = (ServerPlayer) event.getPlayer();
        if (player.isCreative()) return;

        Block block = event.getState().getBlock();
        BlockEntity tileEntity = world.getBlockEntity(event.getPos());
        if (tileEntity == null) return;

        int lvl = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, player.getMainHandItem());
        if (lvl != 0) {
            ItemStack itemStack = new ItemStack(block);

            CompoundTag nbtTileEntity = tileEntity.save(new CompoundTag());
            nbtTileEntity.remove("x");
            nbtTileEntity.remove("y");
            nbtTileEntity.remove("z");

            CompoundTag nbtState = NbtUtils.writeBlockState(event.getState());
            nbtState.getCompound("Properties").remove("facing");
            nbtState.getCompound("Properties").remove("waterlogged");
            nbtTileEntity.put("BlockState", nbtState);

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
        if (event.getEntity() instanceof ServerPlayer player) {
            ItemStack itemStack = player.getItemInHand(player.getUsedItemHand());

            if (! itemStack.hasTag()) return;

            BlockEntity tileEntity = event.getWorld().getBlockEntity(event.getPos());
            if (tileEntity == null) return;

            CompoundTag nbtTileEntity = itemStack.getTag();
            nbtTileEntity.putInt("x", event.getPos().getX());
            nbtTileEntity.putInt("y", event.getPos().getY());
            nbtTileEntity.putInt("z", event.getPos().getZ());

            // NBT Way
            CompoundTag oldState = NbtUtils.writeBlockState(event.getState());
            if (oldState.getCompound("Properties").contains("facing")) {
                nbtTileEntity.getCompound("BlockState").getCompound("Properties").putString("facing", oldState.getCompound("Properties").getString("facing"));
            }

            BlockState state = NbtUtils.readBlockState(nbtTileEntity.getCompound("BlockState"));
            tileEntity.load(nbtTileEntity);
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
            event.getToolTip().add(1, new TranslatableComponent("tip.silkytouch.nbt", nbtLength + " / " + NBT_MAXIMUM + extra).withStyle(nbtLength <= NBT_MAXIMUM ? ChatFormatting.GREEN : ChatFormatting.RED));
        else
            event.getToolTip().add(1, new TranslatableComponent("tip.silkytouch.nbt", nbtLength + extra).withStyle(nbtLength <= NBT_MAXIMUM ? ChatFormatting.GREEN : ChatFormatting.RED));

        // from ShulkerBoxBlock.appendHoverText
        if (itemStack.getTag().contains("LootTable", 8))
            event.getToolTip().add(new TextComponent("???????"));

        if (itemStack.getTag().contains("Items", 9)) {
            ListTag listnbt = itemStack.getTag().getList("Items", 10);
            int listsize = Integer.min(5, listnbt.size());

            for(int i = 0; i < listsize; ++i) {
                CompoundTag compoundnbt = listnbt.getCompound(i);
                ItemStack item = ItemStack.of(compoundnbt);

                MutableComponent mutableComponent = item.getHoverName().copy();
                mutableComponent.append(" x").append(String.valueOf(item.getCount()));
                event.getToolTip().add(mutableComponent);
            }

            if (listnbt.size() > 5)
                event.getToolTip().add(new TranslatableComponent("container.shulkerBox.more", listnbt.size() - 5).withStyle(ChatFormatting.ITALIC));
        }
    }

    // Dank Storage @ DarkHax
    // License: Public Domain
    // saves on the processing that getTag().toString() does
    private int getNBTSize(@Nullable CompoundTag nbt) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeNbt(nbt);
        buffer.release();
        return buffer.writerIndex();
    }
}
