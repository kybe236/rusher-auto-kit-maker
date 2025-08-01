package de.kybe.autokitmaker;

import net.minecraft.core.BlockPos;

import java.util.List;

public class Chest {
    public final ChestInventory inv;
    private final List<BlockPos> positions;

    public Chest(ChestInventory inv, BlockPos pos) {
        this.inv = inv;
        this.positions = List.of(pos);
    }

    public Chest(ChestInventory inv, BlockPos pos, BlockPos pos2) {
        this.inv = inv;
        this.positions = List.of(pos, pos2);
    }

    public List<BlockPos> getPositions() {
        return positions;
    }
}