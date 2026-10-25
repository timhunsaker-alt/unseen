package dev.unseen;

import dev.unseen.item.SuspiciousSteakItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.Item;

/**
 * Two items, neither craftable or in the creative menu. They only turn up through the "steak" scare.
 * The raw one looks and is named exactly like raw beef; cooked, it becomes the suspicious steak.
 */
public final class UnseenItems {
	public static final ResourceKey<Item> RAW_SUSPICIOUS_STEAK_KEY = ResourceKey.create(Registries.ITEM, Unseen.id("raw_suspicious_steak"));
	public static final ResourceKey<Item> SUSPICIOUS_STEAK_KEY = ResourceKey.create(Registries.ITEM, Unseen.id("suspicious_steak"));

	public static final Item RAW_SUSPICIOUS_STEAK = new Item(new Item.Properties()
			.food(Foods.BEEF)
			.setId(RAW_SUSPICIOUS_STEAK_KEY));

	/** Nutrition and saturation of a normal steak; the rest is up to SuspiciousSteakItem. */
	public static final Item SUSPICIOUS_STEAK = new SuspiciousSteakItem(new Item.Properties()
			.food(Foods.COOKED_BEEF)
			.setId(SUSPICIOUS_STEAK_KEY));

	private UnseenItems() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ITEM, RAW_SUSPICIOUS_STEAK_KEY, RAW_SUSPICIOUS_STEAK);
		Registry.register(BuiltInRegistries.ITEM, SUSPICIOUS_STEAK_KEY, SUSPICIOUS_STEAK);
	}
}
