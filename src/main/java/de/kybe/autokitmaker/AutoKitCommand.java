package de.kybe.autokitmaker;

import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.rusherhack.client.api.feature.command.Command;
import org.rusherhack.client.api.utils.ChatUtils;
import org.rusherhack.core.command.annotations.CommandExecutor;

import java.io.File;

public class AutoKitCommand extends Command {
    public AutoKitCommand() {
        super("auto-kit", "Auto Kit Command");
    }

    @CommandExecutor(subCommand = "add-kit")
    @CommandExecutor.Argument("string")
    private void add(String name) {
        if (name == null || name.isEmpty()) {
            ChatUtils.print("Please provide a valid kit name.");
            return;
        }
        KitManager.saveFromInventory(name);
        ChatUtils.print("Saved current inventory as kit: " + name);
    }

    @CommandExecutor(subCommand = "resultChest")
    private String depositChest() {
        if (mc.hitResult == null) return "No HitResult";
        if (mc.player == null) return "No Player!";
        HitResult.Type hitResultType = mc.hitResult.getType();
        if (hitResultType != HitResult.Type.BLOCK) return "HitResult isn't a Block";
        AutoKitModule.INSTANCE.resultLocation = ((BlockHitResult) mc.hitResult).getBlockPos();
        return "success";
    }

    @CommandExecutor(subCommand = "shulkerChest")
    private String shulkerChest() {
        if (mc.hitResult == null) return "No HitResult";
        if (mc.player == null) return "No Player!";
        HitResult.Type hitResultType = mc.hitResult.getType();
        if (hitResultType != HitResult.Type.BLOCK) return "HitResult isn't a Block";
        AutoKitModule.INSTANCE.emptyShulkerLocation = ((BlockHitResult) mc.hitResult).getBlockPos();
        return "success";
    }

    @CommandExecutor(subCommand = "placeLocation")
    private String placeLocation() {
        if (mc.hitResult == null) return "No HitResult";
        if (mc.player == null) return "No Player!";
        HitResult.Type hitResultType = mc.hitResult.getType();
        if (hitResultType != HitResult.Type.BLOCK) return "HitResult isn't a Block";
        AutoKitModule.INSTANCE.placeLocation = ((BlockHitResult) mc.hitResult).getBlockPos().above(1);
        return "success";
    }

    @CommandExecutor(subCommand = "remove")
    @CommandExecutor.Argument("string")
    private String remove(String name) {
        if (name == null || name.isEmpty()) {
            return "Please provide a valid kit name to remove.";
        }
        File kitFile = new File("rusherhack/autokit", name + ".json");
        if (!kitFile.exists()) {
            return "No kit found with name: " + name;
        }
        if (kitFile.delete()) {
            return "Kit removed: " + name;
        } else {
            return "Failed to remove kit: " + name;
        }
    }

    @CommandExecutor(subCommand = "rename")
    @CommandExecutor.Argument({"string", "string"})
    private String rename(String from, String to) {
        if (from == null || from.isEmpty() || to == null || to.isEmpty()) {
            return "Please provide both original and new kit names.";
        }
        KitManager.rename(from, to);
        return"Renamed kit from '" + from + "' to '" + to + "'.";
    }

    @CommandExecutor(subCommand = "clear-chests")
    private String clearChests() {
        AutoKitModule.INSTANCE.chestStoreManager.clearChests();
        return "Cleared Chests";
    }

    @CommandExecutor(subCommand = "list")
    private String list() {
        return String.join(", ", KitManager.listKits());
    }

    @CommandExecutor(subCommand = "reset-state")
    private String resetCmd() {
        AutoKitModule.INSTANCE.state = AutoKitModule.State.Steal;
        AutoKitModule.INSTANCE.shulkerPlacedToTakeItems = false;
        AutoKitModule.INSTANCE.shulkerPlacedToTakeItemsOrigin = null;
        return "reset";
    }
}