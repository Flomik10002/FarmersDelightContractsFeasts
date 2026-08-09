package dev.flomik.farmerscontracts;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public enum DeliveryMode {
        TICKET_ONLY,
        BOX_ONLY
    }

    private static final ModConfigSpec.EnumValue<DeliveryMode> DELIVERY_MODE = BUILDER
            .comment(
                    "How contracts may be turned in.",
                    "TICKET_ONLY: turn in a Contract Ticket directly at the Contract Board using items from the player's inventory. The Contract Box recipe/item is disabled entirely.",
                    "BOX_ONLY: fill a Contract Box, seal it with the ticket, then deliver the sealed box to the Contract Board. Turning in a ticket straight from inventory is disabled.")
            .defineEnum("deliveryMode", DeliveryMode.BOX_ONLY);

    // Board refresh/breaking behavior, ported 1:1 from Bountiful's board.updateFrequencySecs /
    // board.canBreak (reference/Bountiful, BountifulConfigData.kt) - see
    // docs/board-lifecycle-audit.md for the full comparison.
    private static final ModConfigSpec.IntValue BOARD_UPDATE_FREQUENCY_SECONDS = BUILDER
            .comment("How often (in real seconds) the Contract Board attempts to refill/rotate its offers. Matches Bountiful's default (45).")
            .defineInRange("boardUpdateFrequencySeconds", 45, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.BooleanValue BOARD_CAN_BREAK = BUILDER
            .comment("Whether players are allowed to break the Contract Board at all.")
            .define("boardCanBreak", true);

    private static final ModConfigSpec.BooleanValue BOARD_GLOBAL_STATE = BUILDER
            .comment(
                    "If true, every Contract Board on the server shares one pool of offers/timers (GlobalBoardData) instead of each board keeping its own independent state.",
                    "Matches Bountiful's board.globalBoardState default (false).")
            .define("boardGlobalState", false);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static DeliveryMode deliveryMode() {
        return DELIVERY_MODE.get();
    }

    public static int boardUpdateFrequencySeconds() {
        return BOARD_UPDATE_FREQUENCY_SECONDS.get();
    }

    public static boolean boardCanBreak() {
        return BOARD_CAN_BREAK.get();
    }

    public static boolean boardGlobalState() {
        return BOARD_GLOBAL_STATE.get();
    }

    // Test-only hook (SelfTest) to exercise both delivery-mode branches without a real config
    // file/restart - see ModConfigSpec.ConfigValue#set, which mutates the live cached value.
    public static void setDeliveryModeForTest(DeliveryMode mode) {
        DELIVERY_MODE.set(mode);
    }

    // Test-only hook (SelfTest), same rationale as setDeliveryModeForTest.
    public static void setBoardGlobalStateForTest(boolean value) {
        BOARD_GLOBAL_STATE.set(value);
    }

    private Config() {
    }
}
