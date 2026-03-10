package com.example.schemasyncbot.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class BotStateTest {

    @Test
    void allStates_haveDisplayName() {
        for (BotState state : BotState.values()) {
            assertThat(state.getDisplayName()).isNotBlank();
        }
    }

    @Test
    void allStates_haveDescription() {
        for (BotState state : BotState.values()) {
            assertThat(state.getDescription()).isNotBlank();
        }
    }

    @Test
    void allStates_haveEmoji() {
        for (BotState state : BotState.values()) {
            assertThat(state.getEmoji()).isNotBlank();
        }
    }

    @Test
    void idle_hasCorrectDisplayName() {
        assertThat(BotState.IDLE.getDisplayName()).isEqualTo("Idle");
    }

    @Test
    void selectingPipeline_hasCorrectDisplayName() {
        assertThat(BotState.SELECTING_PIPELINE.getDisplayName()).isEqualTo("Selecting Pipeline");
    }

    @Test
    void configuringParams_hasCorrectDisplayName() {
        assertThat(BotState.CONFIGURING_PARAMS.getDisplayName()).isEqualTo("Configuring Parameters");
    }

    @Test
    void awaitingDiff_hasCorrectDisplayName() {
        assertThat(BotState.AWAITING_DIFF.getDisplayName()).isEqualTo("Awaiting Diff");
    }

    @Test
    void reviewingDiff_hasCorrectDisplayName() {
        assertThat(BotState.REVIEWING_DIFF.getDisplayName()).isEqualTo("Reviewing Diff");
    }

    @Test
    void applying_hasCorrectDisplayName() {
        assertThat(BotState.APPLYING.getDisplayName()).isEqualTo("Applying Changes");
    }

    @Test
    void valueOf_roundTrips() {
        for (BotState state : BotState.values()) {
            assertThat(BotState.valueOf(state.name())).isEqualTo(state);
        }
    }
}
