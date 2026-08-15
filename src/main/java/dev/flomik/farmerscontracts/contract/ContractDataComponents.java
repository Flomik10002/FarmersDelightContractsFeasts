package dev.flomik.farmerscontracts.contract;

import dev.flomik.farmerscontracts.FarmersContractsMod;
import dev.flomik.farmerscontracts.board.BoardRefreshState;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

public final class ContractDataComponents {
    private ContractDataComponents() {
    }

    public static final DataComponentType<GeneratedContract> CONTRACT_DATA = register(
            "contract_data",
            DataComponentType.<GeneratedContract>builder()
                    .persistent(GeneratedContract.CODEC)
                    .networkSynchronized(GeneratedContract.STREAM_CODEC)
                    .build());

    public static final DataComponentType<BoardRefreshState> BOARD_STATE = register(
            "board_state",
            DataComponentType.<BoardRefreshState>builder()
                    .persistent(BoardRefreshState.CODEC)
                    .build());

    private static <T> DataComponentType<T> register(String name, DataComponentType<T> type) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                ResourceLocation.fromNamespaceAndPath(FarmersContractsMod.MODID, name), type);
    }
}
