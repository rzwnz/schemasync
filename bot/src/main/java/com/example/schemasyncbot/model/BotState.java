package com.example.schemasyncbot.model;

/**
 * Finite state machine for the SchemaSync Telegram bot.
 * Each user session is always in exactly one state.
 *
 * <pre>
 * IDLE ──► SELECTING_PIPELINE ──► CONFIGURING_PARAMS ──► AWAITING_DIFF
 *                                                              │
 *                                                              ▼
 *                                          APPLYING ◄── REVIEWING_DIFF
 *                                              │
 *                                              ▼
 *                                            IDLE
 * </pre>
 *
 * Any state can transition to IDLE via /cancel.
 */
public enum BotState {

    /** Default state — no active workflow. */
    IDLE("Idle", "No active workflow. Use /pipelines to get started."),

    /** User is browsing the list of Jenkins pipelines. */
    SELECTING_PIPELINE("Selecting Pipeline", "Browsing available Jenkins pipelines."),

    /** Pipeline selected — user is editing / confirming parameters. */
    CONFIGURING_PARAMS("Configuring Parameters", "Setting up pipeline parameters."),

    /** Diff job triggered — polling Jenkins for the artifact. */
    AWAITING_DIFF("Awaiting Diff", "Waiting for Jenkins to produce the diff artifact."),

    /** Diff received — user is reviewing the changes. */
    REVIEWING_DIFF("Reviewing Diff", "Diff is ready for review. Approve or cancel."),

    /** Apply job triggered — waiting for completion. */
    APPLYING("Applying Changes", "Schema changes are being applied to the database.");

    private final String displayName;
    private final String description;

    BotState(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /** Emoji indicator for the state. */
    public String getEmoji() {
        return switch (this) {
            case IDLE -> "🏠";
            case SELECTING_PIPELINE -> "🔍";
            case CONFIGURING_PARAMS -> "⚙️";
            case AWAITING_DIFF -> "⏳";
            case REVIEWING_DIFF -> "📋";
            case APPLYING -> "🚀";
        };
    }
}
