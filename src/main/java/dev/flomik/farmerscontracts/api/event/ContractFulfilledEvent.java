package dev.flomik.farmerscontracts.api.event;

import dev.flomik.farmerscontracts.contract.ContractRarity;
import dev.flomik.farmerscontracts.contract.GeneratedContract;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * Fired on {@link net.minecraftforge.common.MinecraftForge#EVENT_BUS} whenever a player
 * successfully turns in a contract (ticket or sealed box), after rewards/XP have already
 * been granted. Purely a notification - not cancellable - intended for other mods that want
 * to react to contract completions (e.g. award their own currency/leveling points).
 */
public class ContractFulfilledEvent extends Event {

    private final ServerPlayer player;
    private final GeneratedContract contract;

    public ContractFulfilledEvent(ServerPlayer player, GeneratedContract contract) {
        this.player = player;
        this.contract = contract;
    }

    public ServerPlayer player() {
        return player;
    }

    public GeneratedContract contract() {
        return contract;
    }

    public ContractRarity rarity() {
        return contract.rarity();
    }

    public int tierPoints() {
        return rarity().tierPoints();
    }
}
