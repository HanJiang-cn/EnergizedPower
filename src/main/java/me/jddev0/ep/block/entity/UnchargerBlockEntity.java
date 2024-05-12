package me.jddev0.ep.block.entity;

import me.jddev0.ep.block.UnchargerBlock;
import me.jddev0.ep.block.entity.base.ConfigurableUpgradableInventoryEnergyStorageBlockEntity;
import me.jddev0.ep.inventory.InputOutputItemHandler;
import me.jddev0.ep.config.ModConfigs;
import me.jddev0.ep.energy.ExtractOnlyEnergyStorage;
import me.jddev0.ep.machine.configuration.ComparatorMode;
import me.jddev0.ep.machine.configuration.RedstoneMode;
import me.jddev0.ep.machine.upgrade.UpgradeModuleModifier;
import me.jddev0.ep.screen.UnchargerMenu;
import me.jddev0.ep.util.ByteUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

public class UnchargerBlockEntity
        extends ConfigurableUpgradableInventoryEnergyStorageBlockEntity<ExtractOnlyEnergyStorage, ItemStackHandler>
        implements MenuProvider {
    private final IItemHandler itemHandlerSided = new InputOutputItemHandler(itemHandler, (i, stack) -> true, i -> {
        if(i != 0)
            return false;

        ItemStack stack = itemHandler.getStackInSlot(i);
        IEnergyStorage energyStorage = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if(energyStorage == null || !energyStorage.canExtract())
            return true;

        return energyStorage.extractEnergy(UnchargerBlockEntity.this.energyStorage.getMaxExtract(), true) == 0;
    });

    protected final ContainerData data;
    private int energyProductionLeft = -1;

    public UnchargerBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(
                ModBlockEntities.UNCHARGER_ENTITY.get(), blockPos, blockState,

                ModConfigs.COMMON_UNCHARGER_CAPACITY.getValue(),
                ModConfigs.COMMON_UNCHARGER_TRANSFER_RATE.getValue(),

                1,

                UpgradeModuleModifier.ENERGY_CAPACITY
        );

        data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch(index) {
                    case 0, 1 -> ByteUtils.get2Bytes(UnchargerBlockEntity.this.energyProductionLeft, index);
                    case 2 -> redstoneMode.ordinal();
                    case 3 -> comparatorMode.ordinal();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                switch(index) {
                    case 0, 1 -> {}
                    case 2 -> UnchargerBlockEntity.this.redstoneMode = RedstoneMode.fromIndex(value);
                    case 3 -> UnchargerBlockEntity.this.comparatorMode = ComparatorMode.fromIndex(value);
                }
            }

            @Override
            public int getCount() {
                return 4;
            }
        };
    }

    @Override
    protected ExtractOnlyEnergyStorage initEnergyStorage() {
        return new ExtractOnlyEnergyStorage(0, baseEnergyCapacity, baseEnergyTransferRate) {
            @Override
            public int getCapacity() {
                return Math.max(1, (int)Math.ceil(capacity * upgradeModuleInventory.getModifierEffectProduct(
                        UpgradeModuleModifier.ENERGY_CAPACITY)));
            }

            @Override
            public int getMaxExtract() {
                return Math.max(1, (int)Math.ceil(maxExtract * upgradeModuleInventory.getModifierEffectProduct(
                        UpgradeModuleModifier.ENERGY_TRANSFER_RATE)));
            }

            @Override
            protected void onChange() {
                setChanged();
                syncEnergyToPlayers(32);
            }
        };
    }

    @Override
    protected ItemStackHandler initInventoryStorage() {
        return new ItemStackHandler(slotCount) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                if(slot == 0) {
                    IEnergyStorage energyStorage = stack.getCapability(Capabilities.EnergyStorage.ITEM);
                    return energyStorage != null && energyStorage.canExtract();
                }

                return super.isItemValid(slot, stack);
            }

            @Override
            public void setStackInSlot(int slot, @NotNull ItemStack stack) {
                if(slot == 0) {
                    ItemStack itemStack = getStackInSlot(slot);
                    if(level != null && !stack.isEmpty() && !itemStack.isEmpty() && (!ItemStack.isSameItem(stack, itemStack) ||
                            (!ItemStack.isSameItemSameComponents(stack, itemStack) &&
                                    //Only check if NBT data is equal if one of stack or itemStack is no energy item
                                    !(stack.getCapability(Capabilities.EnergyStorage.ITEM) != null && itemStack.getCapability(Capabilities.EnergyStorage.ITEM) != null))))
                        resetProgress();
                }

                super.setStackInSlot(slot, stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.energizedpower.uncharger");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        syncEnergyToPlayer(player);

        return new UnchargerMenu(id, inventory, this, upgradeModuleInventory, this.data);
    }

    public @Nullable IItemHandler getItemHandlerCapability(@Nullable Direction side) {
        if(side == null)
            return itemHandler;

        return itemHandlerSided;
    }

    public @Nullable IEnergyStorage getEnergyStorageCapability(@Nullable Direction side) {
        return energyStorage;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag nbt, @NotNull HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);

        nbt.put("recipe.energy_production_left", IntTag.valueOf(energyProductionLeft));
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag nbt, @NotNull HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);

        energyProductionLeft = nbt.getInt("recipe.energy_production_left");
    }

    public static void tick(Level level, BlockPos blockPos, BlockState state, UnchargerBlockEntity blockEntity) {
        if(level.isClientSide)
            return;

        if(blockEntity.redstoneMode.isActive(state.getValue(UnchargerBlock.POWERED)))
           tickRecipe(level, blockPos, state, blockEntity);

        transferEnergy(level, blockPos, state, blockEntity);
    }

    private static void tickRecipe(Level level, BlockPos blockPos, BlockState state, UnchargerBlockEntity blockEntity) {
        if(level.isClientSide)
            return;

        if(blockEntity.hasRecipe()) {
            ItemStack stack = blockEntity.itemHandler.getStackInSlot(0);

            IEnergyStorage energyStorage = stack.getCapability(Capabilities.EnergyStorage.ITEM);
            if(energyStorage == null || !energyStorage.canExtract())
                return;

            blockEntity.energyProductionLeft = energyStorage.getEnergyStored();

            int energyProductionPerTick = energyStorage.extractEnergy(Math.min(blockEntity.energyStorage.getMaxExtract(),
                    blockEntity.energyStorage.getCapacity() - blockEntity.energyStorage.getEnergy()), false);

            if(blockEntity.energyProductionLeft < 0 || energyProductionPerTick < 0) {
                //Reset progress for invalid values

                blockEntity.resetProgress();
                setChanged(level, blockPos, state);

                return;
            }

            blockEntity.energyStorage.setEnergy(blockEntity.energyStorage.getEnergy() + energyProductionPerTick);
            blockEntity.energyProductionLeft -= energyProductionPerTick;

            if(blockEntity.energyProductionLeft <= 0)
                blockEntity.resetProgress();

            setChanged(level, blockPos, state);
        }else {
            blockEntity.resetProgress();
            setChanged(level, blockPos, state);
        }
    }

    private static void transferEnergy(Level level, BlockPos blockPos, BlockState state, UnchargerBlockEntity blockEntity) {
        if(level.isClientSide)
            return;

        List<IEnergyStorage> consumerItems = new LinkedList<>();
        List<Integer> consumerEnergyValues = new LinkedList<>();
        int consumptionSum = 0;
        for(Direction direction:Direction.values()) {
            BlockPos testPos = blockPos.relative(direction);

            BlockEntity testBlockEntity = level.getBlockEntity(testPos);

            IEnergyStorage energyStorage = level.getCapability(Capabilities.EnergyStorage.BLOCK, testPos,
                    level.getBlockState(testPos), testBlockEntity, direction.getOpposite());
            if(energyStorage == null || !energyStorage.canReceive())
                continue;

            int received = energyStorage.receiveEnergy(Math.min(blockEntity.energyStorage.getMaxExtract(),
                    blockEntity.energyStorage.getEnergy()), true);
            if(received <= 0)
                continue;

            consumptionSum += received;
            consumerItems.add(energyStorage);
            consumerEnergyValues.add(received);
        }

        List<Integer> consumerEnergyDistributed = new LinkedList<>();
        for(int i = 0;i < consumerItems.size();i++)
            consumerEnergyDistributed.add(0);

        int consumptionLeft = Math.min(blockEntity.energyStorage.getMaxExtract(),
                Math.min(blockEntity.energyStorage.getEnergy(), consumptionSum));
        blockEntity.energyStorage.extractEnergy(consumptionLeft, false);

        int divisor = consumerItems.size();
        outer:
        while(consumptionLeft > 0) {
            int consumptionPerConsumer = consumptionLeft / divisor;
            if(consumptionPerConsumer == 0) {
                divisor = Math.max(1, divisor - 1);
                consumptionPerConsumer = consumptionLeft / divisor;
            }

            for(int i = 0;i < consumerEnergyValues.size();i++) {
                int consumptionDistributed = consumerEnergyDistributed.get(i);
                int consumptionOfConsumerLeft = consumerEnergyValues.get(i) - consumptionDistributed;

                int consumptionDistributedNew = Math.min(consumptionOfConsumerLeft, Math.min(consumptionPerConsumer, consumptionLeft));
                consumerEnergyDistributed.set(i, consumptionDistributed + consumptionDistributedNew);
                consumptionLeft -= consumptionDistributedNew;
                if(consumptionLeft == 0)
                    break outer;
            }
        }

        for(int i = 0;i < consumerItems.size();i++) {
            int energy = consumerEnergyDistributed.get(i);
            if(energy > 0)
                consumerItems.get(i).receiveEnergy(energy, false);
        }
    }

    private void resetProgress() {
        energyProductionLeft = -1;
    }

    private boolean hasRecipe() {
        ItemStack stack = itemHandler.getStackInSlot(0);
        return stack.getCapability(Capabilities.EnergyStorage.ITEM) != null;
    }

    @Override
    protected void updateUpgradeModules() {
        resetProgress();

        super.updateUpgradeModules();
    }
}