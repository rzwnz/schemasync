package com.example.schemasyncbot.model;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class SessionDataTest {

    @Test
    void defaultState_isIdle() {
        SessionData session = new SessionData();
        assertThat(session.getState()).isEqualTo(BotState.IDLE);
    }

    @Test
    void areAllParamsConfirmed_emptyParams_returnsTrue() {
        SessionData session = new SessionData();
        session.setPendingParams(List.of());
        assertThat(session.areAllParamsConfirmed()).isTrue();
    }

    @Test
    void areAllParamsConfirmed_allConfirmed_returnsTrue() {
        SessionData session = new SessionData();
        session.setPendingParams(List.of("A", "B"));
        session.setConfirmedParams(new HashSet<>(Set.of("A", "B")));
        assertThat(session.areAllParamsConfirmed()).isTrue();
    }

    @Test
    void areAllParamsConfirmed_notAllConfirmed_returnsFalse() {
        SessionData session = new SessionData();
        session.setPendingParams(List.of("A", "B"));
        session.setConfirmedParams(new HashSet<>(Set.of("A")));
        assertThat(session.areAllParamsConfirmed()).isFalse();
    }

    @Test
    void areAllParamsSet_allHaveValues_returnsTrue() {
        SessionData session = new SessionData();
        session.setPendingParams(List.of("A"));
        session.getEnv().put("A", "valA");
        assertThat(session.areAllParamsSet()).isTrue();
    }

    @Test
    void areAllParamsSet_missingValue_returnsFalse() {
        SessionData session = new SessionData();
        session.setPendingParams(List.of("A", "B"));
        session.getEnv().put("A", "valA");
        assertThat(session.areAllParamsSet()).isFalse();
    }

    @Test
    void getMissingParams_returnsMissing() {
        SessionData session = new SessionData();
        session.setPendingParams(List.of("A", "B", "C"));
        session.getEnv().put("A", "valA");
        session.getEnv().put("B", "");
        List<String> missing = session.getMissingParams();
        assertThat(missing).containsExactly("B", "C");
    }

    @Test
    void getMissingParams_nullPendingParams_returnsEmpty() {
        SessionData session = new SessionData();
        assertThat(session.getMissingParams()).isEmpty();
    }

    @Test
    void reset_clearsAllState() {
        SessionData session = new SessionData();
        session.setState(BotState.APPLYING);
        session.setSelectedPipeline("pipe");
        session.setPendingParams(List.of("A"));
        session.getEnv().put("A", "val");
        session.setLastBuildNumber(42);
        session.setLastBuildParams(Map.of("X", "Y"));
        session.setParsedDiff(Map.of("k", "v"));
        session.setConfirmedParams(new HashSet<>(Set.of("A")));
        session.setEditingParam("A");

        session.reset();

        assertThat(session.getState()).isEqualTo(BotState.IDLE);
        assertThat(session.getSelectedPipeline()).isNull();
        assertThat(session.getPendingParams()).isNull();
        assertThat(session.getEnv()).isEmpty();
        assertThat(session.getLastBuildNumber()).isNull();
        assertThat(session.getLastBuildParams()).isNull();
        assertThat(session.getParsedDiff()).isNull();
        assertThat(session.getConfirmedParams()).isEmpty();
        assertThat(session.getEditingParam()).isNull();
    }
}
