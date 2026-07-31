package dev.flomik.farmerscontracts.board;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

// Everything ContractBoardBlockEntity's Bountiful-ported refresh cycle needs to keep behaving
// correctly across a chunk unload/reload AND across a break+replace at the same position (see
// ContractDataComponents.BOARD_STATE, carried alongside DataComponents.CONTAINER the same way
// ContractBoxBlockEntity carries its sealed contract). slotTimestamps is a flat list parallel to
// the container's slots (0 = never filled), matching Bountiful's Map<Int, Long> bountyTimestamps.
public record BoardRefreshState(long lastUpdateGameTime, boolean initialized, List<Long> slotTimestamps) {

    public static final Codec<BoardRefreshState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("last_update").forGetter(BoardRefreshState::lastUpdateGameTime),
            Codec.BOOL.fieldOf("initialized").forGetter(BoardRefreshState::initialized),
            Codec.LONG.listOf().fieldOf("slot_timestamps").forGetter(BoardRefreshState::slotTimestamps)
    ).apply(instance, BoardRefreshState::new));
}
