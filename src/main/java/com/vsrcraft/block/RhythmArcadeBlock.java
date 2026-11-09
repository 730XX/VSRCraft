package com.vsrcraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * Bloque 3D del Arcade Mecánico de Redstone (Arcade Cabinet de pie).
 * Estructura de 2 bloques de alto (LOWER y UPPER) con colisión, selección
 * e interacción completa de clic derecho tanto en botones como en la pantalla CRT.
 */
public class RhythmArcadeBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<RhythmArcadeBlock> CODEC = simpleCodec(RhythmArcadeBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public static Runnable clientOpenScreenAction;

    // Silueta 3D de la mitad inferior (LOWER):
    private static final VoxelShape NORTH_LOWER_SHAPE = Shapes.or(
            Block.box(1.0, 0.0, 1.0, 15.0, 2.0, 15.0),
            Block.box(2.0, 2.0, 3.0, 14.0, 12.0, 14.0),
            Block.box(1.0, 12.0, 1.0, 15.0, 15.5, 14.0),
            Block.box(1.0, 14.0, 6.0, 15.0, 16.0, 14.0)
    );

    // Silueta 3D de la mitad superior (UPPER):
    private static final VoxelShape NORTH_UPPER_SHAPE = Shapes.or(
            Block.box(1.0, 0.0, 6.0, 15.0, 13.0, 14.0),
            Block.box(1.0, 12.0, 4.0, 15.0, 14.0, 15.0)
    );

    private static final Map<Direction, VoxelShape> LOWER_SHAPES = Shapes.rotateHorizontal(NORTH_LOWER_SHAPE);
    private static final Map<Direction, VoxelShape> UPPER_SHAPES = Shapes.rotateHorizontal(NORTH_UPPER_SHAPE);

    public RhythmArcadeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() < level.getMaxY() && level.getBlockState(pos.above()).canBeReplaced(context)) {
            return this.defaultBlockState()
                    .setValue(FACING, context.getHorizontalDirection().getOpposite())
                    .setValue(HALF, DoubleBlockHalf.LOWER);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(
                pos.above(),
                state.setValue(HALF, DoubleBlockHalf.UPPER),
                Block.UPDATE_ALL
        );
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return belowState.isFaceSturdy(level, belowPos, Direction.UP);
        } else {
            return belowState.is(this);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            DoubleBlockHalf half = state.getValue(HALF);
            BlockPos otherPos = (half == DoubleBlockHalf.LOWER) ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(otherPos);
            if (otherState.is(this) && otherState.getValue(HALF) != half) {
                level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, otherPos, Block.getId(otherState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
                                     BlockPos pos, Direction direction, BlockPos neighborPos,
                                     BlockState neighborState, RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y) {
            boolean isPartnerDirection = (half == DoubleBlockHalf.LOWER && direction == Direction.UP)
                    || (half == DoubleBlockHalf.UPPER && direction == Direction.DOWN);
            if (isPartnerDirection) {
                if (!neighborState.is(this) || neighborState.getValue(HALF) == half) {
                    return Blocks.AIR.defaultBlockState();
                }
            }
        }

        if (half == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }

        return super.updateShape(state, level, tickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        DoubleBlockHalf half = state.getValue(HALF);
        Map<Direction, VoxelShape> shapeMap = (half == DoubleBlockHalf.UPPER) ? UPPER_SHAPES : LOWER_SHAPES;
        return shapeMap.getOrDefault(facing, (half == DoubleBlockHalf.UPPER) ? NORTH_UPPER_SHAPE : NORTH_LOWER_SHAPE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            level.playLocalSound(
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS,
                    0.8f,
                    1.2f,
                    false
            );
            if (clientOpenScreenAction != null) {
                clientOpenScreenAction.run();
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Emitir partículas de notas solo sobre la marquesina del bloque superior
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER && random.nextInt(5) == 0) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            double y = pos.getY() + 0.95;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            double noteColor = random.nextInt(24) / 24.0;
            level.addParticle(ParticleTypes.NOTE, x, y, z, noteColor, 0.0, 0.0);
        }
    }
}
