package me.jddev0.ep.screen;

import me.jddev0.ep.EnergizedPowerMod;
import me.jddev0.ep.screen.base.UpgradableEnergyStorageContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SolarPanelScreen
        extends UpgradableEnergyStorageContainerScreen<SolarPanelMenu> {
    public SolarPanelScreen(SolarPanelMenu menu, Inventory inventory, Component component) {
        super(menu, inventory, component,
                ResourceLocation.fromNamespaceAndPath(EnergizedPowerMod.MODID, "textures/gui/container/upgrade_view/1_energy_capacity_1_moon_light.png"));

        energyMeterX = 80;
    }
}
