package dev.flomik.farmerscontracts.contract;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ContractGenerator {

    private static final long TICKS_PER_SECOND = 20L;
    private static final long BASE_TIME_SECONDS = 2L * 3600L;
    private static final double SECONDS_PER_WORTH = 20.0;

    private static final double REWARD_PAYOUT_RATIO = 1.0 / 4.5;

    private static final double XP_PER_WORTH = 1.0;
    private static final int MIN_XP = 3;

    private ContractGenerator() {
    }

    public record SimulationResult(List<GeneratedLine> objectives, List<GeneratedLine> rewards, int xp, double targetWorth) {
    }

    public static Optional<SimulationResult> simulate(Customer customer, ContractRarity rarity, RandomSource random) {
        List<ContractPoolEntry> objectiveEntries = entriesOf(customer.objectivePools());
        if (objectiveEntries.isEmpty()) {
            return Optional.empty();
        }

        List<ContractPoolEntry> rewardEntries = entriesOf(customer.rewardPools());
        if (rewardEntries.isEmpty()) {
            return Optional.empty();
        }

        List<ContractPoolEntry> tierEntries = objectiveEntries.stream().filter(e -> e.rarity() == rarity).toList();
        if (tierEntries.isEmpty()) {
            tierEntries = objectiveEntries;
        }

        Set<Item> usedItems = new HashSet<>();

        int objectiveCount = customer.objectiveCount().pick(random);
        List<GeneratedLine> objectives = new ArrayList<>();
        List<ContractPoolEntry> chosenEntries = new ArrayList<>();

        List<ContractPoolEntry> remainingEntries = new ArrayList<>(tierEntries);
        for (int i = 0; i < objectiveCount && !remainingEntries.isEmpty(); i++) {
            List<ContractPoolEntry> candidates = withoutConflicts(remainingEntries, chosenEntries);
            if (candidates.isEmpty()) {
                break;
            }
            ContractPoolEntry entry = pickWeightedByEntryWeight(candidates, random);
            if (entry == null) {
                break;
            }
            remainingEntries.remove(entry);

            GeneratedLine line = resolve(entry, random, usedItems);
            if (line == null) {
                continue;
            }
            usedItems.add(line.stack().getItem());
            objectives.add(line);
            chosenEntries.add(entry);
        }

        if (objectives.isEmpty()) {
            return Optional.empty();
        }

        double targetWorth = objectives.stream().mapToDouble(GeneratedLine::worth).sum();

        ContractPoolEntry rewardEntry = pickWeightedByEntryWeight(rewardEntries, random);
        if (rewardEntry == null) {
            return Optional.empty();
        }

        double rewardWorth = targetWorth * rarity.rewardMultiplier() * REWARD_PAYOUT_RATIO;
        List<GeneratedLine> rewards = new ArrayList<>(resolveForWorth(rewardEntry, rewardWorth, random, usedItems));
        if (rewards.isEmpty()) {
            return Optional.empty();
        }
        rewards.addAll(bonusReward(customer, rarity, random, usedItems));

        int xp = Math.max(MIN_XP, (int) Math.round(targetWorth * rarity.rewardMultiplier() * XP_PER_WORTH));

        return Optional.of(new SimulationResult(objectives, rewards, xp, targetWorth));
    }

    public static Optional<GeneratedContract> generate(ResourceLocation customerId, ServerLevel level) {
        Customer customer = ContractContent.customers().get(customerId);
        if (customer == null) {
            return Optional.empty();
        }

        RandomSource random = level.getRandom();
        long completed = ContractProgress.get(level).completedContracts();
        ContractRarity rarity = pickWeightedRarity(completed, random);

        Optional<SimulationResult> simulated = simulate(customer, rarity, random);
        if (simulated.isEmpty()) {
            return Optional.empty();
        }
        SimulationResult sim = simulated.get();

        long durationSeconds = BASE_TIME_SECONDS + (long) (sim.targetWorth() * SECONDS_PER_WORTH);
        long expiresAt = level.getGameTime() + durationSeconds * TICKS_PER_SECOND;
        RewardBundle rewardBundle = new RewardBundle(sim.rewards(), sim.xp());

        return Optional.of(new GeneratedContract(customerId, customer.name(), rarity, sim.objectives(), rewardBundle, expiresAt));
    }

    private static List<GeneratedLine> bonusReward(Customer customer, ContractRarity rarity, RandomSource random, Set<Item> usedItems) {
        if (rarity != ContractRarity.RARE && rarity != ContractRarity.SPECIAL) {
            return List.of();
        }
        List<ContractPoolEntry> bonusEntries = entriesOf(customer.bonusRewardPools()).stream()
                .filter(entry -> entry.rarity() == rarity)
                .toList();
        if (bonusEntries.isEmpty()) {
            return List.of();
        }
        ContractPoolEntry entry = pickWeightedByEntryWeight(bonusEntries, random);
        if (entry == null) {
            return List.of();
        }
        GeneratedLine line = resolve(entry, random, usedItems);
        return line == null ? List.of() : List.of(line);
    }

    private static List<ContractPoolEntry> entriesOf(List<ResourceLocation> poolIds) {
        List<ContractPoolEntry> entries = new ArrayList<>();
        for (ResourceLocation poolId : poolIds) {
            ContractContent.pool(poolId).ifPresent(pool -> entries.addAll(pool.entries()));
        }
        return entries;
    }

    private static ContractRarity pickWeightedRarity(long completed, RandomSource random) {
        ContractRarity[] tiers = ContractRarity.values();
        double totalWeight = 0;
        for (ContractRarity tier : tiers) {
            totalWeight += tier.weightAdjustedFor(completed);
        }

        double roll = random.nextDouble() * totalWeight;
        double acc = 0;
        for (ContractRarity tier : tiers) {
            acc += tier.weightAdjustedFor(completed);
            if (roll <= acc) {
                return tier;
            }
        }
        return tiers[tiers.length - 1];
    }

    private static List<ContractPoolEntry> withoutConflicts(List<ContractPoolEntry> candidates, List<ContractPoolEntry> chosen) {
        if (chosen.isEmpty()) {
            return candidates;
        }
        List<ContractPoolEntry> filtered = new ArrayList<>();
        for (ContractPoolEntry candidate : candidates) {
            boolean conflicts = false;
            for (ContractPoolEntry existing : chosen) {
                if (candidate.conflictsWith(existing)) {
                    conflicts = true;
                    break;
                }
            }
            if (!conflicts) {
                filtered.add(candidate);
            }
        }
        return filtered;
    }

    private static ContractPoolEntry pickWeightedByEntryWeight(List<ContractPoolEntry> entries, RandomSource random) {
        double totalWeight = entries.stream().mapToDouble(ContractPoolEntry::weight).sum();
        if (totalWeight <= 0) {
            return entries.isEmpty() ? null : entries.get(random.nextInt(entries.size()));
        }

        double roll = random.nextDouble() * totalWeight;
        double acc = 0;
        for (ContractPoolEntry entry : entries) {
            acc += entry.weight();
            if (roll <= acc) {
                return entry;
            }
        }
        return entries.get(entries.size() - 1);
    }

    private static GeneratedLine resolve(ContractPoolEntry entry, RandomSource random, Set<Item> usedItems) {
        ItemStack stack = randomStack(entry, random, usedItems);
        if (stack.isEmpty()) {
            return null;
        }
        int amount = entry.amount().pick(random);
        return new GeneratedLine(stack, amount, entry.worthFor(stack.getItem(), amount));
    }

    private static List<GeneratedLine> resolveForWorth(ContractPoolEntry entry, double targetWorth, RandomSource random, Set<Item> usedItems) {
        ItemStack sample = randomStack(entry, random, usedItems);
        if (sample.isEmpty()) {
            return List.of();
        }

        double unitWorth = entry.worthPerUnit(sample.getItem());
        int totalAmount = Math.max(entry.amount().min(), (int) Math.round(targetWorth / unitWorth));

        List<GeneratedLine> lines = new ArrayList<>();
        int remaining = totalAmount;
        int maxStack = sample.getMaxStackSize();
        while (remaining > 0) {
            int chunk = Math.min(remaining, maxStack);
            lines.add(new GeneratedLine(sample.copyWithCount(chunk), chunk, unitWorth * chunk));
            remaining -= chunk;
        }
        return lines;
    }

    private static ItemStack randomStack(ContractPoolEntry entry, RandomSource random, Set<Item> usedItems) {
        List<ItemStack> matches = new ArrayList<>();
        for (ItemStack stack : entry.item().getItems()) {
            if (FarmersDelightCompat.isAvailable(stack.getItem())) {
                matches.add(stack);
            }
        }
        if (matches.isEmpty()) {
            return ItemStack.EMPTY;
        }

        List<ItemStack> unused = new ArrayList<>();
        for (ItemStack match : matches) {
            if (!usedItems.contains(match.getItem())) {
                unused.add(match);
            }
        }

        if (!unused.isEmpty()) {
            return unused.get(random.nextInt(unused.size()));
        }
        return matches.get(random.nextInt(matches.size()));
    }
}
