package dev.flomik.farmerscontracts.api.event;

import dev.flomik.farmerscontracts.contract.GeneratedContract;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fired whenever a player successfully turns in a contract (ticket or sealed box), after
 * rewards/XP have already been granted. Intended for other mods that want to react to contract
 * completions (e.g. award their own currency/leveling points) - see {@link GeneratedContract#rarity()}
 * for the completed contract's tier.
 */
public interface ContractFulfilledCallback {

    Event<ContractFulfilledCallback> EVENT = EventFactory.createArrayBacked(ContractFulfilledCallback.class,
            listeners -> (player, contract) -> {
                for (ContractFulfilledCallback listener : listeners) {
                    listener.onContractFulfilled(player, contract);
                }
            });

    void onContractFulfilled(ServerPlayer player, GeneratedContract contract);
}
