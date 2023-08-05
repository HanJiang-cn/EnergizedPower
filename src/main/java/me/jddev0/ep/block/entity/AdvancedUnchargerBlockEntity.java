package me.jddev0.ep.block.entity;

import me.jddev0.ep.block.entity.handler.InputOutputItemHandler;
import me.jddev0.ep.energy.EnergyStoragePacketUpdate;
import me.jddev0.ep.energy.ExtractOnlyEnergyStorage;
import me.jddev0.ep.networking.ModMessages;
import me.jddev0.ep.networking.packet.EnergySyncS2CPacket;
import me.jddev0.ep.screen.AdvancedUnchargerMenu;
import me.jddev0.ep.util.ByteUtils;
import me.jddev0.ep.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

public class AdvancedUnchargerBlockEntity extends BlockEntity implements MenuProvider, EnergyStoragePacketUpdate {
    public static final int MAX_EXTRACT_PER_SLOT = 8192;
    public static final int MAX_EXTRACT = MAX_EXTRACT_PER_SLOT * 3;

    private final ItemStackHandler itemHandler = new ItemStackHandler(3) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if(slot >= 0 && slot < 3) {
                LazyOptional<IEnergyStorage> energyStorageLazyOptional = stack.getCapability(ForgeCapabilities.ENERGY);
                if(!energyStorageLazyOptional.isPresent())
                    return false;

                IEnergyStorage energyStorage = energyStorageLazyOptional.orElse(null);
                return energyStorage.canExtract();
            }

            return super.isItemValid(slot, stack);
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            if(slot >= 0 && slot < 3) {
                ItemStack itemStack = getStackInSlot(slot);
                if(level != null && !stack.isEmpty() && !itemStack.isEmpty() && (!ItemStack.isSameItem(stack, itemStack) ||
                        (!ItemStack.isSameItemSameTags(stack, itemStack) &&
                                //Only check if NBT data is equal if one of stack or itemStack is no energy item
                                !(stack.getCapability(ForgeCapabilities.ENERGY).isPresent() && itemStack.getCapability(ForgeCapabilities.ENERGY).isPresent()))))
                    resetProgress(slot);
            }

            super.setStackInSlot(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };
    private LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.empty();
    private final LazyOptional<IItemHandler> lazyItemHandlerSided = LazyOptional.of(
            () -> new InputOutputItemHandler(itemHandler, (i, stack) -> true, i -> {
                if(i < 0 || i > 2)
                    return false;

                ItemStack stack = itemHandler.getStackInSlot(i);
                LazyOptional<IEnergyStorage> energyStorageLazyOptional = stack.getCapability(ForgeCapabilities.ENERGY);
                if(!energyStorageLazyOptional.isPresent())
                    return true;

                IEnergyStorage energyStorage = energyStorageLazyOptional.orElse(null);
                if(!energyStorage.canExtract())
                    return true;

                return energyStorage.extractEnergy(MAX_EXTRACT_PER_SLOT, true) == 0;
            }));

    private final ExtractOnlyEnergyStorage energyStorage;

    private LazyOptional<IEnergyStorage> lazyEnergyStorage = LazyOptional.empty();

    protected final ContainerData data;
    private int[] energyProductionLeft = new int[] {
            -1, -1, -1
    };

    public AdvancedUnchargerBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.ADVANCED_UNCHARGER_ENTITY.get(), blockPos, blockState);

        energyStorage = new ExtractOnlyEnergyStorage(0, 65536 * 3, MAX_EXTRACT) {
            @Override
            protected void onChange() {
                setChanged();

                if(level != null && !level.isClientSide())
                    ModMessages.sendToAllPlayers(new EnergySyncS2CPacket(energy, capacity, getBlockPos()));
            }
        };
        data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch(index) {
                    case 0, 1 -> ByteUtils.get2Bytes(AdvancedUnchargerBlockEntity.this.energyProductionLeft[0], index);
                    case 2, 3 -> ByteUtils.get2Bytes(AdvancedUnchargerBlockEntity.this.energyProductionLeft[1], index - 2);
                    case 4, 5 -> ByteUtils.get2Bytes(AdvancedUnchargerBlockEntity.this.energyProductionLeft[2], index - 4);
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                switch(index) {
                    case 0, 1, 2, 3, 4, 5 -> {}
                }
            }

            @Override
            public int getCount() {
                return 6;
            }
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.energizedpower.advanced_uncharger");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        ModMessages.sendToPlayer(new EnergySyncS2CPacket(energyStorage.getEnergy(), energyStorage.getCapacity(),
                getBlockPos()), (ServerPlayer)player);

        return new AdvancedUnchargerMenu(id, inventory, this, this.data);
    }

    public int getRedstoneOutput() {
        return InventoryUtils.getRedstoneSignalFromItemStackHandler(itemHandler);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if(cap == ForgeCapabilities.ITEM_HANDLER) {
            if(side == null)
                return lazyItemHandler.cast();

            return lazyItemHandlerSided.cast();
        }else if(cap == ForgeCapabilities.ENERGY) {
            return lazyEnergyStorage.cast();
        }

        return super.getCapability(cap, side);
    }

    @Override
    public void onLoad() {
        super.onLoad();

        lazyItemHandler = LazyOptional.of(() -> itemHandler);
        lazyEnergyStorage = LazyOptional.of(() -> energyStorage);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();

        lazyItemHandler.invalidate();
        lazyEnergyStorage.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        nbt.put("inventory", itemHandler.serializeNBT());
        nbt.put("energy", energyStorage.saveNBT());

        for(int i = 0;i < 3;i++)
            nbt.put("recipe.energy_production_left." + i, IntTag.valueOf(energyProductionLeft[i]));

        super.saveAdditional(nbt);
    }

    @Override
    public void load(@NotNull CompoundTag nbt) {
        super.load(nbt);

        itemHandler.deserializeNBT(nbt.getCompound("inventory"));
        energyStorage.loadNBT(nbt.get("energy"));

        for(int i = 0;i < 3;i++)
            energyProductionLeft[i] = nbt.getInt("recipe.energy_production_left." + i);
    }

    public void drops(Level level, BlockPos worldPosition) {
        SimpleContainer inventory = new SimpleContainer(itemHandler.getSlots());
        for(int i = 0;i < itemHandler.getSlots();i++)
            inventory.setItem(i, itemHandler.getStackInSlot(i));

        Containers.dropContents(level, worldPosition, inventory);
    }

    public static void tick(Level level, BlockPos blockPos, BlockState state, AdvancedUnchargerBlockEntity blockEntity) {
        if(level.isClientSide)
            return;

        tickRecipe(level, blockPos, state, blockEntity);
        transferEnergy(level, blockPos, state, blockEntity);
    }

    private static void tickRecipe(Level level, BlockPos blockPos, BlockState state, AdvancedUnchargerBlockEntity blockEntity) {
        if(level.isClientSide)
            return;

        final int maxExtractPerSlot = (int)Math.min(MAX_EXTRACT_PER_SLOT,
                Math.ceil((blockEntity.energyStorage.getMaxEnergyStored() - blockEntity.energyStorage.getEnergy()) / 3.));

        for(int i = 0;i < 3;i++) {
            if(blockEntity.hasRecipe(i)) {
                ItemStack stack = blockEntity.itemHandler.getStackInSlot(i);

                LazyOptional<IEnergyStorage> energyStorageLazyOptional = stack.getCapability(ForgeCapabilities.ENERGY);
                if(!energyStorageLazyOptional.isPresent())
                    continue;

                IEnergyStorage energyStorage = energyStorageLazyOptional.orElse(null);
                if(!energyStorage.canExtract())
                    continue;

                blockEntity.energyProductionLeft[i] = energyStorage.getEnergyStored();

                int energyProductionPerTick = energyStorage.extractEnergy(Math.min(maxExtractPerSlot,
                        blockEntity.energyStorage.getCapacity() - blockEntity.energyStorage.getEnergy()), false);

                if(blockEntity.energyProductionLeft[i] < 0 || energyProductionPerTick < 0) {
                    //Reset progress for invalid values

                    blockEntity.resetProgress(i);
                    setChanged(level, blockPos, state);

                    continue;
                }

                blockEntity.energyStorage.setEnergy(blockEntity.energyStorage.getEnergy() + energyProductionPerTick);
                blockEntity.energyProductionLeft[i] -= energyProductionPerTick;

                setChanged(level, blockPos, state);

                if(blockEntity.energyProductionLeft[i] <= 0)
                    blockEntity.resetProgress(i);
            }else {
                blockEntity.resetProgress(i);
                setChanged(level, blockPos, state);
            }
        }
    }

    private static void transferEnergy(Level level, BlockPos blockPos, BlockState state, AdvancedUnchargerBlockEntity blockEntity) {
        if(level.isClientSide)
            return;

        List<IEnergyStorage> consumerItems = new LinkedList<>();
        List<Integer> consumerEnergyValues = new LinkedList<>();
        int consumptionSum = 0;
        for(Direction direction:Direction.values()) {
            BlockPos testPos = blockPos.relative(direction);

            BlockEntity testBlockEntity = level.getBlockEntity(testPos);
            if(testBlockEntity == null)
                continue;

            LazyOptional<IEnergyStorage> energyStorageLazyOptional = testBlockEntity.getCapability(ForgeCapabilities.ENERGY, direction.getOpposite());
            if(!energyStorageLazyOptional.isPresent())
                continue;

            IEnergyStorage energyStorage = energyStorageLazyOptional.orElse(null);
            if(!energyStorage.canReceive())
                continue;

            int received = energyStorage.receiveEnergy(Math.min(MAX_EXTRACT, blockEntity.energyStorage.getEnergy()), true);
            if(received <= 0)
                continue;

            consumptionSum += received;
            consumerItems.add(energyStorage);
            consumerEnergyValues.add(received);
        }

        List<Integer> consumerEnergyDistributed = new LinkedList<>();
        for(int i = 0;i < consumerItems.size();i++)
            consumerEnergyDistributed.add(0);

        int consumptionLeft = Math.min(MAX_EXTRACT, Math.min(blockEntity.energyStorage.getEnergy(), consumptionSum));
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

    private void resetProgress(int index) {
        energyProductionLeft[index] = -1;
    }

    private boolean hasRecipe(int index) {
        ItemStack stack = itemHandler.getStackInSlot(index);
        return stack.getCapability(ForgeCapabilities.ENERGY).isPresent();
    }

    public int getEnergy() {
        return energyStorage.getEnergy();
    }

    public int getCapacity() {
        return energyStorage.getCapacity();
    }

    @Override
    public void setEnergy(int energy) {
        energyStorage.setEnergyWithoutUpdate(energy);
    }

    @Override
    public void setCapacity(int capacity) {
        energyStorage.setCapacityWithoutUpdate(capacity);
    }
}