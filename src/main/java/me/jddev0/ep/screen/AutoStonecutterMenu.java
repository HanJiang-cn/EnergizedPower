package me.jddev0.ep.screen;

import me.jddev0.ep.block.ModBlocks;
import me.jddev0.ep.block.entity.AutoStonecutterBlockEntity;
import me.jddev0.ep.inventory.ItemCapabilityMenuHelper;
import me.jddev0.ep.inventory.UpgradeModuleSlot;
import me.jddev0.ep.inventory.UpgradeModuleViewContainerData;
import me.jddev0.ep.inventory.upgrade.UpgradeModuleInventory;
import me.jddev0.ep.machine.configuration.ComparatorMode;
import me.jddev0.ep.machine.configuration.RedstoneMode;
import me.jddev0.ep.machine.upgrade.UpgradeModuleModifier;
import me.jddev0.ep.screen.base.ConfigurableMenu;
import me.jddev0.ep.screen.base.EnergyStorageConsumerIndicatorBarMenu;
import me.jddev0.ep.screen.base.SelectableRecipeMachineMenu;
import me.jddev0.ep.util.ByteUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

public class AutoStonecutterMenu extends AbstractContainerMenu
        implements EnergyStorageConsumerIndicatorBarMenu, ConfigurableMenu,
        SelectableRecipeMachineMenu<StonecutterRecipe> {
    private final AutoStonecutterBlockEntity blockEntity;
    private final Level level;
    private final ContainerData data;
    private final UpgradeModuleViewContainerData upgradeModuleViewContainerData;

    public AutoStonecutterMenu(int id, Inventory inv, FriendlyByteBuf buffer) {
        this(id, inv, inv.player.level().getBlockEntity(buffer.readBlockPos()), new UpgradeModuleInventory(
                UpgradeModuleModifier.SPEED,
                UpgradeModuleModifier.ENERGY_CONSUMPTION,
                UpgradeModuleModifier.ENERGY_CAPACITY
        ), new SimpleContainerData(9));
    }

    public AutoStonecutterMenu(int id, Inventory inv, BlockEntity blockEntity, UpgradeModuleInventory upgradeModuleInventory,
                               ContainerData data) {
        super(ModMenuTypes.AUTO_STONECUTTER_MENU.get(), id);

        checkContainerSize(upgradeModuleInventory, 3);
        checkContainerDataCount(data, 9);
        this.blockEntity = (AutoStonecutterBlockEntity)blockEntity;
        this.level = inv.player.level();
        this.data = data;

        addPlayerInventory(inv);
        addPlayerHotbar(inv);

        ItemCapabilityMenuHelper.getCapabilityItemHandler(this.level, this.blockEntity).ifPresent(itemHandler -> {
            addSlot(new SlotItemHandler(itemHandler, 0, 39, 44) {
                @Override
                public boolean isActive() {
                    return super.isActive() && !isInUpgradeModuleView();
                }
            });
            addSlot(new SlotItemHandler(itemHandler, 1, 57, 44) {
                @Override
                public boolean isActive() {
                    return super.isActive() && !isInUpgradeModuleView();
                }
            });
            addSlot(new SlotItemHandler(itemHandler, 2, 124, 44) {
                @Override
                public boolean isActive() {
                    return super.isActive() && !isInUpgradeModuleView();
                }
            });
        });

        for(int i = 0;i < upgradeModuleInventory.getContainerSize();i++)
            addSlot(new UpgradeModuleSlot(upgradeModuleInventory, i, 62 + i * 18, 35, this::isInUpgradeModuleView));

        addDataSlots(this.data);

        upgradeModuleViewContainerData = new UpgradeModuleViewContainerData();
        addDataSlots(upgradeModuleViewContainerData);
    }

    @Override
    public boolean isInUpgradeModuleView() {
        return upgradeModuleViewContainerData.isInUpgradeModuleView();
    }

    @Override
    public boolean clickMenuButton(Player player, int index) {
        if(index == 0) {
            upgradeModuleViewContainerData.toggleInUpgradeModuleView();

            broadcastChanges();
        }

        return false;
    }

    @Override
    public int getEnergy() {
        return blockEntity.getEnergy();
    }

    @Override
    public int getCapacity() {
        return blockEntity.getCapacity();
    }

    @Override
    public int getEnergyIndicatorBarValue() {
        return ByteUtils.from2ByteChunks((short)data.get(4), (short)data.get(5));
    }

    /**
     * @return Same as isCrafting but energy requirements are ignored
     */
    public boolean isCraftingActive() {
        return ByteUtils.from2ByteChunks((short)data.get(0), (short)data.get(1)) > 0;
    }

    public boolean isCrafting() {
        return ByteUtils.from2ByteChunks((short)data.get(0), (short)data.get(1)) > 0 && data.get(6) == 1;
    }

    public int getScaledProgressArrowSize() {
        int progress = ByteUtils.from2ByteChunks((short)data.get(0), (short)data.get(1));
        int maxProgress = ByteUtils.from2ByteChunks((short)data.get(2), (short)data.get(3));
        int progressArrowSize = 24;

        return (maxProgress == 0 || progress == 0)?0:progress * progressArrowSize / maxProgress;
    }

    @Override
    public RedstoneMode getRedstoneMode() {
        return RedstoneMode.fromIndex(data.get(7));
    }

    @Override
    public ComparatorMode getComparatorMode() {
        return ComparatorMode.fromIndex(data.get(8));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot sourceSlot = slots.get(index);
        if(sourceSlot == null || !sourceSlot.hasItem())
            return ItemStack.EMPTY;

        ItemStack sourceItem = sourceSlot.getItem();
        ItemStack sourceItemCopy = sourceItem.copy();

        if(index < 4 * 9) {
            //Player inventory slot -> Merge into upgrade module inventory, Merge into tile inventory
            if(!moveItemStackTo(sourceItem, 4 * 9 + 3, 4 * 9 + 3 + 3, false) &&
                    !moveItemStackTo(sourceItem, 4 * 9, 4 * 9 + 2, false)) {
                //"+2" instead of "+3": Do not allow adding to output slot
                return ItemStack.EMPTY;
            }
        }else if(index < 4 * 9 + 3 + 3) {
            //Tile inventory and upgrade module slot -> Merge into player inventory
            if(!moveItemStackTo(sourceItem, 0, 4 * 9, false)) {
                return ItemStack.EMPTY;
            }
        }else {
            throw new IllegalArgumentException("Invalid slot index");
        }

        if(sourceItem.getCount() == 0)
            sourceSlot.set(ItemStack.EMPTY);
        else
            sourceSlot.setChanged();

        sourceSlot.onTake(player, sourceItem);

        return sourceItemCopy;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(level, blockEntity.getBlockPos()), player, ModBlocks.AUTO_STONECUTTER.get());
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for(int i = 0;i < 3;i++) {
            for(int j = 0;j < 9;j++) {
                addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for(int i = 0;i < 9;i++) {
            addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }

    @Override
    public BlockEntity getBlockEntity() {
        return blockEntity;
    }

    @Override
    public RecipeHolder<StonecutterRecipe> getCurrentRecipe() {
        return blockEntity.getCurrentRecipe();
    }
}