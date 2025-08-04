package de.kybe.autokitmaker;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.rusherhack.client.api.Globals.mc;

public class Utils {
    public static List<String> itemStackToEnchants(ItemStack stack) {
        Set<Object2IntMap.Entry<Holder<Enchantment>>> entries = stack.getEnchantments().entrySet();

        ArrayList<String> enchants = new ArrayList<>();

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : entries) {
            Holder<Enchantment> holder = entry.getKey();
            enchants.add(holder.getRegisteredName());
        }
        return enchants;
    }

    public static List<ItemStack> getContainerItemsFromStack(ItemStack stack) {
        if (stack.has(DataComponents.CONTAINER)) {
            final ItemContainerContents itemContainerContents = stack.get(DataComponents.CONTAINER);
            if (itemContainerContents == null) return null;
            final NonNullList<ItemStack> stacks = NonNullList.withSize(27, ItemStack.EMPTY);
            itemContainerContents.copyInto(stacks);
            return stacks;
        }

        return Collections.emptyList();
    }

    public static Direction getDirection(BlockPos pos) {
        assert mc.player != null;
        assert mc.level != null;

        Vec3 eyesPos = new Vec3(mc.player.getX(), mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()), mc.player.getZ());
        if ((double) pos.getY() > eyesPos.y) {
            if (mc.level.getBlockState(pos.offset(0, -1, 0)).canBeReplaced()) return Direction.DOWN;
            else return mc.player.getDirection().getOpposite();
        }
        if (!mc.level.getBlockState(pos.offset(0, 1, 0)).canBeReplaced()) return mc.player.getDirection().getOpposite();
        return Direction.UP;
    }

    public static boolean isShulker(ItemStack item) {
        return item.getItem().getDescriptionId().contains("shulker_box");
    }
}

