package com.example.schemasyncbot.utils;

import com.example.schemasyncbot.model.BotState;
import com.example.schemasyncbot.model.SessionData;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Centralized message templates for the SchemaSync Telegram bot.
 * All messages use HTML parse mode.
 */
public final class MessageTemplates {

    private MessageTemplates() {}

    // ─── BRANDING ───────────────────────────────────────────────────────
    private static final String LOGO = "🔄";
    private static final String DIVIDER = "───────────────────";

    // ─── WELCOME / START ────────────────────────────────────────────────

    public static String welcome(String username) {
        String greeting = (username != null && !username.isEmpty())
                ? "Hello, <b>" + escapeHtml(username) + "</b>!"
                : "Hello!";
        return LOGO + " <b>SchemaSync Bot</b>\n"
                + DIVIDER + "\n\n"
                + greeting + "\n\n"
                + "I help you manage database schema changes through Jenkins pipelines.\n\n"
                + "<b>Quick Start:</b>\n"
                + "  1️⃣  Select a pipeline with /pipelines\n"
                + "  2️⃣  Configure parameters\n"
                + "  3️⃣  Generate a diff with /diff\n"
                + "  4️⃣  Review &amp; approve with /approve\n\n"
                + "💡 Type /help for detailed usage information.";
    }

    // ─── HELP ───────────────────────────────────────────────────────────

    public static String helpOverview() {
        return "📖 <b>SchemaSync Bot — Help</b>\n"
                + DIVIDER + "\n\n"
                + "<b>Commands:</b>\n\n"
                + "🏠  /start — Start a new session\n"
                + "🔍  /pipelines — Browse Jenkins pipelines\n"
                + "⚙️  /diff — Generate schema diff\n"
                + "✅  /approve — Apply reviewed changes\n"
                + "📋  /logs — View Jenkins build logs\n"
                + "📊  /status — Check current session state\n"
                + "🗑  /delete &lt;key&gt; — Remove a parameter\n"
                + "❌  /cancel — Cancel current operation\n"
                + "📖  /help — Show this help\n\n"
                + "<b>Parameter Input:</b>\n"
                + "Send <code>KEY=value</code> to set parameters.\n"
                + "Example: <code>SOURCE_DB_URL=jdbc:mysql://host:3306/db</code>\n\n"
                + "Select a topic below for more details:";
    }

    public static String helpWorkflow() {
        return "📖 <b>Workflow Guide</b>\n"
                + DIVIDER + "\n\n"
                + "<b>Step 1 — Select Pipeline</b>\n"
                + "Use /pipelines to browse available Jenkins jobs.\n"
                + "Tap a pipeline to select it.\n\n"
                + "<b>Step 2 — Configure Parameters</b>\n"
                + "Each pipeline has required parameters.\n"
                + "Tap each parameter to edit its value, then confirm it.\n"
                + "All parameters must be confirmed before proceeding.\n\n"
                + "<b>Step 3 — Generate Diff</b>\n"
                + "Once all parameters are set, use /diff.\n"
                + "The bot triggers a Jenkins job and waits for the diff artifact.\n\n"
                + "<b>Step 4 — Review &amp; Approve</b>\n"
                + "Review the diff file the bot sends you.\n"
                + "Use /approve or the ✅ button to apply the changes.\n"
                + "Use /cancel to abort.";
    }

    public static String helpParameters() {
        return "📖 <b>Parameter Reference</b>\n"
                + DIVIDER + "\n\n"
                + "Parameters are pipeline-specific. Common ones include:\n\n"
                + "• <code>SOURCE_DB_URL</code> — Source database JDBC URL\n"
                + "• <code>TARGET_DB_URL</code> — Target database JDBC URL\n"
                + "• <code>SCHEMA_NAME</code> — Schema to compare\n"
                + "• <code>DB_USERNAME</code> — Database username\n"
                + "• <code>DB_PASSWORD</code> — Database password\n\n"
                + "<b>Setting values:</b>\n"
                + "• Tap a parameter button → Edit Value → Send the value\n"
                + "• Or send <code>KEY=value</code> directly\n\n"
                + "<b>Editing:</b>\n"
                + "• Tap the parameter again to change it\n"
                + "• Use /delete &lt;KEY&gt; to remove a parameter";
    }

    // ─── PIPELINE SELECTION ────────────────────────────────────────────

    public static String pipelineListHeader() {
        return "🔍 <b>Available Pipelines</b>\n"
                + DIVIDER + "\n\n"
                + "Select a Jenkins pipeline to configure:";
    }

    public static String pipelineSelected(String pipelineName) {
        return "✅ <b>Pipeline Selected</b>\n"
                + DIVIDER + "\n\n"
                + "📌 <code>" + escapeHtml(pipelineName) + "</code>\n\n"
                + "Configure the required parameters below.\n"
                + "Tap each parameter to set its value.";
    }

    public static String noPipelinesFound() {
        return "⚠️ <b>No Pipelines Found</b>\n\n"
                + "No Jenkins jobs were found.\n"
                + "Please check the Jenkins configuration.";
    }

    public static String noParametersFound() {
        return "⚠️ <b>No Parameters Found</b>\n\n"
                + "This pipeline has no configurable parameters.\n"
                + "Please check the Jenkins job configuration.";
    }

    // ─── PARAMETER CONFIGURATION ───────────────────────────────────────

    public static String parameterList(String pipelineName, List<String> params,
                                       Map<String, String> env, Set<String> confirmed) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚙️ <b>Pipeline Parameters</b>\n");
        sb.append(DIVIDER).append("\n");
        sb.append("📌 <code>").append(escapeHtml(pipelineName)).append("</code>\n\n");

        int total = params.size();
        int done = 0;
        for (String p : params) {
            if (confirmed.contains(p)) done++;
        }
        sb.append("Progress: ").append(done).append("/").append(total).append(" confirmed\n");
        sb.append(progressBar(done, total)).append("\n\n");

        for (String param : params) {
            String value = env.getOrDefault(param, "");
            boolean isConfirmed = confirmed.contains(param);
            String icon = isConfirmed ? "✅" : (value.isEmpty() ? "⬜" : "✏️");
            sb.append(icon).append(" <b>").append(escapeHtml(param)).append("</b>");
            if (!value.isEmpty()) {
                sb.append(": <code>").append(escapeHtml(truncate(value, 40))).append("</code>");
            }
            sb.append("\n");
        }

        sb.append("\nTap a parameter to edit or confirm it.");
        return sb.toString();
    }

    public static String parameterDetail(String paramName, String value) {
        String displayValue = (value == null || value.isEmpty()) ? "<i>not set</i>" : "<code>" + escapeHtml(value) + "</code>";
        return "⚙️ <b>Parameter:</b> <code>" + escapeHtml(paramName) + "</code>\n"
                + DIVIDER + "\n\n"
                + "Current value: " + displayValue + "\n\n"
                + "Choose an action:";
    }

    public static String parameterValueSet(String paramName, String value) {
        return "✅ Value for <b>" + escapeHtml(paramName) + "</b> set to:\n"
                + "<code>" + escapeHtml(value) + "</code>\n\n"
                + "Confirm or edit using the buttons below.";
    }

    public static String parameterValuePrompt(String paramName) {
        return "✏️ Send the new value for <b>" + escapeHtml(paramName) + "</b>:\n\n"
                + "<i>Just type the value — no key= prefix needed.</i>";
    }

    public static String allParametersConfirmed() {
        return "✅ <b>All Parameters Confirmed</b>\n\n"
                + "Ready to generate the schema diff.\n"
                + "Use /diff to proceed.";
    }

    public static String parametersIncomplete(List<String> missing) {
        StringBuilder sb = new StringBuilder("⚠️ <b>Missing Parameters</b>\n\n");
        sb.append("The following parameters still need values:\n\n");
        for (String m : missing) {
            sb.append("  • <code>").append(escapeHtml(m)).append("</code>\n");
        }
        sb.append("\nPlease set them using <code>KEY=value</code> format.");
        return sb.toString();
    }

    // ─── DIFF OPERATIONS ───────────────────────────────────────────────

    public static String diffTriggered(String pipelineName, String schemaName) {
        return "🔧 <b>Diff Job Triggered</b>\n"
                + DIVIDER + "\n\n"
                + "📌 Pipeline: <code>" + escapeHtml(pipelineName) + "</code>\n"
                + "📊 Schema: <code>" + escapeHtml(schemaName != null ? schemaName : "N/A") + "</code>\n\n"
                + "⏳ Waiting for Jenkins to complete...\n"
                + "You'll be notified when the diff is ready.";
    }

    public static String diffReady() {
        return "📋 <b>Diff Ready for Review</b>\n"
                + DIVIDER + "\n\n"
                + "The schema diff has been generated.\n"
                + "Review the file below and choose an action:\n\n"
                + "  ✅ <b>Approve</b> — Apply changes to the database\n"
                + "  ❌ <b>Cancel</b> — Discard and start over";
    }

    public static String diffTimeout() {
        return "⏰ <b>Diff Timed Out</b>\n\n"
                + "Jenkins did not produce a diff artifact in time.\n"
                + "Check /logs for details, or try /diff again.";
    }

    // ─── APPROVE / APPLY ───────────────────────────────────────────────

    public static String applyTriggered(String pipelineName, String schemaName) {
        return "🚀 <b>Apply Job Triggered</b>\n"
                + DIVIDER + "\n\n"
                + "📌 Pipeline: <code>" + escapeHtml(pipelineName) + "</code>\n"
                + "📊 Schema: <code>" + escapeHtml(schemaName != null ? schemaName : "N/A") + "</code>\n\n"
                + "The schema changes are being applied.\n"
                + "You'll be notified when the job completes.\n\n"
                + "⚠️ <b>Do not trigger another job until this one finishes.</b>";
    }

    public static String applySuccess() {
        return "✅ <b>Changes Applied Successfully</b>\n"
                + DIVIDER + "\n\n"
                + "The schema changes have been applied to the database.\n\n"
                + "Use /pipelines to start a new workflow.";
    }

    // ─── LOGS ──────────────────────────────────────────────────────────

    public static String logsHeader(String pipelineName) {
        return "📋 <b>Jenkins Logs</b>\n"
                + DIVIDER + "\n"
                + "📌 Pipeline: <code>" + escapeHtml(pipelineName) + "</code>\n"
                + DIVIDER;
    }

    // ─── STATUS ────────────────────────────────────────────────────────

    public static String status(SessionData session) {
        BotState state = session.getState();
        StringBuilder sb = new StringBuilder();
        sb.append("📊 <b>Session Status</b>\n");
        sb.append(DIVIDER).append("\n\n");
        sb.append(state.getEmoji()).append(" State: <b>").append(state.getDisplayName()).append("</b>\n");
        sb.append("<i>").append(state.getDescription()).append("</i>\n\n");

        String pipeline = session.getSelectedPipeline();
        if (pipeline != null) {
            sb.append("📌 Pipeline: <code>").append(escapeHtml(pipeline)).append("</code>\n");
        }

        if (session.getPendingParams() != null && !session.getPendingParams().isEmpty()) {
            int total = session.getPendingParams().size();
            int confirmed = session.getConfirmedParams() != null ? session.getConfirmedParams().size() : 0;
            sb.append("⚙️ Parameters: ").append(confirmed).append("/").append(total).append(" confirmed\n");
            sb.append(progressBar(confirmed, total)).append("\n");
        }

        if (session.getLastBuildNumber() != null) {
            sb.append("🔨 Last Build: #").append(session.getLastBuildNumber()).append("\n");
        }

        if (!session.getEnv().isEmpty()) {
            sb.append("\n<b>Parameters:</b>\n");
            session.getEnv().forEach((k, v) -> {
                boolean isConfirmed = session.getConfirmedParams() != null && session.getConfirmedParams().contains(k);
                String icon = isConfirmed ? "✅" : "✏️";
                sb.append(icon).append(" <code>").append(escapeHtml(k)).append("</code> = <code>")
                        .append(escapeHtml(truncate(v, 30))).append("</code>\n");
            });
        }

        return sb.toString();
    }

    // ─── CANCEL ────────────────────────────────────────────────────────

    public static String cancelled() {
        return "❌ <b>Operation Cancelled</b>\n\n"
                + "Session has been cleared.\n"
                + "Use /pipelines to start a new workflow.";
    }

    // ─── ERRORS ────────────────────────────────────────────────────────

    public static String sessionExpired() {
        return "⏰ <b>Session Expired</b>\n\n"
                + "Your session has expired or was not found.\n"
                + "Use /start to begin a new session.";
    }

    public static String noPipelineSelected() {
        return "⚠️ <b>No Pipeline Selected</b>\n\n"
                + "Please select a pipeline first with /pipelines.";
    }

    public static String error(String message) {
        return "❌ <b>Error</b>\n\n" + escapeHtml(message);
    }

    /**
     * Extracts a human-readable error message from a throwable, never returns null.
     */
    public static String safeErrorMessage(Throwable t) {
        if (t == null) return "Unknown error";
        String msg = t.getMessage();
        if (msg != null && !msg.isBlank()) return msg;
        Throwable cause = t.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage();
        }
        return t.getClass().getSimpleName();
    }

    public static String wrongState(BotState current, String action) {
        return "⚠️ <b>Invalid Action</b>\n\n"
                + "Cannot <i>" + escapeHtml(action) + "</i> while in state:\n"
                + current.getEmoji() + " <b>" + current.getDisplayName() + "</b>\n\n"
                + "Use /status to check your current session, or /cancel to reset.";
    }

    public static String privateOnly() {
        return "🔒 I only work in private chats. Please message me directly!";
    }

    public static String paramDeleted(String key) {
        return "🗑 Parameter <code>" + escapeHtml(key) + "</code> has been removed.\n"
                + "Send a new value or continue with other parameters.";
    }

    public static String paramNotFound(String key) {
        return "⚠️ Parameter <code>" + escapeHtml(key) + "</code> not found in current session.";
    }

    // ─── TABLE DETAILS (parsed diff) ───────────────────────────────────

    public static String tableColumns(String tableName, List<Map<String, Object>> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 <b>").append(escapeHtml(tableName)).append("</b> — Columns\n");
        sb.append(DIVIDER).append("\n\n");
        if (columns == null || columns.isEmpty()) {
            sb.append("<i>No column data available.</i>");
        } else {
            for (Map<String, Object> col : columns) {
                sb.append("  • <code>").append(col.get("columnName")).append("</code>");
                if (col.get("type") != null) {
                    sb.append(" <i>(").append(col.get("type")).append(")</i>");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    // ─── UTILITIES ─────────────────────────────────────────────────────

    private static String progressBar(int done, int total) {
        if (total == 0) return "░░░░░░░░░░ 0%";
        int filled = (int) ((done * 10.0) / total);
        int empty = 10 - filled;
        int pct = (int) ((done * 100.0) / total);
        return "▓".repeat(filled) + "░".repeat(empty) + " " + pct + "%";
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "…" : s;
    }

    public static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
