package dev.flomik.farmerscontracts.contract;

import dev.flomik.farmerscontracts.FarmersContractsMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ContractDataComponents {

    private ContractDataComponents() {
    }

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, FarmersContractsMod.MODID);

    public static final net.neoforged.neoforge.registries.DeferredHolder<DataComponentType<?>, DataComponentType<GeneratedContract>> CONTRACT_DATA =
            COMPONENTS.register("contract_data", () -> DataComponentType.<GeneratedContract>builder()
                    .persistent(GeneratedContract.CODEC)
                    .networkSynchronized(GeneratedContract.STREAM_CODEC)
                    .build());
}
