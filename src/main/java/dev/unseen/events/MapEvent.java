package dev.unseen.events;

import dev.unseen.director.Director;
import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * A map turns up in a chest. It's your base, from above, as if drawn in one
 * sitting. There's a red X on your bed. You never made this map.
 */
public class MapEvent implements HauntEvent {
	private static final int SIZE = 128;

	@Override
	public String id() {
		return "map";
	}

	@Override
	public int minDread() {
		return Tuning.MAP_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.MAP_COOLDOWN;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		ServerLevel level = Senses.level(player);
		BlockPos home = FootprintsEvent.homeIn(level, record);
		if (home == null || !Senses.homeLoaded(level, home) || !Senses.farFrom(player, home, Tuning.MAP_MIN_PLAYER_DISTANCE)) {
			return false;
		}
		var containers = Senses.containersNear(level, home);
		if (containers.isEmpty()) {
			return false;
		}

		// A made-up dimension, like the photos use, so the game never redraws it with live terrain.
		MapItemSavedData map = MapItemSavedData.createFresh(home.getX(), home.getZ(), (byte) 0, false, false, Director.DARKROOM);
		paintTerrain(level, home, map.colors);
		paintX(map.colors);

		MapId id = level.getFreeMapId();
		level.setMapData(id, map);
		ItemStack stack = new ItemStack(Items.FILLED_MAP);
		stack.set(DataComponents.MAP_ID, id);
		return Senses.tuckInto(containers, stack, random);
	}

	/** Colors each pixel from the top block of its column. Unloaded ground stays blank, like unexplored map. */
	private static void paintTerrain(ServerLevel level, BlockPos home, byte[] colors) {
		int left = home.getX() - SIZE / 2;
		int top = home.getZ() - SIZE / 2;
		int[] previous = new int[SIZE];
		for (int pz = 0; pz < SIZE; pz++) {
			for (int px = 0; px < SIZE; px++) {
				int x = left + px;
				int z = top + pz;
				if (!level.hasChunkAt(new BlockPos(x, home.getY(), z))) {
					previous[px] = Integer.MIN_VALUE;
					continue;
				}
				int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				BlockPos pos = new BlockPos(x, y, z);
				MapColor color = level.getBlockState(pos).getMapColor(level, pos);

				// Shade by slope against the pixel to the north, the way real maps do.
				MapColor.Brightness brightness = MapColor.Brightness.NORMAL;
				if (previous[px] != Integer.MIN_VALUE) {
					brightness = y > previous[px] ? MapColor.Brightness.HIGH : y < previous[px] ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
				}
				previous[px] = y;
				colors[px + pz * SIZE] = color.getPackedId(brightness);
			}
		}
	}

	/** A thick red X in the middle, where the bed is. */
	private static void paintX(byte[] colors) {
		byte red = MapColor.COLOR_RED.getPackedId(MapColor.Brightness.HIGH);
		int c = SIZE / 2;
		for (int i = -5; i <= 5; i++) {
			for (int w = 0; w <= 1; w++) {
				set(colors, c + i + w, c + i);
				set(colors, c + i + w, c - i);
			}
		}
	}

	private static void set(byte[] colors, int x, int y) {
		if (x >= 0 && x < SIZE && y >= 0 && y < SIZE) {
			colors[x + y * SIZE] = MapColor.COLOR_RED.getPackedId(MapColor.Brightness.HIGH);
		}
	}
}
