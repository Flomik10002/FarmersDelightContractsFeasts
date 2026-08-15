package dev.flomik.farmerscontracts.api.event;

import dev.flomik.farmerscontracts.contract.ContractRarity;
import dev.flomik.farmerscontracts.contract.GeneratedContract;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

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
