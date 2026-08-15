package dev.flomik.farmerscontracts.api.event;

import dev.flomik.farmerscontracts.contract.GeneratedContract;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

public interface ContractFulfilledCallback {
    Event<ContractFulfilledCallback> EVENT = EventFactory.createArrayBacked(ContractFulfilledCallback.class,
            listeners -> (player, contract) -> {
                for (ContractFulfilledCallback listener : listeners) {
                    listener.onContractFulfilled(player, contract);
                }
            });

    void onContractFulfilled(ServerPlayer player, GeneratedContract contract);
}
