package dev.flomik.farmerscontracts.villager;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;

public class VisitContractBoardBehavior extends Behavior<Villager> {

    private static final int CLOSE_ENOUGH_DIST = 2;
    private static final int RUN_TIME_TICKS = 200;

    private final float speed;

    public VisitContractBoardBehavior(float speed) {
        super(ImmutableMap.of(ContractVillagerMemories.NEAREST_BOARD.get(), MemoryStatus.VALUE_PRESENT), RUN_TIME_TICKS);
        this.speed = speed;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        return villager.getBrain().getMemory(ContractVillagerMemories.NEAREST_BOARD.get())
                .filter(pos -> pos.distSqr(villager.blockPosition()) > (double) (CLOSE_ENOUGH_DIST * CLOSE_ENOUGH_DIST))
                .isPresent();
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        villager.getBrain().getMemory(ContractVillagerMemories.NEAREST_BOARD.get())
                .ifPresent(pos -> BehaviorUtils.setWalkAndLookTargetMemories(villager, pos, speed, CLOSE_ENOUGH_DIST));
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long gameTime) {
        villager.getBrain().eraseMemory(ContractVillagerMemories.NEAREST_BOARD.get());
    }
}
