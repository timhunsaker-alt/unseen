package dev.unseen.voice;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Your controls over your own voice. Anyone can run these for themselves.
 *
 *   /unseenvoice optin            let it record short clips of you and make phrases in your voice
 *   /unseenvoice optout           stop, and delete every clip, phrase and cloned voice of yours
 *   /unseenvoice status           what it has of yours
 *   /unseenvoice nickname <text>  how your name should be said ("Timmy" for tim_h)
 *   /unseenvoice build            (op) make your phrases now instead of waiting
 */
public final class VoiceCommand {
	private VoiceCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("unseenvoice")
				.then(Commands.literal("optin").executes(VoiceCommand::optIn))
				.then(Commands.literal("optout").executes(VoiceCommand::optOut))
				.then(Commands.literal("status").executes(VoiceCommand::status))
				.then(Commands.literal("nickname")
						.then(Commands.argument("text", StringArgumentType.greedyString()).executes(VoiceCommand::nickname)))
				.then(Commands.literal("build")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(VoiceCommand::build)));
	}

	private static int optIn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		VoiceStore store = UnseenVoice.store();
		if (store == null) {
			return fail(ctx);
		}
		store.optIn(player.getUUID());
		say(ctx, "It's listening now. Short clips of your voice are saved on this server, and phrases may be "
				+ "made in your voice. Nothing goes anywhere except this server"
				+ (UnseenVoice.config().hasVoiceServer() ? " and the voice server you set up." : ".")
				+ " /unseenvoice optout deletes all of it.");
		return 1;
	}

	private static int optOut(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		VoiceStore store = UnseenVoice.store();
		if (store == null) {
			return fail(ctx);
		}
		store.optOutAndWipe(player.getUUID());
		if (UnseenVoice.recorder() != null) {
			UnseenVoice.recorder().forget(player.getUUID());
		}
		say(ctx, "Done. Everything of yours has been deleted from this server. (A cloned voice made on the voice "
				+ "server stays there until its owner deletes the profile named unseen-" + player.getUUID().toString().substring(0, 8) + ".)");
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		VoiceStore store = UnseenVoice.store();
		PhraseBank bank = UnseenVoice.bank();
		if (store == null || bank == null) {
			return fail(ctx);
		}
		UUID id = player.getUUID();
		String name = player.getName().getString();
		int clips = store.clips(id).size();
		int phrases = store.bank(id).size();
		int wanted = bank.phrases(id, name).size();
		String msg = (store.isOptedIn(id) ? "Recording: on" : "Recording: off (/unseenvoice optin)")
				+ " | clips " + clips + " (" + Math.round(bank.referenceSeconds(id)) + "s)"
				+ " | voice " + (store.player(id).voice != null ? "made" : "not yet")
				+ " | phrases " + phrases + "/" + wanted
				+ " | voice server " + (UnseenVoice.config().hasVoiceServer() ? "set" : "not set");
		say(ctx, msg);
		return 1;
	}

	private static int nickname(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		VoiceStore store = UnseenVoice.store();
		if (store == null) {
			return fail(ctx);
		}
		String text = StringArgumentType.getString(ctx, "text").strip();
		store.setNickname(player.getUUID(), text);
		// Phrases made with the old name are no longer wanted; they'll be remade with the new one.
		say(ctx, "Okay. It will say \"" + text + "\".");
		return 1;
	}

	private static int build(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		PhraseBank bank = UnseenVoice.bank();
		if (bank == null) {
			return fail(ctx);
		}
		if (!UnseenVoice.config().hasVoiceServer()) {
			say(ctx, "No voice server is set. Put its address in config/unseen-voice.json.");
			return 0;
		}
		boolean started = bank.request(player.getUUID(), player.getName().getString());
		say(ctx, started ? "Making phrases. It can take a while; check /unseenvoice status."
				: "Nothing to do: not opted in, or already building.");
		return started ? 1 : 0;
	}

	private static int fail(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendFailure(Component.literal("The voice side isn't running yet."));
		return 0;
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String message) {
		ctx.getSource().sendSuccess(() -> Component.literal(message), false);
	}
}
