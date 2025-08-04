package de.kybe.autokitmaker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.world.item.ItemStack;
import org.rusherhack.client.api.utils.ChatUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.rusherhack.client.api.Globals.mc;

public class KitManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File BASE_DIR = new File("rusherhack/autokit");

    public static void save(AutoKitInventory inventory, String name) {
        if (!BASE_DIR.exists()) {
            BASE_DIR.mkdirs();
        }

        File file = new File(BASE_DIR, name + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(inventory, writer);
        } catch (IOException e) {
            ChatUtils.print("Failed to save inventory: " + name + " " + e);
        }
    }

    public static List<String> listKits() {
        if (!BASE_DIR.exists()) {
            return Collections.emptyList();
        }

        File[] files = BASE_DIR.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }

        return Arrays.stream(files)
                .map(f -> f.getName().replaceFirst("(?i)\\.json$", ""))
                .sorted()
                .toList();
    }

    public static AutoKitInventory load(String name) {
        File file = new File(BASE_DIR, name + ".json");
        if (!file.exists()) {
            ChatUtils.print("Inventory file not found: " + name);
            return null;
        }

        try (FileReader reader = new FileReader(file)) {
            return GSON.fromJson(reader, AutoKitInventory.class);
        } catch (IOException e) {
            ChatUtils.print("Failed to load inventory: " + name + " " + e);
            return null;
        }
    }

    public static void rename(String originalName, String newName) {
        File originalFile = new File(BASE_DIR, originalName + ".json");
        File newFile = new File(BASE_DIR, newName + ".json");

        if (!originalFile.exists()) {
            ChatUtils.print("Original file does not exist: " + originalName);
            return;
        }

        if (newFile.exists()) {
            ChatUtils.print("Target file already exists: " + newName);
            return;
        }

        boolean success = originalFile.renameTo(newFile);
        if (!success) {
            ChatUtils.print("Failed to rename " + originalName + " to " + newName);
        }
    }

    public static void saveFromInventory(String name) {
        AutoKitInventory inv = getFromInventory();
        if (inv == null) return;
        save(inv, name);
    }

    public static AutoKitInventory getFromInventory() {
        if (mc.player == null) {
            System.out.println("Player is null!");
            return null;
        }

        AutoKitInventory inv = new AutoKitInventory();

        for (int i = 9; i < mc.player.getInventory().items.size(); i++) {
            ItemStack stack = mc.player.getInventory().items.get(i);
            inv.items.set(i - 9, new AutoKitItem(stack));
        }

        return inv;
    }
}