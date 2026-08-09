package dev.flomik.farmerscontracts.api;

import dev.flomik.farmerscontracts.contract.ContractRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * Mod-integration hook: tracks contract-completion points on a vanilla scoreboard objective
 * so external systems (command block chains, other mods' scoreboard-based leveling) can read
 * them without needing a dedicated reward item. Common/Uncommon/Rare/Special award
 * {@link ContractRarity#tierPoints()} (1/2/3/4) per completion.
 */
public final class ContractScoreboard {

    // Short on purpose - safely under any vanilla objective-name length limit.
    public static final String OBJECTIVE_NAME = "fc_points";

    public static void awardTierPoints(ServerLevel level, ServerPlayer player, ContractRarity rarity) {
        Scoreboard scoreboard = level.getServer().getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = scoreboard.addObjective(
                    OBJECTIVE_NAME,
                    ObjectiveCriteria.DUMMY,
                    Component.translatable("scoreboard.farmerscontracts.contract_points"),
                    ObjectiveCriteria.RenderType.INTEGER,
                    false,
                    null);
        }
        scoreboard.getOrCreatePlayerScore(player, objective).add(rarity.tierPoints());
    }

    private ContractScoreboard() {
    }
}
