package de.kybe.autokitmaker;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class AutoKitInventory {
    @SerializedName("items")
    public List<AutoKitItem> items;

    public AutoKitInventory() {
        this.items = fill();
    }

    private static List<AutoKitItem> fill() {
        List<AutoKitItem> list = new ArrayList<>(27);
        for (int i = 0; i < 27; i++) {
            list.add(new AutoKitItem("block.minecraft.air"));
        }
        return list;
    }

    public AutoKitItem getSafe(int slot) {
        return (slot >= 0 && slot < items.size()) ? items.get(slot) : null;
    }
}
