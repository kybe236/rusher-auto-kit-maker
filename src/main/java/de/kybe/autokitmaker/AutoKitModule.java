package de.kybe.autokitmaker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
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
import net.minecraft.world.phys.Vec3;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.events.client.EventUpdate;
import org.rusherhack.client.api.events.network.EventPacket;
import org.rusherhack.client.api.events.render.EventRender3D;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.render.IRenderer3D;
import org.rusherhack.client.api.utils.ChatUtils;
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

    private final StringSetting currentKit = new StringSetting("CurrentKit", "");
    private final BooleanSetting enchantSensitive = new BooleanSetting("EnchantSensitive", false);
    private final NumberSetting<Integer> inventoryMoveDelay = new NumberSetting<>("InventoryMoveDelay", 5, 0, 20);
    private final NumberSetting<Integer> afterChestOpenDelay = new NumberSetting<>("AfterChestOpenDelay", 5, 0, 20);
    private final BooleanSetting addChests = new BooleanSetting("Add Chests", true);
    private final BooleanSetting active = new BooleanSetting("Active", false);

    private final BooleanSetting debug = new BooleanSetting("debug", false).setHidden(true);

    public BlockPos placeLocation = null;
    public BlockPos emptyShulkerLocation = null;
    public BlockPos resultLocation = null;
    public BlockPos openChest = null;
    public BlockPos shulkerPlacedToTakeItemsOrigin = null;
    private int ticksSinceLastMove = 0;
    private int ticksSinceChestOpen = 0;

    public State state = State.Steal;
    public boolean shulkerPlacedToTakeItems = false;

    public ChestStoreManager chestStoreManager = new ChestStoreManager();

    public AutoKitModule() {
        super("AutoKitMaker", ModuleCategory.CLIENT);
        this.registerSettings(currentKit, addChests, active, enchantSensitive, inventoryMoveDelay, afterChestOpenDelay, debug);

        INSTANCE = this;
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

    @Subscribe
    @SuppressWarnings("unused")
    private void onPacketSend(EventPacket.Send event) {
        if (event.getPacket() instanceof ServerboundUseItemOnPacket pkt) {
            BlockPos pos = pkt.getHitResult().getBlockPos();
            if (mc.level == null) return;
            if (mc.level.getBlockState(pos).getBlock() instanceof ChestBlock) {
                openChest = pos;
                ticksSinceChestOpen = 0;
            } else if (mc.level.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock) {
                openChest = pos;
                ticksSinceChestOpen = 0;
            }
        } else if (event.getPacket() instanceof ServerboundContainerClosePacket) openChest = null;
    }

    @SuppressWarnings("unused")
    @Subscribe
    private void onPacketReceive(EventPacket.Receive event) {
        if (event.getPacket() instanceof ClientboundContainerClosePacket) openChest = null;
    }

    public ChestInventory getChestInventoryFromScreen() {
        if (mc.player == null) return null;
        if (!(mc.player.containerMenu instanceof ChestMenu chestMenu)) return null;
        int size = chestMenu.getContainer().getContainerSize();
        ChestInventory chestInventory = new ChestInventory();
        for (int i = 0; i < size; i++) {
            chestInventory.addItem(chestMenu.slots.get(i).getItem());
        }
        return chestInventory;
    }

    boolean warned = false;


    @Subscribe
    @SuppressWarnings({"unused", "DuplicatedCode"})
    private void onUpdate(EventUpdate  event) {
        if (mc.player == null || mc.gameMode == null || mc.level == null)
            return;

        if (!warned && debug.getValue()) {
            ChatUtils.print("debug may show your coords only use it when your debuging and not live");
            debug.setValue(false);
            warned = true;
        }

        if (addChests.getValue()) {
            if (openChest == null) return;
            if (!(mc.player.containerMenu instanceof ChestMenu)) return;
            BlockState blockState = mc.level.getBlockState(openChest);
            if (blockState.getBlock() instanceof ChestBlock cb) {
                if (blockState.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
                    Chest chest = new Chest(getChestInventoryFromScreen(), openChest);
                    if (chestStoreManager.getChest(openChest) != null) return;
                    chestStoreManager.addChest(chest);
                } else {
                    Chest chest = new Chest(getChestInventoryFromScreen(), openChest, openChest.relative(ChestBlock.getConnectedDirection(blockState)));
                    if (chestStoreManager.getChest(openChest) != null) return;
                    chestStoreManager.addChest(chest);
                }
            } else if (blockState.getBlock() instanceof BarrelBlock) {
                Chest chest = new Chest(getChestInventoryFromScreen(), openChest);
                if (chestStoreManager.getChest(openChest) != null) return;
                chestStoreManager.addChest(chest);
            }
            return;
        }

        if (!active.getValue()) return;

        if (currentKit.getValue().isEmpty()) {
            ChatUtils.print("no kit selected");
            this.setToggled(false);
            return;
        }

        if (emptyShulkerLocation == null) {
            ChatUtils.print("Please use *auto-kit shulkerchest while looking at the chest giving empty shulkers to set the location");
            this.setToggled(false);
            return;
        }

        if (placeLocation == null) {
            ChatUtils.print("Please use *auto-kit placelocation while looking at the block below where the shulkers should be placed temporarily");
            this.setToggled(false);
            return;
        }

        AutoKitInventory inv = KitManager.load(currentKit.getValue());
        if (inv == null) return;

        if (ticksSinceLastMove < inventoryMoveDelay.getValue()) {
            ticksSinceLastMove++;
            return;
        }

        if (ticksSinceChestOpen < afterChestOpenDelay.getValue()) {
            ticksSinceChestOpen++;
            return;
        }

        if (debug.getValue()) ChatUtils.print(state.toString());
        if (state == State.Steal) {
            switch (mc.player.containerMenu) {
                case ChestMenu menu -> {
                    int size = menu.getContainer().getContainerSize();
                    int id = menu.containerId;
                    if (openChest == null) {
                        if (debug.getValue()) ChatUtils.print("openChest == null");
                        return;
                    }

                    Chest chest = chestStoreManager.getChest(openChest);
                    if (chest == null) {
                        if (debug.getValue()) ChatUtils.print("chest not in chest store");
                        return;
                    }
                    chest.inv.setItems(getChestInventoryFromScreen().getItems());

                    for (int invSlot = size; invSlot < menu.slots.size() - 9; invSlot++) {
                        ItemStack item = menu.slots.get(invSlot).getItem();
                        int slot = invSlot - size;
                        if (inv.getSafe(slot) == null || inv.getSafe(slot).isAir()) continue;
                        if (inv.getSafe(slot).matches(item, !enchantSensitive.getValue()) && item.getMaxStackSize() == item.getCount())
                            continue;

                        for (int innerInvSlot = size; innerInvSlot < menu.slots.size() - 9; innerInvSlot++) {
                            if (innerInvSlot == invSlot) continue;
                            ItemStack innerItem = menu.slots.get(innerInvSlot).getItem();
                            if (innerItem.isEmpty()) continue;
                            int innerSlot = innerInvSlot - size;
                            if (!inv.getSafe(slot).matches(innerItem, !enchantSensitive.getValue())) continue;
                            if (inv.getSafe(innerSlot).matches(innerItem, !enchantSensitive.getValue())) continue;
                            if (!ItemStack.isSameItemSameComponents(item, innerItem) && !item.isEmpty()) continue;

                            mc.gameMode.handleInventoryMouseClick(id, innerInvSlot, 0, ClickType.PICKUP, mc.player);
                            mc.gameMode.handleInventoryMouseClick(id, invSlot, 0, ClickType.PICKUP, mc.player);
                            mc.gameMode.handleInventoryMouseClick(id, innerInvSlot, 0, ClickType.PICKUP, mc.player);
                            ticksSinceLastMove = 0;
                            return;
                        }

                        for (int upperSlot = 0; upperSlot < size; upperSlot++) {
                            ItemStack slotItem = menu.slots.get(upperSlot).getItem();
                            if (slotItem.isEmpty()) continue;
                            if (!inv.getSafe(slot).matches(slotItem, !enchantSensitive.getValue())) continue;
                            if (!ItemStack.isSameItemSameComponents(item, slotItem) && !item.isEmpty()) continue;

                            int needed = slotItem.getMaxStackSize();
                            if (!item.isEmpty()) needed -= item.getCount();

                            if (chestStoreManager.hasNotTotalQuantity(slotItem, needed, null)) {
                                if (debug.getValue())
                                    ChatUtils.print("Not enough items in chests to take a full stack of " + inv.getSafe(slot).item);
                                continue;
                            }

                            mc.gameMode.handleInventoryMouseClick(id, upperSlot, 0, ClickType.PICKUP, mc.player);
                            mc.gameMode.handleInventoryMouseClick(id, invSlot, 0, ClickType.PICKUP, mc.player);
                            mc.gameMode.handleInventoryMouseClick(id, upperSlot, 0, ClickType.PICKUP, mc.player);
                            ticksSinceLastMove = 0;
                            return;
                        }

                        for (int upperSlot = 0; upperSlot < size; upperSlot++) {
                            if (!isShulker(menu.slots.get(upperSlot).getItem())) continue;
                            List<ItemStack> items = Utils.getContainerItemsFromStack(menu.slots.get(upperSlot).getItem());
                            for (ItemStack item1 : items) {
                                if (item1.isEmpty()) continue;
                                if (!inv.getSafe(slot).matches(item1, !enchantSensitive.getValue())) continue;
                                if (!ItemStack.isSameItemSameComponents(item, item1) && !item.isEmpty()) continue;

                                int needed = item1.getMaxStackSize();
                                if (!item.isEmpty()) needed -= item.getCount();

                                if (chestStoreManager.hasNotTotalQuantity(item1, needed, null)) {
                                    if (debug.getValue())
                                        ChatUtils.print("Not enough items in chests to take a full stack of " + inv.getSafe(slot).item);
                                    continue;
                                }

                                mc.gameMode.handleInventoryMouseClick(id, upperSlot, 0, ClickType.PICKUP, mc.player);
                                mc.gameMode.handleInventoryMouseClick(id, menu.slots.size() - 9 + 1, 0, ClickType.PICKUP, mc.player);
                                ticksSinceLastMove = 0;
                                shulkerPlacedToTakeItems = true;
                                shulkerPlacedToTakeItemsOrigin = openChest;
                                state = State.PlaceShulker;
                                mc.player.closeContainer();
                                return;
                            }
                        }
                    }
                }
                case ShulkerBoxMenu menu -> {
                    int size = 27; // CONTAINER_SIZE

                    int id = menu.containerId;

                    for (int invSlot = size; invSlot < menu.slots.size() - 9; invSlot++) {
                        ItemStack item = menu.slots.get(invSlot).getItem();
                        int slot = invSlot - size;
                        if (inv.getSafe(slot) == null || inv.getSafe(slot).isAir()) continue;
                        if (inv.getSafe(slot).matches(item, !enchantSensitive.getValue()) && item.getMaxStackSize() == item.getCount())
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
                            if (!inv.getSafe(slot).matches(innerItem, !enchantSensitive.getValue())) continue;
                            int innerSlot = innerInvSlot - size;
                            if (inv.getSafe(innerSlot).matches(innerItem, !enchantSensitive.getValue())) continue;

                            mc.gameMode.handleInventoryMouseClick(id, innerInvSlot, 0, ClickType.PICKUP, mc.player);
                            mc.gameMode.handleInventoryMouseClick(id, invSlot, 0, ClickType.PICKUP, mc.player);
                            mc.gameMode.handleInventoryMouseClick(id, innerInvSlot, 0, ClickType.PICKUP, mc.player);
                            ticksSinceLastMove = 0;
                            return;
                        }

                        for (int upperSlot = 0; upperSlot < size; upperSlot++) {
                            ItemStack slotItem = menu.slots.get(upperSlot).getItem();
                            if (slotItem.isEmpty()) continue;
                            if (!inv.getSafe(slot).matches(slotItem, !enchantSensitive.getValue())) continue;
                            if (!ItemStack.isSameItemSameComponents(item, slotItem) && !item.isEmpty()) continue;

                            int needed = slotItem.getMaxStackSize();
                            if (!item.isEmpty()) needed -= item.getCount();

                            if (chestStoreManager.hasNotTotalQuantity(slotItem, needed, extra)) {
                                if (debug.getValue())
                                    ChatUtils.print("Not enough items in chests to take a full stack of " + inv.getSafe(slot).item);
                                continue;
                            }

                            mc.gameMode.handleInventoryMouseClick(id, upperSlot, 0, ClickType.PICKUP, mc.player);
                            mc.gameMode.handleInventoryMouseClick(id, invSlot, 0, ClickType.PICKUP, mc.player);
                            mc.gameMode.handleInventoryMouseClick(id, upperSlot, 0, ClickType.PICKUP, mc.player);
                            ticksSinceLastMove = 0;
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
                        if (inv.getSafe(slot).matches(item, !enchantSensitive.getValue()) && item.getMaxStackSize() == item.getCount())
                            continue;

                        for (int innerInvSlot = InventoryMenu.INV_SLOT_START; innerInvSlot < InventoryMenu.INV_SLOT_END; innerInvSlot++) {
                            ItemStack innerItem = menu.slots.get(innerInvSlot).getItem();
                            int innerSlot = innerInvSlot - InventoryMenu.INV_SLOT_START;
                            if (innerItem.isEmpty()) continue;
                            if (!inv.getSafe(slot).matches(innerItem, !enchantSensitive.getValue())) continue;
                            if (inv.getSafe(innerSlot).matches(innerItem, !enchantSensitive.getValue())) continue;
                            if (!item.isEmpty() && !ItemStack.isSameItemSameComponents(item, innerItem)) continue;

                            mc.gameMode.handleInventoryMouseClick(id, innerInvSlot, 0, ClickType.PICKUP, mc.player);
                            mc.gameMode.handleInventoryMouseClick(id, invSlot, 0, ClickType.PICKUP, mc.player);
                            mc.gameMode.handleInventoryMouseClick(id, innerInvSlot, 0, ClickType.PICKUP, mc.player);
                            ticksSinceLastMove = 0;
                            return;
                        }
                    }
                }
                default -> {
                }
            }

            if (shulkerPlacedToTakeItems) {
                state = State.MineShulker;
                return;
            }

            boolean kitReadyInInventory = true;
            for (int i = 9; i < mc.player.getInventory().items.size(); i++) {
                ItemStack invItem = mc.player.getInventory().getItem(i);
                AutoKitItem kitItem = inv.getSafe(i - 9);

                if (!kitItem.matches(invItem, !enchantSensitive.getValue()) && !kitItem.isAir()) {
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
        } else if (state == State.GetShulker) {
            ItemStack item = mc.player.getInventory().getItem(1);
            if (item.isEmpty()) {
                if (mc.player.position().distanceTo(emptyShulkerLocation.getCenter()) < 4.5) {
                    if (mc.player.containerMenu instanceof ChestMenu menu) {
                        int size = menu.getContainer().getContainerSize();
                        int id = menu.containerId;

                        for (int i = 0; i < size; i++) {
                            if (!isShulker(menu.slots.get(i).getItem())) continue;
                            mc.gameMode.handleInventoryMouseClick(id, i, 0, ClickType.PICKUP, mc.player);
                            mc.gameMode.handleInventoryMouseClick(id, size + 9 * 3 + 1, 0, ClickType.PICKUP, mc.player);
                            ticksSinceLastMove = 0;
                            mc.player.closeContainer();
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
            if (isShulker(item)) {
                state = State.PlaceShulker;
                mc.player.closeContainer();
            } else {
                ChatUtils.print("Invalid item in slot number 1 (second from left)");
                this.setToggled(true);
            }
        } else if (state == State.PlaceShulker) {
            if (mc.level.getBlockState(placeLocation).getBlock() instanceof ShulkerBoxBlock) {
                if (shulkerPlacedToTakeItems) {
                    boolean res = RusherHackAPI.interactions().useBlock(placeLocation, InteractionHand.MAIN_HAND, false, false);
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
                    ticksSinceLastMove = 0;
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
        } else if (state == State.Fill) {
            if (mc.player.containerMenu instanceof ShulkerBoxMenu menu) {
                int size = 27; // CONTAINER_SIZE
                int id = menu.containerId;

                for (int upperSlot = 0; upperSlot < size; upperSlot++) {
                    ItemStack slotItem = menu.slots.get(upperSlot).getItem();
                    if (inv.getSafe(upperSlot) == null || inv.getSafe(upperSlot).isAir()) continue;
                    if (inv.getSafe(upperSlot).matches(slotItem, !enchantSensitive.getValue())) continue;

                    for (int invSlot = size; invSlot < menu.slots.size() - 9; invSlot++) {
                        ItemStack item = menu.slots.get(invSlot).getItem();
                        if (item.isEmpty()) continue;
                        if (!inv.getSafe(upperSlot).matches(item, !enchantSensitive.getValue())) continue;
                        mc.gameMode.handleInventoryMouseClick(id, invSlot, 0, ClickType.PICKUP, mc.player);
                        mc.gameMode.handleInventoryMouseClick(id, upperSlot, 0, ClickType.PICKUP, mc.player);
                        ticksSinceLastMove = 0;
                        return;
                    }
                }

                state = State.MineShulker;
                mc.player.closeContainer();
            } else {
                RusherHackAPI.interactions().useBlock(placeLocation, InteractionHand.MAIN_HAND, false, false);
            }
        } else if (state == State.MineShulker) {
            if (mc.getConnection() == null) return;
            if (isShulker(mc.player.getInventory().getItem(1))) {
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
                    ticksSinceLastMove = 0;
                    return;
                }
                mc.gameMode.continueDestroyBlock(placeLocation, getDirection(placeLocation));
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
        } else if (state == State.PutShulkerIntoResult) {
            if (mc.player.getEyePosition().distanceTo(resultLocation.getCenter()) < 4.5) {
                if (mc.player.containerMenu instanceof ChestMenu menu) {
                    int id = menu.containerId;

                    int shulker = menu.slots.size() - 9 + 1;

                    mc.gameMode.handleInventoryMouseClick(id, shulker, 0, ClickType.QUICK_MOVE, mc.player);
                    mc.player.closeContainer();
                    ticksSinceLastMove = 0;
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
        } else if (state == State.SearchChestWithItem) {
            AutoKitItem neededItem = null;
            int neededAmount = -1;
            for (int i = 9; i < mc.player.getInventory().items.size(); i++) {
                ItemStack invItem = mc.player.getInventory().getItem(i);
                AutoKitItem kitItem = inv.getSafe(i - 9);

                if (!kitItem.matches(invItem, !enchantSensitive.getValue()) && !kitItem.isAir()) {
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

            Optional<List<BlockPos>> target = chestStoreManager.getChest(neededItem, enchantSensitive.getValue(), neededAmount == -1 ? neededItem.maxStackCount : neededAmount);
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

            if (target.get().size() == 2) BaritoneUtils.gotoDoubleChest(target.get().get(0), target.get().get(1));
            else BaritoneUtils.gotoChest(target.get().getFirst());
        } else if (state == State.BringShulkerBack) {
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
                    ticksSinceLastMove = 0;
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
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onRender3D(EventRender3D event) {
        final IRenderer3D renderer = event.getRenderer();

        final int black = ColorUtils.transparency(Color.BLACK.getRGB(), 0.5f);
        final int red = ColorUtils.transparency(Color.RED.getRGB(), 0.5f);
        final int green = ColorUtils.transparency(Color.GREEN.getRGB(), 0.5f);
        final int yellow = ColorUtils.transparency(Color.YELLOW.getRGB(), 0.5f);
        final int orange = ColorUtils.transparency(Color.ORANGE.getRGB(), 0.5f);

        renderer.begin(event.getMatrixStack());

        if (placeLocation != null) renderer.drawBox(placeLocation, false, true, red);
        if (emptyShulkerLocation != null) renderer.drawBox(emptyShulkerLocation, false, true, green);
        if (resultLocation != null) renderer.drawBox(resultLocation, false, true, yellow);
        if (shulkerPlacedToTakeItemsOrigin != null) renderer.drawBox(shulkerPlacedToTakeItemsOrigin, false, true, orange);

        for (BlockPos chestPos : chestStoreManager.getChests()) {
            renderer.drawBox(chestPos, false, true, black);
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