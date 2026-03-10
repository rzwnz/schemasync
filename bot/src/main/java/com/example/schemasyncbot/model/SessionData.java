package com.example.schemasyncbot.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Session data stored in Redis for each Telegram chat.
 * Tracks the current {@link BotState} and all workflow data.
 */
@Data
public class SessionData implements Serializable {

    private static final long serialVersionUID = 2L;

    /** Current state in the bot's finite state machine. */
    private BotState state = BotState.IDLE;

    /** Key-value environment/parameters for the Jenkins build. */
    private Map<String, String> env = new HashMap<>();

    /** Locally-cached diff file (transient — not serialized to Redis). */
    private transient File diffFile;

    /** Currently selected Jenkins pipeline name. */
    private String selectedPipeline;

    /** Ordered list of parameter names the pipeline requires. */
    private List<String> pendingParams;

    /** Build number of the last triggered Jenkins build. */
    private Integer lastBuildNumber;

    /** Parameters used in the last build (for re-triggering / approve). */
    private Map<String, String> lastBuildParams;

    /** Parsed diff data from the SchemaSync microservice. */
    private Map<String, Object> parsedDiff;

    /** Set of parameter names the user has explicitly confirmed. */
    private Set<String> confirmedParams = new HashSet<>();

    /** Parameter currently being edited (awaiting user text input). */
    private String editingParam;

    // ─── Convenience helpers ────────────────────────────────────────────

    /** Check whether all pending parameters have confirmed values. */
    public boolean areAllParamsConfirmed() {
        if (pendingParams == null || pendingParams.isEmpty()) return true;
        return pendingParams.stream().allMatch(p -> confirmedParams.contains(p));
    }

    /** Check whether all pending parameters have non-empty values. */
    public boolean areAllParamsSet() {
        if (pendingParams == null || pendingParams.isEmpty()) return true;
        return pendingParams.stream()
                .allMatch(p -> env.getOrDefault(p, "").length() > 0);
    }

    /** Return the list of parameters that are missing values. */
    @JsonIgnore
    public List<String> getMissingParams() {
        if (pendingParams == null) return List.of();
        return pendingParams.stream()
                .filter(p -> env.getOrDefault(p, "").isEmpty())
                .toList();
    }

    /** Reset session to IDLE state, clearing all workflow data. */
    public void reset() {
        this.state = BotState.IDLE;
        this.selectedPipeline = null;
        this.pendingParams = null;
        this.lastBuildNumber = null;
        this.lastBuildParams = null;
        this.parsedDiff = null;
        this.confirmedParams = new HashSet<>();
        this.editingParam = null;
        this.env = new HashMap<>();
        this.diffFile = null;
    }
}