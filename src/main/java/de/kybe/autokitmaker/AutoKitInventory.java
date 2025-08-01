package de.kybe.autokitmaker;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class AutoKitInventory {
    private static final int INVENTORY_SIZE = 36;
    private static final int HOTBAR_SIZE = 9;

    @SerializedName("items")
    public List<AutoKitItem> items;

    public AutoKitInventory() {
        this.items = fill("block.minecraft.air", INVENTORY_SIZE - HOTBAR_SIZE);
    }

    private static List<AutoKitItem> fill(String itemId, int count) {
        List<AutoKitItem> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new AutoKitItem(itemId));
        }
        return list;
    }

    public AutoKitItem getSafe(int slot) {
        return (slot >= 0 && slot < items.size()) ? items.get(slot) : null;
    }
}
