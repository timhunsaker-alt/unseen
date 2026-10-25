package dev.unseen;

import dev.unseen.block.FootprintBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class UnseenBlocks {
	public static final ResourceKey<Block> FOOTPRINT_KEY = ResourceKey.create(Registries.BLOCK, Unseen.id("footprint"));

	// A snow layer with a boot-shaped dent in it. No item: players can't get one.
	public static final Block FOOTPRINT = new FootprintBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.SNOW)
			.replaceable()
			.noCollision()
			.strength(0.1F)
			.sound(SoundType.SNOW)
			.randomTicks()
			.noLootTable()
			.pushReaction(PushReaction.POPPED)
			.setId(FOOTPRINT_KEY));

	private UnseenBlocks() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.BLOCK, FOOTPRINT_KEY, FOOTPRINT);
	}
}
