package dev.flomik.farmerscontracts.api;

import dev.flomik.farmerscontracts.contract.ContractRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public final class ContractScoreboard {
    public static final String OBJECTIVE_NAME = "fc_points";

    public static void awardTierPoints(ServerLevel level, ServerPlayer player, ContractRarity rarity) {
        Scoreboard scoreboard = level.getServer().getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = scoreboard.addObjective(
                    OBJECTIVE_NAME,
                    ObjectiveCriteria.DUMMY,
                    Component.translatable("scoreboard.farmerscontracts.contract_points"),
                    ObjectiveCriteria.RenderType.INTEGER);
        }
        Score score = scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective);
        score.add(rarity.tierPoints());
    }

    private ContractScoreboard() {
    }
}
