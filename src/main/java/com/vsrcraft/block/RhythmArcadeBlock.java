package com.vsrcraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * Bloque 3D del Arcade Mecánico de Redstone (Arcade Cabinet de pie).
 * Altura imponente de casi 2 bloques (30 píxeles), con pantalla vertical CRT nítida,
 * bandeja de botones mecánicos con relieve y laterales de madera noble con acabados de bronce.
 */
public class RhythmArcadeBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<RhythmArcadeBlock> CODEC = simpleCodec(RhythmArcadeBlock::new);

    public static Runnable clientOpenScreenAction;

    // Silueta 3D de pie (orientación NORTE por defecto, hasta y=30 px):
    // 1. Pedestal base de piedra negra: [1, 0, 1] a [15, 2, 15]
    // 2. Gabinete inferior: [2, 2, 3] a [14, 12, 14]
    // 3. Bandeja de control y botones: [1, 12, 1] a [15, 15.5, 14]
    // 4. Cabina superior con monitor CRT vertical y marquesina: [1, 14, 4] a [15, 30, 15]
    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(1.0, 0.0, 1.0, 15.0, 2.0, 15.0),
            Block.box(2.0, 2.0, 3.0, 14.0, 12.0, 14.0),
            Block.box(1.0, 12.0, 1.0, 15.0, 15.5, 14.0),
            Block.box(1.0, 14.0, 4.0, 15.0, 30.0, 15.0)
    );

    private static final Map<Direction, VoxelShape> SHAPES_BY_DIRECTION = Shapes.rotateHorizontal(NORTH_SHAPE);

    public RhythmArcadeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return SHAPES_BY_DIRECTION.getOrDefault(facing, NORTH_SHAPE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            level.playLocalSound(
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
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
        // Emitir partículas de notas sobre la marquesina superior (y ≈ 1.9 bloques)
        if (random.nextInt(5) == 0) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            double y = pos.getY() + 1.95;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            double noteColor = random.nextInt(24) / 24.0;
            level.addParticle(ParticleTypes.NOTE, x, y, z, noteColor, 0.0, 0.0);
        }
    }
}
