package dev.unseen;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.unseen.director.Director;
import dev.unseen.director.HauntLog;
import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Tuning;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lets a player see what it has done to them, if they want to. It never says anything on its own:
 * this is only an answer to a question.
 *
 *   /unseenlog              the last 10 things
 *   /unseenlog <number>     the last N (up to 50)
 *   /unseenlog clear        forget what's written about you
 *   /unseenlog player <name> [number]    (op) read someone else's
 *
 * Server owners can switch off players reading their own with Tuning.PLAYER_CAN_LOOK_UP_LOG.
 */
public final class UnseenLogCommand {
	private UnseenLogCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		var operator = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
		dispatcher.register(Commands.literal("unseenlog")
				.requires(source -> Tuning.PLAYER_CAN_LOOK_UP_LOG || operator.test(source))
				.executes(ctx -> show(ctx, ctx.getSource().getPlayerOrException(), Tuning.LOG_DEFAULT_LINES))
				.then(Commands.argument("count", IntegerArgumentType.integer(1, Tuning.LOG_MAX_LINES))
						.executes(ctx -> show(ctx, ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "count"))))
				.then(Commands.literal("clear").executes(UnseenLogCommand::clear))
				.then(Commands.literal("player")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("target", EntityArgument.player())
								.executes(ctx -> show(ctx, EntityArgument.getPlayer(ctx, "target"), Tuning.LOG_DEFAULT_LINES))
								.then(Commands.argument("count", IntegerArgumentType.integer(1, Tuning.LOG_MAX_LINES))
										.executes(ctx -> show(ctx, EntityArgument.getPlayer(ctx, "target"), IntegerArgumentType.getInteger(ctx, "count")))))));
	}

	private static int show(CommandContext<CommandSourceStack> ctx, ServerPlayer target, int count) {
		PlayerRecord record = Director.get().record(target);
		if (record == null) {
			ctx.getSource().sendFailure(Component.literal("Nothing is written down yet."));
			return 0;
		}
		String text = HauntLog.format(record, count);
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		PlayerRecord record = Director.get().record(player);
		if (record == null) {
			return 0;
		}
		record.history.clear();
		ctx.getSource().sendSuccess(() -> Component.literal("Cleared. What happens next will still be written down."), false);
		return 1;
	}
}
