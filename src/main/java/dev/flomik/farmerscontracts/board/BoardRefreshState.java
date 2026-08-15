package dev.flomik.farmerscontracts.board;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record BoardRefreshState(long lastUpdateGameTime, boolean initialized, List<Long> slotTimestamps) {
    public static final Codec<BoardRefreshState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("last_update").forGetter(BoardRefreshState::lastUpdateGameTime),
            Codec.BOOL.fieldOf("initialized").forGetter(BoardRefreshState::initialized),
            Codec.LONG.listOf().fieldOf("slot_timestamps").forGetter(BoardRefreshState::slotTimestamps)
    ).apply(instance, BoardRefreshState::new));
}
