package me.guardian.block;

import me.guardian.GuardianMod;
import me.guardian.block.entity.AltarBlockEntity;
import me.guardian.event.GuardianAltarRitualHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

public class AltarBlock extends Block implements EntityBlock {
    private final Supplier<BlockEntityType<? extends AltarBlockEntity>> blockEntityType;

    public AltarBlock(Properties properties, Supplier<BlockEntityType<? extends AltarBlockEntity>> blockEntityType) {
        super(properties);
        this.blockEntityType = blockEntityType;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityType.get().create(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            return GuardianAltarRitualHooks.select(serverLevel, pos, serverPlayer, stack);
        }

        return InteractionResult.PASS;
    }

    public static boolean isGuardianFragment(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return GuardianMod.MOD_ID.equals(itemId.getNamespace()) && itemId.getPath().startsWith("fragment_");
    }
}
