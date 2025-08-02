package de.kybe.autokitmaker;

import com.google.gson.annotations.SerializedName;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AutoKitItem {
    @SerializedName("item")
    public String item;

    @SerializedName("enchantments")
    public List<String> enchantments;


    @SerializedName("maxStackCount")
    public int maxStackCount;


    @SuppressWarnings("unused")
    public AutoKitItem() {
        this.item = "";
        this.enchantments = new ArrayList<>();
    }

    public AutoKitItem(ItemStack stack) {
        this.item = stack.getItem().getDescriptionId();
        this.enchantments = Utils.itemStackToEnchants(stack);
        this.maxStackCount = stack.getMaxStackSize();
    }

    public AutoKitItem(String item) {
        this.item = item;
        this.enchantments = List.of();
    }

    public boolean matches(ItemStack otherStack, boolean ignoreEnchants) {
        if (otherStack == null) return false;
        if (!this.item.equals(otherStack.getItem().getDescriptionId())) return false;
        if (ignoreEnchants) return true;

        return Set.copyOf(enchantments).equals(Set.copyOf(Utils.itemStackToEnchants(otherStack)));
    }

    public boolean isAir() {
        return this.item.equals("block.minecraft.air");
    }

    @Override
    public String toString() {
        String enchants = (enchantments != null && !enchantments.isEmpty())
                ? String.join(", ", enchantments)
                : "None";
        return "AutoKitItem[item=" + item + ", enchantments=" + enchants + "]";
    }
}