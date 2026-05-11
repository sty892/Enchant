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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class KeyholeBlock extends Block implements EntityBlock {
    private static final Gson GSON = new Gson();
    private static final int KEYHOLE_COUNT = 8;
    private static final int ALL_INSERTED_SCAN_RADIUS = 16;
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");

    private final int slot;
    private final Supplier<BlockEntityType<? extends KeyholeBlockEntity>> blockEntityType;

    public KeyholeBlock(Properties properties, int slot, Supplier<BlockEntityType<? extends KeyholeBlockEntity>> blockEntityType) {
        super(properties);
        if (slot < 1 || slot > KEYHOLE_COUNT) {
            throw new IllegalArgumentException("Keyhole slot must be 1..8, got " + slot);
        }
        this.slot = slot;
        this.blockEntityType = blockEntityType;
        this.registerDefaultState(this.stateDefinition.any().setValue(FILLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILLED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityType.get().create(pos, state);
    }

    public int slot() {
        return slot;
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

        if (state.getValue(FILLED)) {
            return InteractionResult.SUCCESS;
        }

        JsonObject config = readKeysConfig();
        if (config == null || !config.has("keys") || !config.get("keys").isJsonArray()) {
            return InteractionResult.PASS;
        }

        Identifier heldItemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        JsonObject keyConfig = findKeyConfig(config.getAsJsonArray("keys"), heldItemId);
        if (keyConfig == null) {
            return InteractionResult.PASS;
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        BlockState nextState = state.setValue(FILLED, true);
        level.setBlock(pos, nextState, 3);
        keyholeBe.setFilled(true);

        if (isKeyWhitelisted(player)) {
            return InteractionResult.SUCCESS;
        }

        executeConfiguredEvent(level, pos, player, keyConfig, "on_insert", "on_insert_script");
        if (areAllKeyholesFilledNearby(level, pos)) {
            executeConfiguredEvent(level, pos, player, config, "on_all_inserted", "on_all_inserted_script");
        }

        return InteractionResult.SUCCESS;
    }

    private void executeConfiguredEvent(Level level, BlockPos pos, Player player, JsonObject config, String eventKey, String scriptKey) {
        if (config.has(eventKey) && config.get(eventKey).isJsonObject()) {
            GuardianEventExecutor.execute(level, config.getAsJsonObject(eventKey), pos, player);
        }
        if (config.has(scriptKey) && config.get(scriptKey).isJsonPrimitive()) {
            JsonObject event = new JsonObject();
            event.addProperty("script", config.get(scriptKey).getAsString());
            GuardianEventExecutor.execute(level, event, pos, player);
        }
    }

    private JsonObject readKeysConfig() {
        try {
            return GSON.fromJson(ConfigManager.readRaw("keys_config.json"), JsonObject.class);
        } catch (IOException | RuntimeException e) {
            GuardianMod.LOGGER.warn("Failed to read keys_config.json", e);
            return null;
        }
    }

    private boolean isKeyWhitelisted(Player player) {
        try {
            JsonObject config = GSON.fromJson(ConfigManager.readRaw("guardian_config.json"), JsonObject.class);
            if (config == null || !config.has("key_whitelist") || !config.get("key_whitelist").isJsonArray()) {
                return false;
            }
            for (JsonElement entry : config.getAsJsonArray("key_whitelist")) {
                if (player.getUUID().toString().equalsIgnoreCase(entry.getAsString())) {
                    return true;
                }
            }
        } catch (IOException | RuntimeException e) {
            GuardianMod.LOGGER.warn("Failed to read key whitelist", e);
        }
        return false;
    }

    private JsonObject findKeyConfig(JsonArray keys, Identifier heldItemId) {
        for (JsonElement element : keys) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject key = element.getAsJsonObject();
            if (!key.has("item_id")) {
                continue;
            }

            Identifier configuredItemId = Identifier.tryParse(key.get("item_id").getAsString());
            if (heldItemId.equals(configuredItemId) && configuredSlot(key) == slot) {
                return key;
            }
        }
        return null;
    }

    private int configuredSlot(JsonObject key) {
        if (key.has("slot")) {
            return key.get("slot").getAsInt();
        }
        if (key.has("keyhole_slot")) {
            return key.get("keyhole_slot").getAsInt();
        }
        if (key.has("keyhole_stage")) {
            return key.get("keyhole_stage").getAsInt();
        }
        if (key.has("keyhole_id")) {
            String value = key.get("keyhole_id").getAsString();
            int separator = value.lastIndexOf('_');
            if (separator >= 0 && separator + 1 < value.length()) {
                try {
                    return Integer.parseInt(value.substring(separator + 1));
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private boolean areAllKeyholesFilledNearby(Level level, BlockPos center) {
        Set<Integer> filledSlots = new HashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = -ALL_INSERTED_SCAN_RADIUS; x <= ALL_INSERTED_SCAN_RADIUS; x++) {
            for (int y = -ALL_INSERTED_SCAN_RADIUS; y <= ALL_INSERTED_SCAN_RADIUS; y++) {
                for (int z = -ALL_INSERTED_SCAN_RADIUS; z <= ALL_INSERTED_SCAN_RADIUS; z++) {
                    cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockState candidate = level.getBlockState(cursor);
                    Block block = candidate.getBlock();
                    if (block instanceof KeyholeBlock keyhole && candidate.hasProperty(FILLED) && candidate.getValue(FILLED)) {
                        filledSlots.add(keyhole.slot);
                        if (filledSlots.size() == KEYHOLE_COUNT) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
