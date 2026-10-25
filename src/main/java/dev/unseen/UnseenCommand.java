package dev.unseen;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.unseen.director.Director;
import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Tuning;
import dev.unseen.events.HauntEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Developer tools for testing scares without waiting days of game time.
 * Needs cheats / operator. Remove or keep secret before sharing the mod.
 *
 *   /unseen status
 *   /unseen trigger <footprints|torch|door|footsteps|photo>
 *   /unseen dread <0-5>      (or -1 to go back to normal)
 */
public final class UnseenCommand {
	private UnseenCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("unseen")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("status").executes(UnseenCommand::status))
				.then(Commands.literal("trigger")
						.then(Commands.argument("event", StringArgumentType.word())
								.suggests((ctx, builder) -> {
									for (HauntEvent event : Director.get().events()) {
										builder.suggest(event.id());
									}
									builder.suggest("photo");
									return builder.buildFuture();
								})
								.executes(UnseenCommand::trigger)))
				.then(Commands.literal("dread")
						.then(Commands.argument("level", IntegerArgumentType.integer(-1, Tuning.MAX_DREAD))
								.executes(UnseenCommand::dread))));
	}

	private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		PlayerRecord record = Director.get().record(player);
		if (record == null) {
			return 0;
		}
		String home = record.homePos == null ? "unknown" : net.minecraft.core.BlockPos.of(record.homePos).toShortString();
		long quietFor = (Director.get().now() - record.lastEventAt) / Tuning.SECOND;
		String msg = "dread " + record.dread() + "/" + Tuning.MAX_DREAD
				+ " | watched for " + (record.activeTicks / Tuning.MINUTE) + " min"
				+ " | home " + home
				+ " | last event " + (quietFor > 1_000_000 ? "never" : quietFor + "s ago")
				+ " | photos waiting " + record.undeliveredPhotos.size();
		ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
		return 1;
	}

	private static int trigger(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		PlayerRecord record = Director.get().record(player);
		String id = StringArgumentType.getString(ctx, "event");
		if (record == null) {
			return 0;
		}

		if (id.equals("photo")) {
			record.forcePhotoNextSleep = true;
			ctx.getSource().sendSuccess(() -> Component.literal("Next time you sleep, it takes a picture."), false);
			return 1;
		}

		for (HauntEvent event : Director.get().events()) {
			if (event.id().equals(id)) {
				boolean happened = Director.get().runEvent(player, record, event, Director.get().now());
				String result = happened
						? "Done. Go look."
						: "Couldn't happen right now (wrong place, no snow, no home yet, or you're looking).";
				ctx.getSource().sendSuccess(() -> Component.literal(id + ": " + result), false);
				return happened ? 1 : 0;
			}
		}
		ctx.getSource().sendFailure(Component.literal("Unknown event: " + id));
		return 0;
	}

	private static int dread(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		PlayerRecord record = Director.get().record(player);
		if (record == null) {
			return 0;
		}
		record.dreadOverride = IntegerArgumentType.getInteger(ctx, "level");
		ctx.getSource().sendSuccess(() -> Component.literal("Dread is now " + record.dread()), false);
		return 1;
	}
}
