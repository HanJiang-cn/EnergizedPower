package me.jddev0.ep.block.entity;

import me.jddev0.ep.block.entity.base.FluidStorageBlockEntity;
import me.jddev0.ep.block.entity.base.FluidStorageSingleTankMethods;
import me.jddev0.ep.config.ModConfigs;
import me.jddev0.ep.networking.ModMessages;
import me.jddev0.ep.networking.packet.FluidSyncS2CPacket;
import me.jddev0.ep.screen.DrainMenu;
import me.jddev0.ep.util.ByteUtils;
import me.jddev0.ep.util.FluidUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DrainBlockEntity extends FluidStorageBlockEntity<FluidTank> implements MenuProvider {
    protected final ContainerData data;
    private int progress;
    private int maxProgress = ModConfigs.COMMON_DRAIN_DRAIN_DURATION.getValue();

    public DrainBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(
                ModBlockEntities.DRAIN_ENTITY.get(), blockPos, blockState,

                FluidStorageSingleTankMethods.INSTANCE,
                ModConfigs.COMMON_DRAIN_FLUID_TANK_CAPACITY.getValue() * 1000
        );

        data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch(index) {
                    case 0, 1 -> ByteUtils.get2Bytes(DrainBlockEntity.this.progress, index);
                    case 2, 3 -> ByteUtils.get2Bytes(DrainBlockEntity.this.maxProgress, index - 2);
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                switch(index) {
                    case 0, 1 -> DrainBlockEntity.this.progress = ByteUtils.with2Bytes(
                            DrainBlockEntity.this.progress, (short)value, index
                    );
                    case 2, 3 -> DrainBlockEntity.this.maxProgress = ByteUtils.with2Bytes(
                            DrainBlockEntity.this.maxProgress, (short)value, index - 2
                    );
                }
            }

            @Override
            public int getCount() {
                return 4;
            }
        };
    }

    @Override
    protected FluidTank initFluidStorage() {
        return new FluidTank(baseTankCapacity) {
            @Override
            protected void onContentsChanged() {
                setChanged();

                if(level != null && !level.isClientSide())
                    ModMessages.sendToPlayersWithinXBlocks(
                            new FluidSyncS2CPacket(0, fluid, capacity, getBlockPos()),
                            getBlockPos(), (ServerLevel)level, 32
                    );
            }
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.energizedpower.drain");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        ModMessages.sendToPlayer(new FluidSyncS2CPacket(0, fluidStorage.getFluid(), fluidStorage.getCapacity(), worldPosition), (ServerPlayer)player);

        return new DrainMenu(id, inventory, this, this.data);
    }

    public int getRedstoneOutput() {
        return FluidUtils.getRedstoneSignalFromFluidHandler(fluidStorage);
    }

    public @Nullable IFluidHandler getFluidHandlerCapability(@Nullable Direction side) {
        return fluidStorage;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag nbt, @NotNull HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);

        nbt.put("drain.progress", IntTag.valueOf(progress));
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag nbt, @NotNull HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);

        progress = nbt.getInt("drain.progress");
    }

    public static void tick(Level level, BlockPos blockPos, BlockState state, DrainBlockEntity blockEntity) {
        if(level.isClientSide)
            return;

        if(hasRecipe(blockEntity)) {
            if(blockEntity.progress < 0 || blockEntity.maxProgress < 0) {
                //Reset progress for invalid values

                blockEntity.resetProgress(blockPos, state);
                setChanged(level, blockPos, state);

                return;
            }

            if(blockEntity.progress < blockEntity.maxProgress)
                blockEntity.progress++;

            if(blockEntity.progress >= blockEntity.maxProgress) {
                BlockPos aboveBlockPos = blockPos.above();
                BlockState aboveBlockState = level.getBlockState(aboveBlockPos);
                if(aboveBlockState.getBlock() instanceof BucketPickup aboveBlock) {
                    ItemStack bucketItemStack = aboveBlock.pickupBlock(null, level, aboveBlockPos, aboveBlockState);

                    if(!bucketItemStack.isEmpty()) {
                        level.gameEvent(null, GameEvent.FLUID_PICKUP, aboveBlockPos);

                        IFluidHandlerItem fluidStorage = bucketItemStack.getCapability(Capabilities.FluidHandler.ITEM);
                        if(fluidStorage != null && fluidStorage.getTanks() == 1) {
                            FluidStack fluidStack = fluidStorage.getFluidInTank(0);

                            if(!fluidStack.isEmpty())
                                blockEntity.fluidStorage.fill(fluidStack.copy(), IFluidHandler.FluidAction.EXECUTE);
                        }
                    }
                }

                blockEntity.resetProgress(blockPos, state);
            }

            setChanged(level, blockPos, state);
        }else {
            blockEntity.resetProgress(blockPos, state);
            setChanged(level, blockPos, state);
        }
    }

    private void resetProgress(BlockPos blockPos, BlockState state) {
        progress = 0;
    }

    private static boolean hasRecipe(DrainBlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        BlockPos blockPos = blockEntity.getBlockPos();

        BlockPos aboveBlockPos = blockPos.above();
        BlockState aboveBlockState = level.getBlockState(aboveBlockPos);

        if(!(aboveBlockState.getBlock() instanceof BucketPickup))
            return false;

        FluidState fluidState = level.getFluidState(aboveBlockPos);

        if(fluidState.isEmpty())
            return false;

        return blockEntity.fluidStorage.fill(new FluidStack(fluidState.getType(), 1000), IFluidHandler.FluidAction.SIMULATE) == 1000;
    }
}