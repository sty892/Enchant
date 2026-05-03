package me.guardian.block;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.guardian.GuardianMod;
import me.guardian.block.entity.KeyholeBlockEntity;
import me.guardian.config.ConfigManager;
import me.guardian.event.GuardianEventExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.io.IOException;
import java.util.function.Supplier;

public class KeyholeBlock extends Block implements EntityBlock {
    private static final Gson GSON = new Gson();
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 8);
    private final Supplier<BlockEntityType<? extends KeyholeBlockEntity>> blockEntityType;

    public KeyholeBlock(Properties properties, Supplier<BlockEntityType<? extends KeyholeBlockEntity>> blockEntityType) {
        super(properties);
        this.blockEntityType = blockEntityType;
        this.registerDefaultState(this.stateDefinition.any().setValue(STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityType.get().create(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof KeyholeBlockEntity keyholeBe) {
            return tryInsertKey(stack, state, level, pos, player, keyholeBe);
        }
        return InteractionResult.SUCCESS;
    }

    private InteractionResult tryInsertKey(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, KeyholeBlockEntity keyholeBe) {
        if (stack.isEmpty()) {
            return InteractionResult.PASS;
        }

        int currentStage = state.getValue(STAGE);
        if (currentStage >= 8) {
            return InteractionResult.SUCCESS;
        }

        JsonObject config = readKeysConfig();
        if (config == null || !config.has("keys") || !config.get("keys").isJsonArray()) {
            return InteractionResult.PASS;
        }

        int nextStage = currentStage + 1;
        Identifier heldItemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        JsonObject keyConfig = findKeyConfig(config.getAsJsonArray("keys"), heldItemId, nextStage);
        if (keyConfig == null) {
            return InteractionResult.PASS;
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        BlockState nextState = state.setValue(STAGE, nextStage);
        level.setBlock(pos, nextState, 3);
        keyholeBe.setStage(nextStage);

        if (keyConfig.has("on_insert") && keyConfig.get("on_insert").isJsonObject()) {
            GuardianEventExecutor.execute(level, keyConfig.getAsJsonObject("on_insert"), pos, player);
        }
        if (nextStage == 8 && config.has("on_all_inserted") && config.get("on_all_inserted").isJsonObject()) {
            GuardianEventExecutor.execute(level, config.getAsJsonObject("on_all_inserted"), pos, player);
        }

        return InteractionResult.SUCCESS;
    }

    private JsonObject readKeysConfig() {
        try {
            return GSON.fromJson(ConfigManager.readRaw("keys_config.json"), JsonObject.class);
        } catch (IOException | RuntimeException e) {
            GuardianMod.LOGGER.warn("Failed to read keys_config.json", e);
            return null;
        }
    }

    private JsonObject findKeyConfig(JsonArray keys, Identifier heldItemId, int nextStage) {
        for (JsonElement element : keys) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject key = element.getAsJsonObject();
            if (!key.has("item_id") || !key.has("keyhole_stage")) {
                continue;
            }

            Identifier configuredItemId = Identifier.tryParse(key.get("item_id").getAsString());
            int configuredStage = key.get("keyhole_stage").getAsInt();
            if (heldItemId.equals(configuredItemId) && configuredStage == nextStage) {
                return key;
            }
        }
        return null;
    }
}
