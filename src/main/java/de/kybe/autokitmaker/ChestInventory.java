package de.kybe.autokitmaker;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ChestInventory {
    private final List<ItemStack> items = new ArrayList<>();
    private final List<ItemStack> expandedItems = new ArrayList<>();

    private static String normalizeName(@Nullable String name) {
        return name == null ? "" : name;
    }

    private static String getItemCustomName(ItemStack item) {
        return item.getCustomName() == null ? "" : item.getCustomName().getString();
    }

    public void expandItems() {
        expandedItems.clear();
        for (ItemStack item : items) {
            if (AutoKitModule.isShulker(item)) {
                List<ItemStack> inner = Utils.getContainerItemsFromStack(item);
                if (inner != null) {
                    expandedItems.addAll(inner);
                }
            } else {
                expandedItems.add(item);
            }
        }
    }

    public void addItem(ItemStack item) {
        items.add(item);
        expandItems();
    }

    public List<ItemStack> getItems() {
        return Collections.unmodifiableList(expandedItems);
    }

    public void setItems(List<ItemStack> items) {
        this.items.clear();
        this.items.addAll(items);
        this.expandItems();
    }

    public boolean hasItem(ItemStack otherItem) {
        for (ItemStack item : getItems()) {
            if (ItemStack.matches(item, otherItem)) return true;
        }
        return false;
    }

    public boolean hasItem(AutoKitItem otherItem, boolean enchantSensitive, boolean onlySpecificEnchantsMatterToggled, String onlySpecificEnchantsMatter, @Nullable String customName) {
        String targetName = normalizeName(customName);
        for (ItemStack item : getItems()) {
            if (!otherItem.matches(item, !enchantSensitive, onlySpecificEnchantsMatterToggled, onlySpecificEnchantsMatter))
                continue;
            if (getItemCustomName(item).equals(targetName)) return true;
        }
        return false;
    }

    public int getItemCount(AutoKitItem otherItem, boolean enchantSensitive, boolean onlySpecificEnchantsMatterToggled, String onlySpecificEnchantsMatter, @Nullable String customName) {
        int amount = 0;
        String targetName = normalizeName(customName);
        for (ItemStack item : getItems()) {
            if (!otherItem.matches(item, !enchantSensitive, onlySpecificEnchantsMatterToggled, onlySpecificEnchantsMatter))
                continue;
            if (getItemCustomName(item).equals(targetName)) amount += item.getCount();
        }
        return amount;
    }

    public Map<String, Integer> getMatchingItemCountsByName(AutoKitItem otherItem, boolean enchantSensitive, boolean onlySpecificEnchantsMatterToggled, String onlySpecificEnchantsMatter) {
        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack item : getItems()) {
            if (!otherItem.matches(item, !enchantSensitive, onlySpecificEnchantsMatterToggled, onlySpecificEnchantsMatter))
                continue;

            String name = getItemCustomName(item);
            counts.put(name, counts.getOrDefault(name, 0) + item.getCount());
        }
        return counts;
    }
}