package me.jddev0.ep.util;

import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class InventoryUtils {
    private InventoryUtils() {}

    public static boolean canInsertItemIntoSlot(Container inventory, int slot, ItemStack itemStack) {
        ItemStack inventoryItemStack = inventory.getItem(slot);

        return inventoryItemStack.isEmpty() || (ItemStack.isSameItemSameComponents(inventoryItemStack, itemStack) &&
                inventoryItemStack.getMaxStackSize() >= inventoryItemStack.getCount() + itemStack.getCount());
    }

    public static int getRedstoneSignalFromItemStackHandler(ItemStackHandler itemHandler) {
        float fullnessPercentSum = 0;
        boolean isEmptyFlag = true;

        int size = itemHandler.getSlots();
        for(int i = 0;i < size;i++) {
            ItemStack item = itemHandler.getStackInSlot(i);
            if(!item.isEmpty()) {
                fullnessPercentSum += (float)item.getCount() / Math.min(item.getMaxStackSize(), itemHandler.getSlotLimit(i));
                isEmptyFlag = false;
            }
        }

        return Math.min(Mth.floor(fullnessPercentSum / size * 14.f) + (isEmptyFlag?0:1), 15);
    }
}
