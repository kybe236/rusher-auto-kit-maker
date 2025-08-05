package de.kybe.autokitmaker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.events.client.EventUpdate;
import org.rusherhack.client.api.events.network.EventPacket;
import org.rusherhack.client.api.events.render.EventRender3D;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.render.IRenderer3D;
import org.rusherhack.client.api.setting.ColorSetting;
import org.rusherhack.client.api.utils.ChatUtils;
import org.rusherhack.core.event.stage.Stage;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.NumberSetting;
import org.rusherhack.core.setting.StringSetting;
import org.rusherhack.core.utils.ColorUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AutoKitModule extends ToggleableModule {
    public static AutoKitModule INSTANCE;

    private final StringSetting currentKit = new StringSetting("Kit", "");
    private final NumberSetting<Integer> inventoryMoveDelay = new NumberSetting<>("Inventory Action Delay", 5, 0, 20);

    private final BooleanSetting addChests = new BooleanSetting("Add Chests", true);
    private final BooleanSetting autoClose = new BooleanSetting("Auto Close", true);

    private final BooleanSetting active = new BooleanSetting("Active", false);

    private final BooleanSetting enchantSensitive = new BooleanSetting("EnchantSensitive", false);
    private final BooleanSetting onlySpecificEnchantsMatterToggled = new BooleanSetting("Only Specific Enchants Matter Toggled", "Makes it so it only checks if the given enchant matches", true);
    private final StringSetting onlySpecificEnchantsMatter = new StringSetting("Only Specific Enchants that matter", "minecraft:fortune,minecraft:silk_touch");
    private final NumberSetting<Integer> afterOpenDelay = new NumberSetting<>("After Open Delay", 5, 0, 20);

    private final BooleanSetting grimFix = new BooleanSetting("Grim Fix", true);

    private final ColorSetting storageColor = new ColorSetting("Sotrage Color", Color.BLACK);
    private final ColorSetting placeColor = new ColorSetting("Place Pos Color", Color.RED);
    private final ColorSetting shulkerColor = new ColorSetting("Shulker Chest Color", Color.GREEN);
    private final ColorSetting resultColor = new ColorSetting("Result Chest Color", Color.YELLOW);
    private final ColorSetting placedColor = new ColorSetting("Placed Shulker Color", Color.ORANGE);

    private final BooleanSetting debug = new BooleanSetting("debug", false).setHidden(true);

    public BlockPos placeLocation = null;
    public BlockPos resultLocation = null;
    public BlockPos emptyShulkerLocation = null;
    public BlockPos shulkerPlacedToTakeItemsOrigin = null;
    public boolean shulkerPlacedToTakeItems = false;
    public ChestStoreManager chestStoreManager = new ChestStoreManager();
    public State state = State.Steal;
    private BlockPos openChest = null;
    private boolean fullChest = false;
    private int ticks = 0;

    public AutoKitModule() {
        super("AutoKitMaker", ModuleCategory.CLIENT);
        this.registerSettings(
                currentKit,
                addChests,
                autoClose,
                active,
                inventoryMoveDelay,
                grimFix,
                afterOpenDelay,
                enchantSensitive,
                onlySpecificEnchantsMatterToggled,
                onlySpecificEnchantsMatter,
                debug
        );
        INSTANCE = this;
    }

    @Subscribe
    @SuppressWarnings("unused")
    private void onPacketSend(EventPacket.Send event) {
        if (event.getPacket() instanceof ServerboundUseItemOnPacket pkt) {
            if (mc.level == null) return;
            BlockPos pos = pkt.getHitResult().getBlockPos();
            if (mc.level.getBlockState(pos).getBlock() instanceof ChestBlock
                    || mc.level.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock) {
                openChest = pos;
                fullChest = false;
            }
        } else if (event.getPacket() instanceof ServerboundContainerClosePacket) {
            fullChest = false;
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(stage = Stage.PRE)
    private void onPacketReceive(EventPacket.Receive event) {
        if (event.getPacket() instanceof ClientboundContainerClosePacket) {
            fullChest = false;
        } else if (event.getPacket() instanceof ClientboundContainerSetContentPacket packet) {
            if (packet.getContainerId() != 0 && packet.getStateId() == 1) fullChest = true;
        } else if (event.getPacket() instanceof ClientboundContainerSetContentPacket packet) {
            if (mc.player == null) return;
            if (grimFix.getValue() && packet.getStateId() != 1 && packet.getContainerId() == mc.player.containerMenu.containerId)
                event.setCancelled(true);
        } else if (event.getPacket() instanceof ClientboundOpenScreenPacket) {
            ticks = afterOpenDelay.getValue();
        }
    }

    @Subscribe
    @SuppressWarnings({"unused", "DuplicatedCode"})
    private void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.gameMode == null || mc.level == null)
            return;

        if (mc.player.containerMenu instanceof ChestMenu) {
            Chest chest = chestStoreManager.getChest(openChest);
            ChestInventory items = ChestInventory.getChestInventoryFromScreen();
            if (items != null && chest != null)
                chest.inv.setItems(ChestInventory.getChestInventoryFromScreen().getItems());
        }

        if (addChests.getValue() && !active.getValue()) {
            if (openChest == null || !fullChest) return;
            if (!(mc.player.containerMenu instanceof ChestMenu)) return;
            BlockState blockState = mc.level.getBlockState(openChest);
            if (blockState.getBlock() instanceof ChestBlock cb) {
                if (blockState.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
                    Chest chest = new Chest(ChestInventory.getChestInventoryFromScreen(), openChest);
                    if (chestStoreManager.getChest(openChest) != null) {
                        if (autoClose.getValue() && mc.player.hasContainerOpen()) mc.player.closeContainer();
                        return;
                    }
                    chestStoreManager.addChest(chest);
                } else {
                    Chest chest = new Chest(
                            ChestInventory.getChestInventoryFromScreen(),
                            openChest,
                            openChest.relative(ChestBlock.getConnectedDirection(blockState))
                    );
                    if (chestStoreManager.getChest(openChest) != null) {
                        if (autoClose.getValue() && mc.player.hasContainerOpen()) mc.player.closeContainer();
                        return;
                    }
                    chestStoreManager.addChest(chest);
                }
            } else if (blockState.getBlock() instanceof BarrelBlock) {
                Chest chest = new Chest(ChestInventory.getChestInventoryFromScreen(), openChest);
                if (chestStoreManager.getChest(openChest) != null) {
                    if (autoClose.getValue() && mc.player.hasContainerOpen()) mc.player.closeContainer();
                    return;
                }
                chestStoreManager.addChest(chest);
            }
            if (autoClose.getValue() && mc.player.hasContainerOpen()) mc.player.closeContainer();
            return;
        }

        if (!active.getValue()) return;

        if (currentKit.getValue().isEmpty()) {
            ChatUtils.print("no kit selected");
            active.setValue(false);
            return;
        }

        if (emptyShulkerLocation == null) {
            ChatUtils.print("Please use *auto-kit shulkerchest while looking at the chest giving empty shulkers to set the location");
            active.setValue(false);
            return;
        }

        if (placeLocation == null) {
            ChatUtils.print("Please use *auto-kit placelocation while looking at the block below where the shulkers should be placed temporarily");
            active.setValue(false);
            return;
        }

        AutoKitInventory inv = KitManager.load(currentKit.getValue());
        if (inv == null) {
            ChatUtils.print("Inv Not Found");
            active.setValue(false);
            return;
        }

        if (ticks > 0) {
            ticks--;
            return;
        }

        if (debug.getValue()) ChatUtils.print(state.toString());
        switch (state) {
            case Steal -> {
                switch (mc.player.containerMenu) {
                    case ChestMenu menu -> {
                        if (!fullChest) return;
                        int size = menu.getContainer().getContainerSize();
                        int id = menu.containerId;
                        if (openChest == null) {
                            if (debug.getValue()) ChatUtils.print("openChest == null");
                            return;
                        }

                        for (int invSlot = size; invSlot < menu.slots.size() - 9; invSlot++) {
                            ItemStack item = menu.slots.get(invSlot).getItem();
                            int slot = invSlot - size;
                            if (inv.getSafe(slot) == null || inv.getSafe(slot).isAir()) continue;
                            if (inv.getSafe(slot).matches(item, !enchantSensitive.getValue(), onlySpecificEnchantsMatterToggled.getValue(), onlySpecificEnchantsMatter.getValue())
                                    && item.getMaxStackSize() == item.getCount())
                                continue;

                            for (int innerInvSlot = size; innerInvSlot < menu.slots.size() - 9; innerInvSlot++) {
                                if (innerInvSlot == invSlot) continue;
                                ItemStack innerItem = menu.slots.get(innerInvSlot).getItem();
                                if (innerItem.isEmpty()) continue;
                                int innerSlot = innerInvSlot - size;
                                if (!inv.getSafe(slot).matches(innerItem, !enchantSensitive.getValue(), onlySpecificEnchantsMatterToggled.getValue(), onlySpecificEnchantsMatter.getValue()))
                                    continue;
                                if (inv.getSafe(innerSlot).matches(innerItem, !enchantSensitive.getValue(), onlySpecificEnchantsMatterToggled.getValue(), onlySpecificEnchantsMatter.getValue()))
                                    continue;
                                if (!ItemStack.isSameItemSameComponents(item, innerItem) && !item.isEmpty()) continue;

                                mc.gameMode.handleInventoryMouseClick(id, innerInvSlot, 0, ClickType.PICKUP, mc.player);
                                mc.gameMode.handleInventoryMouseClick(id, invSlot, 0, ClickType.PICKUP, mc.player);
                                mc.gameMode.handleInventoryMouseClick(id, innerInvSlot, 0, ClickType.PICKUP, mc.player);
                                ticks = inventoryMoveDelay.getValue();
                                return;
                            }

                            for (int upperSlot = 0; upperSlot < size; upperSlot++) {
                                ItemStack slotItem = menu.slots.get(upperSlot).getItem();
                                if (slotItem.isEmpty()) continue;
                                if (!inv.getSafe(slot).matches(slotItem, !enchantSensitive.getValue(), onlySpecificEnchantsMatterToggled.getValue(), onlySpecificEnchantsMatter.getValue()))
                                    continue;
                                if (!ItemStack.isSameItemSameComponents(item, slotItem) && !item.isEmpty()) continue;

                                int needed = slotItem.getMaxStackSize();
                                if (!item.isEmpty()) needed -= item.getCount();

                                if (chestStoreManager.hasNotTotalQuantity(slotItem, needed, null, enchantSensitive.getValue(), onlySpecificEnchantsMatterToggled.getValue(), onlySpecificEnchantsMatter.getValue())) {
                                    if (debug.getValue())
                                        ChatUtils.print("Not enough items in chests to take a full stack of " + inv.getSafe(slot).item);
                                    continue;
                                }

                                mc.gameMode.handleInventoryMouseClick(id, upperSlot, 0, ClickType.PICKUP, mc.player);
                                mc.gameMode.handleInventoryMouseClick(id, invSlot, 0, ClickType.PICKUP, mc.player);
                                mc.gameMode.handleInventoryMouseClick(id, upperSlot, 0, ClickType.PICKUP, mc.player);
                                ticks = inventoryMoveDelay.getValue();
                                return;
                            }

                            for (int upperSlot = 0; upperSlot < size; upperSlot++) {
                                if (!Utils.isShulker(menu.slots.get(upperSlot).getItem())) continue;
                                List<ItemStack> items = Utils.getContainerItemsFromStack(menu.slots.get(upperSlot).getItem());
                                assert items != null;
                                for (ItemStack item1 : items) {
                                    if (item1.isEmpty()) continue;
                                    if (!inv.getSafe(slot).matches(item1, !enchantSensitive.getValue(), onlySpecificEnchantsMatterToggled.getValue(), onlySpecificEnchantsMatter.getValue()))
                                        continue;
                                    if (!ItemStack.isSameItemSameComponents(item, item1) && !item.isEmpty()) continue;

                                    int needed = item1.getMaxStackSize();
                                    if (!item.isEmpty()) needed -= item.getCount();

                                    if (chestStoreManager.hasNotTotalQuantity(item1, needed, null, enchantSensitive.getValue(), onlySpecificEnchantsMatterToggled.getValue(), onlySpecificEnchantsMatter.getValue())) {
                                        if (debug.getValue())
                                            ChatUtils.print("Not enough items in chests to take a full stack of " + inv.getSafe(slot).item);
                                        continue;
                                    }

                                    mc.gameMode.handleInventoryMouseClick(id, upperSlot, 0, ClickType.PICKUP, mc.player);
                                    mc.gameMode.handleInventoryMouseClick(id, menu.slots.size() - 9 + 1, 0, ClickType.PICKUP, mc.player);
                                    ticks = inventoryMoveDelay.getValue();
                                    shulkerPlacedToTakeItems = true;
                                    shulkerPlacedToTakeItemsOrigin = openChest;
                                    state = State.PlaceShulker;
                                    if (mc.player.hasContainerOpen()) mc.player.closeContainer();
                                    return;
                                }
                            }
                        }
                    }
                    case ShulkerBoxMenu menu -> {
                        if (!fullChest) return;
                        int size = 27;
                        int id = menu.containerId;

                        for (int invSlot = size; invSlot < menu.slots.size() - 9; invSlot++) {
                            ItemStack item = menu.slots.get(invSlot).getItem();
                            int slot = invSlot - size;
                            if (inv.getSafe(slot) == null || inv.getSafe(slot).isAir()) continue;
                            if (inv.getSafe(slot).matches(item, !enchantSensitive.getValue(), onlySpecificEnchantsMatterToggled.getValue(), onlySpecificEnchantsMatter.getValue())
                                    && item.getMaxStackSize() == item.getCount())
                                continue;

                            ArrayList<ItemStack> extraList = new ArrayList<>();
                            for (int innerinnerInvSlot = size; innerinnerInvSlot < menu.slots.size() - 9; innerinnerInvSlot++) {
                                extraList.add(menu.slots.get(innerinnerInvSlot).getItem());
                            }

                            ChestInventory extra = new ChestInventory();
                            extra.setItems(extraList);

                            for (int innerInvSlot = size; innerInvSlot < menu.slots.size() - 9; innerInvSlot++) {
                                ItemStack innerItem = menu.slots.get(innerInvSlot).getItem();
                                if (innerItem.isEmpty()) continue;
                                if (!inv.getSafe(slot).matches(innerItem, !enchantSensitive.getValue(), onlySpecificEnchantsMatterToggled.getValue(), onlySpecificEnchantsMatter.getValue()))
                                    continue;
                                int innerSlot = innerInvSlot - size;
                                if (inv.getSafe(innerSlot).matches(innerItem, !enchantSensitive.getValue(), onlySpecificEnchantsMatterToggled.getValue(), onlySpecificEnchantsMatter.getValue()))
                                    continue;

                                mc.gameMode.handleInventoryMouseClick(id, innerInvSlot, 0, ClickType.PICKUP, mc.player);
                                mc.gameMode.handleInventoryMouseClick(id, invSlot, 0, ClickType.PICKUP, mc.player);
                                mc.gameMode.handleInventoryMouseClick(id, innerInvSlot, 0, ClickType.PICKUP, mc.player);
                                ticks = inventoryMoveDelay.getValue();
                                return;
                            }

                            for (int upperSlot = 0; upperSlot < size; upperSlot++) {
                                ItemStack slotItem = menu.slots.get(upperSlot).getItem();
                                if (slotItem.isEmpty()) continue;
                                if (!inv.getSafe(slot).matches(slotItem, !enchantSensitive.getValue(), onlySpecificEnchantsMatterToggled.getValue(), onlySpecificEnchantsMatter.getValue()))
                                    continue;
                                if (!ItemStack.isSameItemSameComponents(item, slotItem) && !item.isEmpty()) continue;

                                int needed = slotItem.getMaxStackSize();
                                if (!item.isEmpty()) needed -= item.getCount();

                                if (chestStoreManager.hasNotTotalQuantity(slotItem, needed, extra, enchantSensitive.getValue(), onlySpecificEnchantsMatterToggled.getValue(), onlySpecificEnchantsMatter.getValue())) {
                                    if (debug.getValue())
                                        ChatUtils.print("Not enough items in chests to take a full stack of " + inv.getSafe(slot).item);
                                    continue;
                                }

                                mc.gameMode.handleInventoryMouseClick(id, upperSlot, 0, ClickType.PICKUP, mc.player);
                                mc.gameMode.handleInventoryMouseClick(id, invSlot, 0, ClickType.PICKUP, mc.player);
                                mc.gameMode.handleInventoryMouseClick(id, upperSlot, 0, ClickType.PICKUP, mc.player);
                                ticks = inventoryMoveDelay.getValue();
                                return;
                            }
                        }
                    }
                    case InventoryMenu menu -> {
                        int id = menu.containerId;

                        for (int invSlot = InventoryMenu.INV_SLOT_START; invSlot < InventoryMenu.INV_SLOT_END; invSlot++) {
                            ItemStack item = menu.slots.get(invSlot).getItem();
                            int slot = invSlot - InventoryMenu.INV_SLOT_START;
                            if (inv.getSafe(slot) == null || inv.getSafe(slot).isAir()) continue;
                            if (inv.getSafe(slot).matches(item, !enchantSensitive.getValue(), onlySpecificEnchantsMatterToggled.getValue(), onlySpecificEnchantsMatter.getValue())
                                    && item.getMaxStackSize() == item.getCount())
                                continue;

                            for (int innerInvSlot = InventoryMenu.INV_SLOT_START; innerInvSlot < InventoryMenu.INV_SLOT_END; innerInvSlot++) {
                                ItemStack innerItem = menu.slots.get(innerInvSlot).getItem();
                                int innerSlot = innerInvSlot - InventoryMenu.INV_SLOT_START;
                                if (innerItem.isEmpty()) continue;
                                if (!inv.getSafe(slot).matches(innerItem, !enchantSensitive.getValue(), onlySpecificEnchantsMatterToggled.getValue(), onlySpecificEnchantsMatter.getValue()))
                                    continue;
                                if (inv.getSafe(innerSlot).matches(innerItem, !enchantSensitive.getValue(), onlySpecificEnchantsMatterToggled.getValue(), onlySpecificEnchantsMatter.getValue()))
                                    continue;
                                if (!item.isEmpty() && !ItemStack.isSameItemSameComponents(item, innerItem)) continue;

                                mc.gameMode.handleInventoryMouseClick(id, innerInvSlot, 0, ClickType.PICKUP, mc.player);
                                mc.gameMode.handleInventoryMouseClick(id, invSlot, 0, ClickType.PICKUP, mc.player);
                                mc.gameMode.handleInventoryMouseClick(id, innerInvSlot, 0, ClickType.PICKUP, mc.player);
                                ticks = inventoryMoveDelay.getValue();
                                return;
                            }
                        }
                    }
                    default -> {
                    }
                }

                if (shulkerPlacedToTakeItems) {
                    if (mc.player.hasContainerOpen()) mc.player.closeContainer();
                    state = State.MineShulker;
                    return;
                }

                boolean kitReadyInInventory = true;
                for (int i = 9; i < mc.player.getInventory().items.size(); i++) {
                    ItemStack invItem = mc.player.getInventory().getItem(i);
                    AutoKitItem kitItem = inv.getSafe(i - 9);

                    if (!kitItem.matches(invItem, !enchantSensitive.getValue(), onlySpecificEnchantsMatterToggled.getValue(), onlySpecificEnchantsMatter.getValue()) && !kitItem.isAir()) {
                        kitReadyInInventory = false;
                        break;
                    }

                    if (!invItem.isEmpty() && invItem.getCount() < invItem.getMaxStackSize()) {
                        kitReadyInInventory = false;
                        break;
                    }
                }

                if (mc.player.hasContainerOpen()) mc.player.closeContainer();
                if (kitReadyInInventory) {
                    state = State.GetShulker;
                } else {
                    state = State.SearchChestWithItem;
                }
            }
            case GetShulker -> {
                ItemStack item = mc.player.getInventory().getItem(1);
                if (item.isEmpty()) {
                    if (mc.player.position().distanceTo(emptyShulkerLocation.getCenter()) < 4.5) {
                        if (mc.player.containerMenu instanceof ChestMenu menu) {
                            if (!fullChest) return;
                            int size = menu.getContainer().getContainerSize();
                            int id = menu.containerId;

                            for (int i = 0; i < size; i++) {
                                if (!Utils.isShulker(menu.slots.get(i).getItem())) continue;
                                mc.gameMode.handleInventoryMouseClick(id, i, 0, ClickType.PICKUP, mc.player);
                                mc.gameMode.handleInventoryMouseClick(id, size + 9 * 3 + 1, 0, ClickType.PICKUP, mc.player);
                                ticks = inventoryMoveDelay.getValue();
                                if (mc.player.hasContainerOpen()) mc.player.closeContainer();
                                return;
                            }
                            return;
                        }
                        boolean res = RusherHackAPI.interactions().useBlock(emptyShulkerLocation, InteractionHand.MAIN_HAND, false, false);
                        if (!res) {
                            ChatUtils.print("Unable to use empty Shulker Chest");
                        }
                    } else if (BaritoneUtils.isBaritoneNotPathing()) {
                        BaritoneUtils.gotoChest(emptyShulkerLocation);
                    }
                    return;
                }
                if (Utils.isShulker(item)) {
                    state = State.PlaceShulker;
                    if (mc.player.hasContainerOpen()) mc.player.closeContainer();
                } else {
                    ChatUtils.print("Invalid item in slot number 1 (second from left)");
                    this.setToggled(true);
                }
            }
            case PlaceShulker -> {
                if (mc.level.getBlockState(placeLocation).getBlock() instanceof ShulkerBoxBlock) {
                    if (shulkerPlacedToTakeItems) {
                        BlockHitResult hr = RusherHackAPI.interactions().getBlockHitResult(placeLocation, false, false, mc.player.blockInteractionRange());
                        if (hr == null) return;
                        RusherHackAPI.getRotationManager().updateRotation(hr, 180);
                        boolean res = RusherHackAPI.interactions().useBlock(hr, InteractionHand.MAIN_HAND, true);
                        if (!res) {
                            ChatUtils.print("Unable to open Result Chest");
                            return;
                        }
                        state = State.Steal;
                    } else {
                        state = State.Fill;
                    }
                }

                if (mc.player.getBoundingBox().intersects(new AABB(placeLocation))) {
                    if (mc.player.getInventory().selected != 1) {
                        mc.player.getInventory().selected = 1;
                    }
                    List<Direction> dirs = List.of(Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH);
                    for (Direction dir : dirs) {
                        BlockPos pos = placeLocation.relative(dir, 3);
                        if (!mc.level.getBlockState(pos).isAir()) continue;
                        BaritoneUtils.goToExact(pos);
                        return;
                    }
                }

                if (mc.player.position().distanceTo(placeLocation.getCenter()) < 4) {
                    if (mc.player.getInventory().selected != 1) {
                        mc.player.getInventory().selected = 1;
                        ticks = inventoryMoveDelay.getValue();
                        return;
                    }
                    BlockHitResult res = RusherHackAPI.interactions().getBlockPlaceHitResult(placeLocation, false, false, 4);
                    if (res == null) {
                        return;
                    }
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, res);
                } else if (BaritoneUtils.isBaritoneNotPathing()) {
                    BaritoneUtils.goToExact(placeLocation);
                }
            }
            case Fill -> {
                if (!(mc.level.getBlockState(placeLocation).getBlock() instanceof ShulkerBoxBlock)) {
                    state = State.PlaceShulker;
                }
                if (mc.player.containerMenu instanceof ShulkerBoxMenu menu) {
                    int size = 27; // CONTAINER_SIZE
                    int id = menu.containerId;

                    for (int upperSlot = 0; upperSlot < size; upperSlot++) {
                        ItemStack slotItem = menu.slots.get(upperSlot).getItem();
                        if (inv.getSafe(upperSlot) == null || inv.getSafe(upperSlot).isAir()) continue;
                        if (inv.getSafe(upperSlot).matches(slotItem, !enchantSensitive.getValue(), onlySpecificEnchantsMatterToggled.getValue(), onlySpecificEnchantsMatter.getValue()))
                            continue;

                        for (int invSlot = size; invSlot < menu.slots.size() - 9; invSlot++) {
                            ItemStack item = menu.slots.get(invSlot).getItem();
                            if (item.isEmpty()) continue;
                            if (!inv.getSafe(upperSlot).matches(item, !enchantSensitive.getValue(), onlySpecificEnchantsMatterToggled.getValue(), onlySpecificEnchantsMatter.getValue()))
                                continue;
                            mc.gameMode.handleInventoryMouseClick(id, invSlot, 0, ClickType.PICKUP, mc.player);
                            mc.gameMode.handleInventoryMouseClick(id, upperSlot, 0, ClickType.PICKUP, mc.player);
                            ticks = inventoryMoveDelay.getValue();
                            return;
                        }
                    }

                    state = State.MineShulker;
                    if (mc.player.hasContainerOpen()) mc.player.closeContainer();
                } else {
                    RusherHackAPI.interactions().useBlock(placeLocation, InteractionHand.MAIN_HAND, false, false);
                }
            }
            case MineShulker -> {
                if (mc.getConnection() == null) return;
                if (Utils.isShulker(mc.player.getInventory().getItem(1))) {
                    if (shulkerPlacedToTakeItems) {
                        state = State.BringShulkerBack;
                    } else {
                        state = State.PutShulkerIntoResult;
                    }
                    return;
                }
                AABB aabb = AABB.ofSize(placeLocation.getCenter(), 2.5, 2.5, 2.5);
                List<Entity> targets = mc.level.getEntities(mc.player, aabb, e -> e instanceof ItemEntity);
                if (!targets.isEmpty() && mc.level.getBlockState(placeLocation).isAir()) {
                    BaritoneUtils.goToExact(targets.getFirst().blockPosition());
                    return;
                }
                if (!mc.level.getBlockState(placeLocation).isAir()) {
                    if (mc.player.getInventory().selected != 0) {
                        mc.player.getInventory().selected = 0;
                        ticks = inventoryMoveDelay.getValue();
                        return;
                    }
                    mc.gameMode.continueDestroyBlock(placeLocation, Utils.getDirection(placeLocation));
                    mc.player.swing(InteractionHand.MAIN_HAND);
                }
            }
            case PutShulkerIntoResult -> {
                if (mc.player.getEyePosition().distanceTo(resultLocation.getCenter()) < 4.5) {
                    if (mc.player.containerMenu instanceof ChestMenu menu) {
                        int id = menu.containerId;
                        int shulker = menu.slots.size() - 9 + 1;

                        mc.gameMode.handleInventoryMouseClick(id, shulker, 0, ClickType.QUICK_MOVE, mc.player);
                        if (mc.player.hasContainerOpen()) mc.player.closeContainer();
                        ticks = inventoryMoveDelay.getValue();
                        this.state = State.Steal;
                        return;
                    }
                    boolean res = RusherHackAPI.interactions().useBlock(resultLocation, InteractionHand.MAIN_HAND, false, false);
                    if (!res) {
                        ChatUtils.print("Unable to open Result Chest");
                    }
                } else if (BaritoneUtils.isBaritoneNotPathing()) {
                    BaritoneUtils.goToClose(resultLocation);
                }
            }
            case SearchChestWithItem -> {
                AutoKitItem neededItem = null;
                int neededAmount = -1;
                for (int i = 9; i < mc.player.getInventory().items.size(); i++) {
                    ItemStack invItem = mc.player.getInventory().getItem(i);
                    AutoKitItem kitItem = inv.getSafe(i - 9);

                    if (!kitItem.matches(invItem, !enchantSensitive.getValue(), onlySpecificEnchantsMatterToggled.getValue(), onlySpecificEnchantsMatter.getValue())
                            && !kitItem.isAir()) {
                        neededItem = kitItem;
                        break;
                    }

                    if (!invItem.isEmpty() && invItem.getCount() < invItem.getMaxStackSize()) {
                        neededAmount = invItem.getMaxStackSize() - invItem.getCount();
                        neededItem = kitItem;
                        break;
                    }
                }

                if (neededItem == null) {
                    state = State.GetShulker;
                    return;
                }

                Optional<List<BlockPos>> target = chestStoreManager.getChest(
                        neededItem,
                        enchantSensitive.getValue(),
                        onlySpecificEnchantsMatterToggled.getValue(),
                        onlySpecificEnchantsMatter.getValue(),
                        neededAmount == -1 ? neededItem.maxStackCount : neededAmount
                );
                if (target.isEmpty()) {
                    ChatUtils.print("Storage doesn't have enough " + neededItem.item);
                    return;
                }

                for (BlockPos pos : target.get()) {
                    if (mc.player.getEyePosition().distanceTo(pos.getCenter()) < 4.5) {
                        if (mc.player.containerMenu instanceof ChestMenu menu) {
                            state = State.Steal;
                            return;
                        }
                        boolean res = RusherHackAPI.interactions().useBlock(pos, InteractionHand.MAIN_HAND, false, false);
                        if (!res) {
                            ChatUtils.print("Unable to open Chest");
                        }
                        return;
                    }
                }

                if (target.get().size() == 2)
                    BaritoneUtils.gotoDoubleChest(target.get().get(0), target.get().get(1));
                else
                    BaritoneUtils.gotoChest(target.get().getFirst());
            }
            case BringShulkerBack -> {
                if (shulkerPlacedToTakeItemsOrigin == null) {
                    this.setToggled(true);
                    return;
                }
                if (mc.player.getEyePosition().distanceTo(shulkerPlacedToTakeItemsOrigin.getCenter()) < 4.5) {
                    if (mc.player.containerMenu instanceof ChestMenu menu) {
                        int id = menu.containerId;
                        int shulker = menu.slots.size() - 9 + 1;

                        mc.gameMode.handleInventoryMouseClick(id, shulker, 0, ClickType.QUICK_MOVE, mc.player);
                        shulkerPlacedToTakeItems = false;
                        shulkerPlacedToTakeItemsOrigin = null;
                        ticks = inventoryMoveDelay.getValue();
                        this.state = State.Steal;
                        return;
                    }
                    boolean res = RusherHackAPI.interactions().useBlock(shulkerPlacedToTakeItemsOrigin, InteractionHand.MAIN_HAND, false, false);
                    if (!res) {
                        ChatUtils.print("Unable to open Chest To Bring Items Back");
                    }
                } else if (BaritoneUtils.isBaritoneNotPathing()) {
                    BaritoneUtils.gotoChest(shulkerPlacedToTakeItemsOrigin);
                }
            }
            case null, default -> {
            }
        }
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onRender3D(EventRender3D event) {
        final IRenderer3D renderer = event.getRenderer();

        final int storageColorRGB = ColorUtils.transparency(storageColor.getValueRGB(), 0.5f);
        final int placeColorRGB = ColorUtils.transparency(placeColor.getValueRGB(), 0.5f);
        final int shulkerColorRGB = ColorUtils.transparency(shulkerColor.getValueRGB(), 0.5f);
        final int resultColorRGB = ColorUtils.transparency(resultColor.getValueRGB(), 0.5f);
        final int placedColorRGB = ColorUtils.transparency(placedColor.getValueRGB(), 0.5f);

        renderer.begin(event.getMatrixStack());

        if (placeLocation != null) renderer.drawBox(placeLocation, false, true, placeColorRGB);
        if (emptyShulkerLocation != null) renderer.drawBox(emptyShulkerLocation, false, true, shulkerColorRGB);
        if (resultLocation != null) renderer.drawBox(resultLocation, false, true, resultColorRGB);
        if (shulkerPlacedToTakeItemsOrigin != null)
            renderer.drawBox(shulkerPlacedToTakeItemsOrigin, false, true, placedColorRGB);

        for (BlockPos chestPos : chestStoreManager.getChests()) {
            renderer.drawBox(chestPos, false, true, storageColorRGB);
        }

        renderer.end();
    }

    public enum State {
        Steal,
        Fill,
        GetShulker,
        PlaceShulker,
        MineShulker,
        SearchChestWithItem,
        PutShulkerIntoResult,
        BringShulkerBack,
    }
}