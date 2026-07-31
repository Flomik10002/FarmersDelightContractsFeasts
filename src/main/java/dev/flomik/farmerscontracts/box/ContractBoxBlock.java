package dev.flomik.farmerscontracts.box;

import com.mojang.serialization.MapCodec;
import dev.flomik.farmerscontracts.Config;
import dev.flomik.farmerscontracts.FarmersContractsMod;
import dev.flomik.farmerscontracts.contract.ContractDataComponents;
import dev.flomik.farmerscontracts.contract.GeneratedContract;
import dev.flomik.farmerscontracts.contract.GeneratedLine;
import dev.flomik.farmerscontracts.item.ContractTicketItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ContractBoxBlock extends BaseEntityBlock {

    public static final BooleanProperty SEALED = BooleanProperty.create("sealed");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.box(3.5, 0, 3.5, 12.5, 9, 12.5);

    public ContractBoxBlock(Properties properties) {
        super(properties.sound(SoundType.WOOD).strength(1.5F));
        registerDefaultState(stateDefinition.any().setValue(SEALED, false).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(ContractBoxBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SEALED, FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ContractBoxBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean sealed = context.getItemInHand().has(ContractDataComponents.CONTRACT_DATA.get());
        return super.getStateForPlacement(context)
                .setValue(SEALED, sealed)
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof MenuProvider provider ? provider : null;
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        if (player.isShiftKeyDown()
                || state.getValue(SEALED)
                || !(stack.getItem() instanceof ContractTicketItem)
                || Config.deliveryMode() == Config.DeliveryMode.TICKET_ONLY) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        return trySeal((ServerLevel) level, (ServerPlayer) player, pos, stack)
                ? ItemInteractionResult.CONSUME
                : ItemInteractionResult.FAIL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(SEALED)) {
            player.sendSystemMessage(Component.translatable("chat.farmerscontracts.box_locked"));
            return InteractionResult.CONSUME;
        }

        MenuProvider menuProvider = state.getMenuProvider(level, pos);
        if (menuProvider != null && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(menuProvider);
        }
        return InteractionResult.CONSUME;
    }

    // Public (not package-private, unlike ContractBoardBlock.tryTurnIn) so SelfTest - which lives
    // in dev.flomik.farmerscontracts.board, not this package - can exercise it directly.
    public boolean trySeal(ServerLevel level, ServerPlayer player, BlockPos pos, ItemStack ticket) {
        if (!(level.getBlockEntity(pos) instanceof ContractBoxBlockEntity box)) {
            return false;
        }

        GeneratedContract contract = ticket.get(ContractDataComponents.CONTRACT_DATA.get());
        if (contract == null) {
            return false;
        }

        if (contract.isExpired(level.getGameTime())) {
            ticket.shrink(1);
            player.sendSystemMessage(Component.translatable("chat.farmerscontracts.expired", ContractTicketItem.customerName(contract)));
            return true;
        }

        // See ContractBoardBlock.tryTurnIn for why objectives are merged by item first.
        List<GeneratedLine> objectives = GeneratedLine.mergeByItem(contract.objectives());

        // The ticket is proof that the box's contents are exactly this order, nothing else - so
        // any item that isn't part of the order at all, or an amount that's off in either
        // direction, fails the seal (docs/contract_box.md: "все предметы внутри соответствуют
        // ТОЛЬКО этому заказу").
        Set<Item> allowedItems = new HashSet<>();
        for (GeneratedLine objective : objectives) {
            allowedItems.add(objective.stack().getItem());
        }
        for (int i = 0; i < box.getContainerSize(); i++) {
            ItemStack stack = box.getItem(i);
            if (!stack.isEmpty() && !allowedItems.contains(stack.getItem())) {
                player.sendSystemMessage(Component.translatable("chat.farmerscontracts.box_mismatch", describe(objectives)));
                return false;
            }
        }

        boolean exactMatch = true;
        for (GeneratedLine objective : objectives) {
            if (countMatching(box, objective.stack().getItem()) != objective.amount()) {
                exactMatch = false;
                break;
            }
        }

        if (!exactMatch) {
            player.sendSystemMessage(Component.translatable("chat.farmerscontracts.box_mismatch", describe(objectives)));
            return false;
        }

        for (GeneratedLine objective : objectives) {
            consumeMatching(box, objective.stack().getItem(), objective.amount());
        }

        box.seal(contract);
        level.setBlock(pos, level.getBlockState(pos).setValue(SEALED, true), 3);
        ticket.shrink(1);
        player.sendSystemMessage(Component.translatable("chat.farmerscontracts.box_sealed", ContractTicketItem.customerName(contract)));
        return true;
    }

    private static int countMatching(Container container, Item item) {
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void consumeMatching(Container container, Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
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
