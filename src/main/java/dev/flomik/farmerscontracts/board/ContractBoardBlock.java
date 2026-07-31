package dev.flomik.farmerscontracts.board;

import dev.flomik.farmerscontracts.Config;
import dev.flomik.farmerscontracts.FarmersContractsMod;
import dev.flomik.farmerscontracts.box.ContractBoxBlockEntity;
import dev.flomik.farmerscontracts.contract.ContractProgress;
import dev.flomik.farmerscontracts.contract.GeneratedContract;
import dev.flomik.farmerscontracts.contract.GeneratedLine;
import dev.flomik.farmerscontracts.item.ContractTicketItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

public class ContractBoardBlock extends BaseEntityBlock {

    // Explosion resistance matches Bountiful's board exactly (BoardBlock.kt:
    // destroyTime(3f).explosionResistance(3600000f), the same figure vanilla uses for bedrock/
    // portal frames) - the board survives TNT/creepers even though it can still be mined normally.
    public ContractBoardBlock(Properties properties) {
        super(properties.sound(SoundType.WOOD).strength(2.5F, 3_600_000F));
    }

    // Refuse to even start breaking the block if the config disallows it - ported from
    // Bountiful's BoardBlock.getDestroyProgress (config board.canBreak).
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!Config.boardCanBreak()) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ContractBoardBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? null
                : createTickerHelper(type, FarmersContractsMod.CONTRACT_BOARD_ENTITY.get(), ContractBoardBlockEntity::tick);
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof MenuProvider provider ? provider : null;
    }

    // Carries the block entity's ENTIRE saveAdditional NBT onto the dropped item (items,
    // lastUpdateGameTime, initialized, slotTimestamps, masks) so break+replace at the same
    // position is a no-op - both for the container contents AND the Bountiful-ported refresh
    // timer (which would otherwise reset to 0 and look like an instant-reroll exploit).
    // BlockItem.updateCustomBlockEntityTag restores it automatically on the next placement - the
    // pre-components equivalent of the loot_table copy_components function used on the NeoForge
    // 1.21.1 side (see ContractBoxBlock's own getDrops override for the same pattern).
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof ContractBoardBlockEntity board) {
            ItemStack stack = new ItemStack(FarmersContractsMod.CONTRACT_BOARD_ITEM.get());
            CompoundTag tag = board.saveWithoutMetadata();
            if (!tag.isEmpty()) {
                stack.addTagElement("BlockEntityTag", tag);
            }
            return List.of(stack);
        }
        return super.getDrops(state, params);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        ItemStack stack = player.getItemInHand(hand);
        boolean isTicket = stack.getItem() instanceof ContractTicketItem;
        boolean isSealedBox = !isTicket
                && stack.is(FarmersContractsMod.CONTRACT_BOX_ITEM.get())
                && ContractBoxBlockEntity.sealedContractOf(stack) != null;

        if ((isTicket && Config.deliveryMode() != Config.DeliveryMode.BOX_ONLY)
                || (isSealedBox && Config.deliveryMode() != Config.DeliveryMode.TICKET_ONLY)) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            boolean turnedIn = isTicket
                    ? tryTurnIn((ServerLevel) level, (ServerPlayer) player, stack)
                    : tryDeliverBox((ServerLevel) level, (ServerPlayer) player, stack);
            return turnedIn ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        MenuProvider menuProvider = state.getMenuProvider(level, pos);
        if (menuProvider != null && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(menuProvider);
        }
        return InteractionResult.CONSUME;
    }

    // Package-private (not private) so SelfTest can exercise it directly without going through
    // the full block-interaction/BlockHitResult plumbing.
    boolean tryTurnIn(ServerLevel level, ServerPlayer player, ItemStack ticket) {
        GeneratedContract contract = ContractTicketItem.dataOf(ticket);
        if (contract == null) {
            return false;
        }

        if (contract.isExpired(level.getGameTime())) {
            ticket.shrink(1);
            player.sendSystemMessage(Component.translatable("chat.farmerscontracts.expired", ContractTicketItem.customerName(contract)));
            return true;
        }

        // Merge by item first: ContractGenerator can legitimately produce two objective lines
        // for the same item (e.g. once a pool's "unused item" pick is exhausted, it falls back to
        // reusing one already picked). Checking/consuming raw per-line amounts against the
        // player's inventory independently would under-count the real total needed - e.g. two
        // lines of 5 and 3 wheat would each individually pass with only 5 wheat on hand instead
        // of requiring 8, and consumption would then silently come up short on the second line.
        List<GeneratedLine> objectives = GeneratedLine.mergeByItem(contract.objectives());

        List<GeneratedLine> missing = new ArrayList<>();
        for (GeneratedLine objective : objectives) {
            Item item = objective.stack().getItem();
            if (countMatching(player, item) < objective.amount()) {
                missing.add(objective);
            }
        }

        if (!missing.isEmpty()) {
            player.sendSystemMessage(Component.translatable("chat.farmerscontracts.still_needed", describe(missing)));
            return false;
        }

        for (GeneratedLine objective : objectives) {
            consumeMatching(player, objective.stack().getItem(), objective.amount());
        }

        for (GeneratedLine reward : contract.rewards()) {
            giveOrDrop(level, player, reward.stack().copyWithCount(reward.amount()));
        }
        player.giveExperiencePoints(contract.xp());

        ticket.shrink(1);
        ContractProgress.get(level).incrementCompleted();
        player.sendSystemMessage(Component.translatable("chat.farmerscontracts.fulfilled", ContractTicketItem.customerName(contract)));
        return true;
    }

    // Package-private (not private) so SelfTest can exercise it directly. Delivering a sealed
    // Contract Box (see dev.flomik.farmerscontracts.box) needs no inventory check here - the box
    // already validated and consumed its contents when it was sealed (ContractBoxBlock.trySeal);
    // carrying its sealed contract data to the board is proof enough.
    boolean tryDeliverBox(ServerLevel level, ServerPlayer player, ItemStack box) {
        GeneratedContract contract = ContractBoxBlockEntity.sealedContractOf(box);
        if (contract == null) {
            return false;
        }

        if (contract.isExpired(level.getGameTime())) {
            box.shrink(1);
            player.sendSystemMessage(Component.translatable("chat.farmerscontracts.expired", ContractTicketItem.customerName(contract)));
            return true;
        }

        for (GeneratedLine reward : contract.rewards()) {
            giveOrDrop(level, player, reward.stack().copyWithCount(reward.amount()));
        }
        player.giveExperiencePoints(contract.xp());

        box.shrink(1);
        ContractProgress.get(level).incrementCompleted();
        player.sendSystemMessage(Component.translatable("chat.farmerscontracts.fulfilled", ContractTicketItem.customerName(contract)));
        return true;
    }

    private static int countMatching(Player player, Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void consumeMatching(Player player, Item item, int amount) {
        int remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) {
                break;
            }
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }

    private static void giveOrDrop(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (!player.addItem(stack)) {
            ItemEntity entity = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), stack);
            entity.setPickUpDelay(0);
            level.addFreshEntity(entity);
        }
    }

    private static Component describe(List<GeneratedLine> lines) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                result.append(", ");
            }
            GeneratedLine line = lines.get(i);
            result.append(Component.translatable("chat.farmerscontracts.line_amount", line.amount(), line.stack().getHoverName()));
        }
        return result;
    }
}
