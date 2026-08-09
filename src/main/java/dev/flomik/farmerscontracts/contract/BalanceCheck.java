package dev.flomik.farmerscontracts.contract;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public final class BalanceCheck {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int TRIALS = 10_000;

    private static final double[] PERCENTILES = {10, 25, 50, 75, 90, 95, 99};
    private static final String[] PERCENTILE_LABELS = {"p10", "p25", "median", "p75", "p90", "p95", "p99"};

    private static final int TOP_OBJECTIVES = 3;

    private BalanceCheck() {
    }

    public static boolean isRequested() {
        return Boolean.getBoolean("farmerscontracts.balance");
    }

    public static void run() {
        RandomSource random = RandomSource.create();
        LOGGER.info("=== Farmer's Contracts economy balance check ({} trials per customer/rarity) ===", TRIALS);

        for (Map.Entry<ResourceLocation, Customer> entry : ContractContent.customers().entrySet()) {
            ResourceLocation customerId = entry.getKey();
            Customer customer = entry.getValue();

            for (ContractRarity rarity : ContractRarity.values()) {
                double[] worth = new double[TRIALS];
                double[] emeralds = new double[TRIALS];
                double[] rewardValue = new double[TRIALS];
                double[] profit = new double[TRIALS];
                GeneratedLine[] topObjectives = new GeneratedLine[TOP_OBJECTIVES];
                int successes = 0;
                int xpSum = 0;
                int bonusCount = 0;

                for (int i = 0; i < TRIALS; i++) {
                    Optional<ContractGenerator.SimulationResult> result = ContractGenerator.simulate(customer, rarity, random);
                    if (result.isEmpty()) {
                        continue;
                    }
                    ContractGenerator.SimulationResult sim = result.get();

                    double emeraldWorth = 0;
                    double rewardWorth = 0;
                    for (GeneratedLine line : sim.rewards()) {
                        rewardWorth += line.worth();
                        if (line.stack().is(Items.EMERALD)) {
                            emeraldWorth += line.amount();
                        } else {
                            bonusCount++;
                        }
                    }
                    for (GeneratedLine line : sim.objectives()) {
                        recordTop(topObjectives, line);
                    }

                    worth[successes] = sim.targetWorth();
                    emeralds[successes] = emeraldWorth;
                    rewardValue[successes] = rewardWorth;
                    profit[successes] = rewardWorth - sim.targetWorth();
                    xpSum += sim.xp();
                    successes++;
                }

                if (successes == 0) {
                    LOGGER.info("  {} [{}]: no data for this tier", customerId, rarity.getSerializedName());
                    continue;
                }

                worth = Arrays.copyOf(worth, successes);
                emeralds = Arrays.copyOf(emeralds, successes);
                rewardValue = Arrays.copyOf(rewardValue, successes);
                profit = Arrays.copyOf(profit, successes);
                Arrays.sort(worth);
                Arrays.sort(emeralds);
                Arrays.sort(rewardValue);
                Arrays.sort(profit);

                LOGGER.info("  {} [{}]: {}/{} trials succeeded, avg xp={}, bonus items in {} trials",
                        customerId, rarity.getSerializedName(), successes, TRIALS, xpSum / successes, bonusCount);
                LOGGER.info("    worth        {}", percentileSummary(worth));
                LOGGER.info("    emerald pay  {}", percentileSummary(emeralds));
                LOGGER.info("    reward value {}", percentileSummary(rewardValue));
                LOGGER.info("    profit       {}", percentileSummary(profit));
                LOGGER.info("    top {} rolled objectives: {}", TOP_OBJECTIVES, formatTop(topObjectives));
            }
        }
        LOGGER.info("=== balance check complete ===");
    }

    private static double percentile(double[] sorted, double p) {
        if (sorted.length == 1) {
            return sorted[0];
        }
        double rank = (p / 100.0) * (sorted.length - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        if (lo == hi) {
            return sorted[lo];
        }
        double frac = rank - lo;
        return sorted[lo] + (sorted[hi] - sorted[lo]) * frac;
    }

    private static String percentileSummary(double[] sorted) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PERCENTILES.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(PERCENTILE_LABELS[i]).append('=').append(String.format("%.1f", percentile(sorted, PERCENTILES[i])));
        }
        return sb.toString();
    }

    private static void recordTop(GeneratedLine[] top, GeneratedLine candidate) {
        for (int i = 0; i < top.length; i++) {
            if (top[i] == null || candidate.worth() > top[i].worth()) {
                for (int j = top.length - 1; j > i; j--) {
                    top[j] = top[j - 1];
                }
                top[i] = candidate;
                return;
            }
        }
    }

    private static String formatTop(GeneratedLine[] top) {
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (GeneratedLine line : top) {
            if (line == null) {
                continue;
            }
            if (any) {
                sb.append(", ");
            }
            sb.append(line.amount()).append('x').append(line.stack().getHoverName().getString())
                    .append(" (worth=").append(String.format("%.1f", line.worth())).append(')');
            any = true;
        }
        return any ? sb.toString() : "n/a";
    }
}
