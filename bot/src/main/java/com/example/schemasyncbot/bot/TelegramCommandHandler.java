package com.example.schemasyncbot.bot;

import com.example.schemasyncbot.commands.CallbackQueryHandler;
import com.example.schemasyncbot.commands.CommandRegistry;
import com.example.schemasyncbot.commands.HelpCommand;
import com.example.schemasyncbot.commands.StatusCommand;
import com.example.schemasyncbot.commands.StartCommand;
import com.example.schemasyncbot.commands.PipelinesCommand;
import com.example.schemasyncbot.commands.CommandsCommand;
import com.example.schemasyncbot.commands.DiffCommand;
import com.example.schemasyncbot.commands.ApproveCommand;
import com.example.schemasyncbot.commands.LogsCommand;
import com.example.schemasyncbot.commands.CancelCommand;
import com.example.schemasyncbot.commands.DeleteCommand;
import com.example.schemasyncbot.commands.ConfirmCommand;
import com.example.schemasyncbot.model.SessionData;
import com.example.schemasyncbot.service.IParameterInputService;
import com.example.schemasyncbot.service.IJenkinsPollingService;
import com.example.schemasyncbot.service.JenkinsService;
import com.example.schemasyncbot.service.RedisSessionService;
import com.example.schemasyncbot.service.SchemaSyncService;
import com.example.schemasyncbot.utils.MessageTemplates;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Central update dispatcher for the SchemaSync Telegram bot.
 * <p>
 * Routes incoming Telegram updates to the appropriate handler:
 * <ol>
 *   <li>Callback queries → {@link CallbackQueryHandler}</li>
 *   <li>Slash commands   → {@link CommandRegistry}</li>
 *   <li>Text input       → {@link IParameterInputService} (key=value or editing param)</li>
 * </ol>
 * <p>
 * Only private chats are supported. Group messages are rejected.
 * Session is always saved after successful command / input handling.
 */
@Component
public class TelegramCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(TelegramCommandHandler.class);

    private final TelegramBot bot;
    private final RedisSessionService redisSessionService;
    private final CommandRegistry commandRegistry;
    private final IParameterInputService parameterInputService;
    private final CallbackQueryHandler callbackQueryHandler;

    // Injected dependencies forwarded to command constructors
    private final JenkinsService jenkins;
    private final SchemaSyncService schemaSync;
    private final IJenkinsPollingService jenkinsPollingService;

    public TelegramCommandHandler(TelegramBot bot,
                                  SchemaSyncService schemaSync,
                                  JenkinsService jenkins,
                                  RedisSessionService redisSessionService,
                                  CommandRegistry commandRegistry,
                                  IParameterInputService parameterInputService,
                                  IJenkinsPollingService jenkinsPollingService,
                                  CallbackQueryHandler callbackQueryHandler,
                                  @Value("${bot.pipelines}") String pipelinesConfig) {
        this.bot = bot;
        this.schemaSync = schemaSync;
        this.jenkins = jenkins;
        this.redisSessionService = redisSessionService;
        this.commandRegistry = commandRegistry;
        this.parameterInputService = parameterInputService;
        this.jenkinsPollingService = jenkinsPollingService;
        this.callbackQueryHandler = callbackQueryHandler;
    }

    @jakarta.annotation.PostConstruct
    public void registerCommands() {
        commandRegistry.register(new StartCommand(bot, redisSessionService));
        commandRegistry.register(new PipelinesCommand(bot, jenkins, redisSessionService));
        commandRegistry.register(new CommandsCommand(bot));
        commandRegistry.register(new DiffCommand(bot, jenkins, redisSessionService, jenkinsPollingService));
        commandRegistry.register(new ApproveCommand(bot, jenkins, redisSessionService));
        commandRegistry.register(new LogsCommand(bot, jenkins, redisSessionService));
        commandRegistry.register(new CancelCommand(bot, redisSessionService));
        commandRegistry.register(new DeleteCommand(bot, redisSessionService));
        commandRegistry.register(new ConfirmCommand(bot, jenkins, redisSessionService));
        commandRegistry.register(new HelpCommand(bot));
        commandRegistry.register(new StatusCommand(bot, redisSessionService));
    }

    /**
     * Main entry point — dispatches a Telegram update.
     */
    public void handle(Update update) {

        // ── 1. Callback queries ─────────────────────────────────────────
        if (update.callbackQuery() != null) {
            CallbackQuery callback = update.callbackQuery();
            if (callback.maybeInaccessibleMessage() != null
                    && callback.maybeInaccessibleMessage().chat() != null
                    && !isPrivateChat(callback.maybeInaccessibleMessage().chat())) {
                return;
            }
            callbackQueryHandler.handle(callback);
            return;
        }

        // ── 2. Only process messages ────────────────────────────────────
        if (update.message() == null) return;

        // ── 3. Private chat only ────────────────────────────────────────
        if (!isPrivateChat(update.message().chat())) {
            bot.execute(new SendMessage(update.message().chat().id(), MessageTemplates.privateOnly())
                    .parseMode(ParseMode.HTML));
            return;
        }

        Long chatId = update.message().chat().id();
        String text = update.message().text();
        if (text == null || text.isBlank()) return;

        // ── 4. Slash commands → CommandRegistry (single source of truth) ─
        String commandKey = text.split(" ")[0].toLowerCase();
        if (commandKey.startsWith("/") && commandRegistry.contains(commandKey)) {
            log.debug("Dispatching command '{}' for chat {}", commandKey, chatId);
            boolean handled = commandRegistry.get(commandKey).handle(update);
            if (handled) return;
        }

        // ── 5. Text input — parameter editing or key=value ──────────────
        SessionData session = redisSessionService.getSession(chatId);

        // 5a. Currently editing a parameter → accept raw value
        if (session.getEditingParam() != null && !text.startsWith("/")) {
            if (parameterInputService.handle(chatId, session, text)) {
                redisSessionService.saveSession(chatId, session);
                return;
            }
        }

        // 5b. key=value input
        if (text.contains("=") && !text.startsWith("/")) {
            if (parameterInputService.handle(chatId, session, text)) {
                redisSessionService.saveSession(chatId, session);
                return;
            }
        }

        // ── 6. Unknown input ────────────────────────────────────────────
        log.debug("Unrecognized input from chat {}: '{}'", chatId, text);
        bot.execute(new SendMessage(chatId,
                "❓ I didn't understand that.\n\n"
                        + "Use /help for usage instructions, or /commands for a quick reference.")
                .parseMode(ParseMode.HTML));
    }

    private boolean isPrivateChat(Chat chat) {
        return chat != null && chat.type() == Chat.Type.Private;
    }
}