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
}
