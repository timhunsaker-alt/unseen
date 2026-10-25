package dev.unseen.item;

import dev.unseen.director.Director;
import dev.unseen.director.HauntLog;
import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Tuning;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A steak that cooked itself. Eaten, it is a steak, and then one of three things happens:
 * nothing more, you feel sick, or you are made whole again and left starving.
 * None of them can hurt you.
 */
public class SuspiciousSteakItem extends Item {
	public enum Outcome {
		/** Just a steak. */
		NORMAL,
		/** A steak, then nausea. */
		NAUSEA,
		/** Full health, but the hunger bar drops to 1 or 2. */
		FULL_HEARTS
	}

	public SuspiciousSteakItem(Properties properties) {
		super(properties);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		ItemStack result = super.finishUsingItem(stack, level, entity); // eaten like any steak first
		if (entity instanceof ServerPlayer player) {
			apply(player, roll(player.getRandom()));
		}
		return result;
	}

	public static Outcome roll(RandomSource random) {
		int total = Tuning.STEAK_WEIGHT_NORMAL + Tuning.STEAK_WEIGHT_NAUSEA + Tuning.STEAK_WEIGHT_FULL_HEARTS;
		int pick = random.nextInt(Math.max(1, total));
		if (pick < Tuning.STEAK_WEIGHT_NORMAL) {
			return Outcome.NORMAL;
		}
		return pick < Tuning.STEAK_WEIGHT_NORMAL + Tuning.STEAK_WEIGHT_NAUSEA ? Outcome.NAUSEA : Outcome.FULL_HEARTS;
	}

	public static void apply(ServerPlayer player, Outcome outcome) {
		String what;
		switch (outcome) {
			case NAUSEA -> {
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, Tuning.STEAK_NAUSEA_TICKS));
				what = "It made you feel sick.";
			}
			case FULL_HEARTS -> {
				player.setHealth(player.getMaxHealth());
				FoodData food = player.getFoodData();
				int span = Tuning.STEAK_HUNGER_MAX - Tuning.STEAK_HUNGER_MIN + 1;
				food.setFoodLevel(Tuning.STEAK_HUNGER_MIN + player.getRandom().nextInt(span));
				food.setSaturation(0.0F);
				what = "You were made whole again, and left starving.";
			}
			default -> what = "It was only a steak.";
		}
		PlayerRecord record = Director.get().record(player);
		if (record != null) {
			HauntLog.add(player, record, "steak", "You ate the suspicious steak. " + what);
		}
	}
}
