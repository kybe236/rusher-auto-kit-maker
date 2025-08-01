package de.kybe.autokitmaker;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.rusherhack.client.api.utils.ChatUtils;

import java.util.*;

public class ChestStoreManager {
    private final List<Chest> chests = new ArrayList<>();

    public void addChest(Chest chest) {
        chests.add(chest);
    }

    public void clearChests() {
        chests.clear();
    }

    public List<BlockPos> getChests() {
        ArrayList<BlockPos> chests = new ArrayList<>();
        for (Chest chest : this.chests) {
            chests.addAll(chest.getPositions());
        }
        return chests;
    }

    public Optional<Chest> getChestContaining(AutoKitItem item, boolean enchantSensitive, @Nullable String customName) {
        for (Chest chest: chests) {
            if (chest.inv.hasItem(item, enchantSensitive, customName)) return Optional.of(chest);
        }
        return Optional.empty();
    }

    public Optional<Chest> getChestContaining(
            AutoKitItem item,
            boolean enchantSensitive,
            @Nullable String customName,
            int minQuantity
    ) {
        for (Chest chest : chests) {
            int count = chest.inv.getItemCount(item, enchantSensitive, customName);
            if (count >= minQuantity) {
                return Optional.of(chest);
            }
        }
        return Optional.empty();
    }

    public Map<String, Integer> getMatchingItemCountsByNameAcrossAllChests(AutoKitItem otherItem, boolean enchantSensitive) {
        Map<String, Integer> total = new HashMap<>();
        for (Chest chest : chests) {
            Map<String, Integer> chestCounts = chest.inv.getMatchingItemCountsByName(otherItem, enchantSensitive);
            chestCounts.forEach((name, count) -> total.merge(name, count, Integer::sum));
        }
        return total;
    }

    public Chest getChest(
            BlockPos pos
    ) {
        for (Chest chest : chests) {
            for (BlockPos p : chest.getPositions()) {
                if (p.equals(pos)) {
                    return chest;
                }
            }
        }
        return null;
    }

    public Optional<List<BlockPos>> getChest(
            AutoKitItem item,
            boolean enchantSensitive,
            int minCount
    ) {
        Map<String, Integer> counts = getMatchingItemCountsByNameAcrossAllChests(item, enchantSensitive);

        List<Map.Entry<String, Integer>> candidates = new ArrayList<>(counts.entrySet());
        candidates.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        for (Map.Entry<String, Integer> entry : candidates) {
            String customName = entry.getKey();
            int available = entry.getValue();
            if (available < minCount) {
                continue;
            }

            List<BlockPos> collected = new ArrayList<>();
            int remaining = minCount;

            for (Chest chest : chests) {
                int inThisChest = chest.inv.getItemCount(item, enchantSensitive, customName);
                if (inThisChest <= 0) {
                    continue;
                }


                collected.addAll(chest.getPositions());
                remaining -= inThisChest;

                if (remaining <= 0) {
                    return Optional.of(collected);
                }
            }

        }
        return Optional.empty();
    }

    public boolean hasTotalQuantity(ItemStack target, int totalRequired, @Nullable ChestInventory extra) {
        AutoKitItem item = new AutoKitItem(target);
        int found = 0;
        String customName = target.getCustomName() == null ? null : target.getCustomName().getString();

        // Helper to accumulate from any ChestInventory
        found += countFromInventory(extra, item, customName, totalRequired, "extra");
        if (found >= totalRequired) return true;

        for (Chest chest : chests) {
            found += countFromInventory(chest.inv, item, customName, totalRequired, "chest");
            if (found >= totalRequired) return true;
        }

        return false;
    }

    private int countFromInventory(ChestInventory inv, AutoKitItem item, String customName, int totalRequired, String sourceTag) {
        if (inv == null) return 0;
        int count = inv.getItemCount(item, false, customName);
        ChatUtils.print("FOUND (so far): " + count + " REQUIRED: " + totalRequired + " FROM: " + sourceTag);
        return count;
    }

    public Optional<Chest> getChestContaining(ItemStack item) {
        for (Chest chest: chests) {
            if (chest.inv.hasItem(item)) return Optional.of(chest);
        }
        return Optional.empty();
    }
}