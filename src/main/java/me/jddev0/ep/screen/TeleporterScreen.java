package me.jddev0.ep.screen;

import me.jddev0.ep.EnergizedPowerMod;
import me.jddev0.ep.screen.base.EnergyStorageContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TeleporterScreen extends EnergyStorageContainerScreen<TeleporterMenu> {
    public TeleporterScreen(TeleporterMenu menu, Inventory inventory, Component component) {
        super(menu, inventory, component,
                ResourceLocation.fromNamespaceAndPath(EnergizedPowerMod.MODID, "textures/gui/container/teleporter.png"));
    }
}
