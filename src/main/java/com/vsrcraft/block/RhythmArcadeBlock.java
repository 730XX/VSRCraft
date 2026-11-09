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
 * Bloque 3D del Mini-Arcade Mecánico de Redstone.
 * Permite abrir el selector de canciones de VSRCraft al interactuar con clic derecho,
 * cuenta con orientación horizontal en 4 direcciones, VoxelShape a medida y partículas musicales.
 */
public class RhythmArcadeBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<RhythmArcadeBlock> CODEC = simpleCodec(RhythmArcadeBlock::new);

    /**
     * Callback registrado desde el cliente (VSRCraftClient) para abrir SongSelectScreen
     * sin acoplar clases exclusivas de cliente en el source set común.
     */
    public static Runnable clientOpenScreenAction;

    // Silueta 3D compuesta (orientación NORTE por defecto):
    // 1. Pedestal base de piedra negra y cobre
    // 2. Cuerpo inferior con ranura de discos
    // 3. Bandeja de control inclinada con teclas mecánicas
    // 4. Gabinete superior con pantalla CRT y marquesina
    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(1.0, 0.0, 1.0, 15.0, 2.0, 15.0),
            Block.box(2.0, 2.0, 2.0, 14.0, 8.0, 14.0),
            Block.box(1.0, 8.0, 1.0, 15.0, 11.0, 14.0),
            Block.box(1.0, 10.0, 4.0, 15.0, 16.0, 15.0)
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
        // Emitir notas musicales ocasionales sobre la marquesina del arcade
        if (random.nextInt(6) == 0) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            double y = pos.getY() + 1.15;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            double noteColor = random.nextInt(24) / 24.0;
            level.addParticle(ParticleTypes.NOTE, x, y, z, noteColor, 0.0, 0.0);
        }
    }
}
