package com.example.schemasyncbot.utils;

import com.example.schemasyncbot.model.BotState;
import com.example.schemasyncbot.model.SessionData;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class MessageTemplatesTest {

    @Test
    void welcome_withUsername_includesGreeting() {
        String msg = MessageTemplates.welcome("Alice");
        assertThat(msg).contains("Alice");
        assertThat(msg).contains("SchemaSync Bot");
        assertThat(msg).contains("Quick Start");
    }

    @Test
    void welcome_withNull_usesGenericGreeting() {
        String msg = MessageTemplates.welcome(null);
        assertThat(msg).contains("Hello!");
        assertThat(msg).doesNotContain("null");
    }

    @Test
    void helpOverview_containsAllCommands() {
        String msg = MessageTemplates.helpOverview();
        assertThat(msg).contains("/start", "/pipelines", "/diff", "/approve", "/logs", "/cancel", "/help");
    }

    @Test
    void helpWorkflow_containsAllSteps() {
        String msg = MessageTemplates.helpWorkflow();
        assertThat(msg).contains("Step 1", "Step 2", "Step 3", "Step 4");
    }

    @Test
    void helpParameters_containsCommonParams() {
        String msg = MessageTemplates.helpParameters();
        assertThat(msg).contains("SOURCE_DB_URL", "TARGET_DB_URL", "SCHEMA_NAME");
    }

    @Test
    void pipelineListHeader_containsTitle() {
        assertThat(MessageTemplates.pipelineListHeader()).contains("Available Pipelines");
    }

    @Test
    void pipelineSelected_containsPipelineName() {
        assertThat(MessageTemplates.pipelineSelected("my-pipeline")).contains("my-pipeline");
    }

    @Test
    void noPipelinesFound_containsWarning() {
        assertThat(MessageTemplates.noPipelinesFound()).contains("No Pipelines Found");
    }

    @Test
    void parameterList_showsProgressAndParams() {
        List<String> params = List.of("A", "B", "C");
        Map<String, String> env = Map.of("A", "val1", "B", "");
        Set<String> confirmed = Set.of("A");
        String msg = MessageTemplates.parameterList("pipe", params, env, confirmed);
        assertThat(msg).contains("Pipeline Parameters");
        assertThat(msg).contains("1/3 confirmed");
        assertThat(msg).contains("val1");
    }

    @Test
    void parameterDetail_withValue_showsValue() {
        String msg = MessageTemplates.parameterDetail("KEY", "value");
        assertThat(msg).contains("KEY");
        assertThat(msg).contains("value");
    }

    @Test
    void parameterDetail_withNull_showsNotSet() {
        String msg = MessageTemplates.parameterDetail("KEY", null);
        assertThat(msg).contains("not set");
    }

    @Test
    void parametersIncomplete_listsMissingParams() {
        String msg = MessageTemplates.parametersIncomplete(List.of("A", "B"));
        assertThat(msg).contains("Missing Parameters");
        assertThat(msg).contains("A");
        assertThat(msg).contains("B");
    }

    @Test
    void diffTriggered_showsPipelineAndSchema() {
        String msg = MessageTemplates.diffTriggered("pipe", "myschema");
        assertThat(msg).contains("Diff Job Triggered");
        assertThat(msg).contains("pipe");
        assertThat(msg).contains("myschema");
    }

    @Test
    void diffTriggered_nullSchema_showsNA() {
        String msg = MessageTemplates.diffTriggered("pipe", null);
        assertThat(msg).contains("N/A");
    }

    @Test
    void applyTriggered_showsPipeline() {
        assertThat(MessageTemplates.applyTriggered("pipe", "schema"))
                .contains("Apply Job Triggered");
    }

    @Test
    void applySuccess_containsMessage() {
        assertThat(MessageTemplates.applySuccess()).contains("Changes Applied Successfully");
    }

    @Test
    void logsHeader_containsPipeline() {
        assertThat(MessageTemplates.logsHeader("pipe")).contains("Jenkins Logs").contains("pipe");
    }

    @Test
    void status_showsStateAndPipeline() {
        SessionData session = new SessionData();
        session.setSelectedPipeline("pipe");
        session.setState(BotState.CONFIGURING_PARAMS);
        String msg = MessageTemplates.status(session);
        assertThat(msg).contains("Session Status");
        assertThat(msg).contains("pipe");
        assertThat(msg).contains(BotState.CONFIGURING_PARAMS.getDisplayName());
    }

    @Test
    void cancelled_containsMessage() {
        assertThat(MessageTemplates.cancelled()).contains("Operation Cancelled");
    }

    @Test
    void noPipelineSelected_containsWarning() {
        assertThat(MessageTemplates.noPipelineSelected()).contains("No Pipeline Selected");
    }

    @Test
    void error_wrapsMessage() {
        assertThat(MessageTemplates.error("boom")).contains("Error").contains("boom");
    }

    @Test
    void privateOnly_containsMessage() {
        assertThat(MessageTemplates.privateOnly()).contains("private chats");
    }

    @Test
    void paramDeleted_containsKey() {
        assertThat(MessageTemplates.paramDeleted("foo")).contains("foo").contains("removed");
    }

    @Test
    void paramNotFound_containsKey() {
        assertThat(MessageTemplates.paramNotFound("bar")).contains("bar").contains("not found");
    }

    @Test
    void escapeHtml_escapesSpecialChars() {
        assertThat(MessageTemplates.escapeHtml("<b>&</b>")).isEqualTo("&lt;b&gt;&amp;&lt;/b&gt;");
    }

    @Test
    void escapeHtml_nullReturnsEmpty() {
        assertThat(MessageTemplates.escapeHtml(null)).isEmpty();
    }

    // ─── Additional coverage tests ─────────────────────────────────────

    @Test
    void noParametersFound_containsWarning() {
        assertThat(MessageTemplates.noParametersFound()).contains("No Parameters Found");
    }

    @Test
    void parameterValueSet_containsKeyAndValue() {
        String msg = MessageTemplates.parameterValueSet("DB_HOST", "localhost");
        assertThat(msg).contains("DB_HOST").contains("localhost");
    }

    @Test
    void parameterValuePrompt_containsParamName() {
        assertThat(MessageTemplates.parameterValuePrompt("DB_PORT")).contains("DB_PORT");
    }

    @Test
    void allParametersConfirmed_containsMessage() {
        assertThat(MessageTemplates.allParametersConfirmed()).contains("All Parameters Confirmed");
    }

    @Test
    void diffReady_containsReviewMessage() {
        assertThat(MessageTemplates.diffReady()).contains("Diff Ready for Review");
    }

    @Test
    void diffTimeout_containsTimeoutMessage() {
        assertThat(MessageTemplates.diffTimeout()).contains("Diff Timed Out");
    }

    @Test
    void sessionExpired_containsExpiredMessage() {
        assertThat(MessageTemplates.sessionExpired()).contains("Session Expired");
    }

    @Test
    void safeErrorMessage_withMessage_returnsMessage() {
        assertThat(MessageTemplates.safeErrorMessage(new RuntimeException("test error")))
                .isEqualTo("test error");
    }

    @Test
    void safeErrorMessage_withNull_returnsUnknown() {
        assertThat(MessageTemplates.safeErrorMessage(null)).isEqualTo("Unknown error");
    }

    @Test
    void safeErrorMessage_blankMessage_returnsCauseMessage() {
        Exception cause = new Exception("root cause");
        Exception ex = new RuntimeException(" ", cause);
        assertThat(MessageTemplates.safeErrorMessage(ex)).isEqualTo("root cause");
    }

    @Test
    void safeErrorMessage_noMessageNoCause_returnsClassName() {
        Exception ex = new NullPointerException();
        assertThat(MessageTemplates.safeErrorMessage(ex)).isEqualTo("NullPointerException");
    }

    @Test
    void wrongState_showsCurrentState() {
        String msg = MessageTemplates.wrongState(BotState.IDLE, "approve changes");
        assertThat(msg).contains("Invalid Action");
        assertThat(msg).contains("approve changes");
        assertThat(msg).contains(BotState.IDLE.getDisplayName());
    }

    @Test
    void tableColumns_withColumns_listsAll() {
        List<Map<String, Object>> columns = List.of(
                Map.of("tableName", "users", "columnName", "id", "type", "BIGINT"),
                Map.of("tableName", "users", "columnName", "name", "type", "VARCHAR")
        );
        String msg = MessageTemplates.tableColumns("users", columns);
        assertThat(msg).contains("users").contains("id").contains("BIGINT").contains("name");
    }

    @Test
    void tableColumns_emptyColumns_showsNotAvailable() {
        String msg = MessageTemplates.tableColumns("empty_table", List.of());
        assertThat(msg).contains("No column data available");
    }

    @Test
    void tableColumns_nullColumns_showsNotAvailable() {
        String msg = MessageTemplates.tableColumns("null_table", null);
        assertThat(msg).contains("No column data available");
    }

    @Test
    void status_withLastBuildNumber_showsBuildNumber() {
        SessionData session = new SessionData();
        session.setLastBuildNumber(42);
        String msg = MessageTemplates.status(session);
        assertThat(msg).contains("42");
    }

    @Test
    void status_withEnvParams_showsParams() {
        SessionData session = new SessionData();
        Map<String, String> env = new HashMap<>();
        env.put("KEY1", "val1");
        session.setEnv(env);
        session.setConfirmedParams(Set.of("KEY1"));
        String msg = MessageTemplates.status(session);
        assertThat(msg).contains("KEY1").contains("val1");
    }

    @Test
    void welcome_emptyUsername_usesGenericGreeting() {
        String msg = MessageTemplates.welcome("");
        assertThat(msg).contains("Hello!");
    }

    @Test
    void parameterDetail_emptyValue_showsNotSet() {
        String msg = MessageTemplates.parameterDetail("KEY", "");
        assertThat(msg).contains("not set");
    }

    @Test
    void parameterList_allConfirmed_showsFullProgress() {
        List<String> params = List.of("A");
        Map<String, String> env = Map.of("A", "val");
        Set<String> confirmed = Set.of("A");
        String msg = MessageTemplates.parameterList("pipe", params, env, confirmed);
        assertThat(msg).contains("1/1 confirmed");
    }
}
