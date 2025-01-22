package me.jddev0.ep.integration.rei;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.jddev0.ep.api.EPAPI;
import me.jddev0.ep.recipe.EPRecipes;
import me.jddev0.ep.recipe.PlantGrowthChamberFertilizerRecipe;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;
import java.util.Optional;

public record PlantGrowthChamberFertilizerDisplay(RecipeHolder<PlantGrowthChamberFertilizerRecipe> recipe) implements Display {
    public static final CategoryIdentifier<PlantGrowthChamberFertilizerDisplay> CATEGORY = CategoryIdentifier.of(EPAPI.MOD_ID, "plant_growth_chamber_fertilizer");
    public static final DisplaySerializer<? extends PlantGrowthChamberFertilizerDisplay> SERIALIZER = DisplaySerializer.of(
            RecordCodecBuilder.mapCodec((instance) -> {
                return instance.group(ResourceLocation.CODEC.fieldOf("recipeId").forGetter(display -> {
                    return display.recipe.id().location();
                }), EPRecipes.PLANT_GROWTH_CHAMBER_FERTILIZER_SERIALIZER.get().codec().fieldOf("ingredient").forGetter(display -> {
                    return display.recipe.value();
                })).apply(instance, (recipeId, recipe) -> new PlantGrowthChamberFertilizerDisplay(new RecipeHolder<>(
                        ResourceKey.create(Registries.RECIPE, recipeId), recipe
                )));
            }),
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC,
                    display -> display.recipe.id().location(),
                    EPRecipes.PLANT_GROWTH_CHAMBER_FERTILIZER_SERIALIZER.get().streamCodec(),
                    display -> display.recipe.value(),
                    (recipeId, recipe) -> new PlantGrowthChamberFertilizerDisplay(new RecipeHolder<>(
                            ResourceKey.create(Registries.RECIPE, recipeId), recipe
                    ))
            )
    );

    @Override
    public List<EntryIngredient> getInputEntries() {
        return List.of(
                EntryIngredients.ofIngredient(recipe.value().getInput())
        );
    }

    @Override
    public List<EntryIngredient> getOutputEntries() {
        return List.of();
    }

    @Override
    public CategoryIdentifier<PlantGrowthChamberFertilizerDisplay> getCategoryIdentifier() {
        return CATEGORY;
    }

    @Override
    public Optional<ResourceLocation> getDisplayLocation() {
        return Optional.of(recipe.id().location());
    }

    @Override
    public DisplaySerializer<? extends PlantGrowthChamberFertilizerDisplay> getSerializer() {
        return SERIALIZER;
    }
}
