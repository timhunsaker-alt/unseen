package dev.unseen.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One footprint pressed into a thin layer of snow.
 *
 * FACING is the direction the walker was heading. LEFT picks the left or right
 * boot, so a trail alternates like real steps. Over time snow fills the print
 * back in (random ticks turn it into a normal snow layer).
 */
public class FootprintBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty LEFT = BooleanProperty.create("left");

	private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 2.0 / 16.0, 1.0);

	/** Roughly 1 in this many random ticks fills a print in. ~1-2 in-game days. */
	private static final int FILL_IN_CHANCE = 30;

	public FootprintBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LEFT, true));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LEFT);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (random.nextInt(FILL_IN_CHANCE) == 0) {
			level.setBlockAndUpdate(pos, Blocks.SNOW.defaultBlockState());
		}
	}
}
