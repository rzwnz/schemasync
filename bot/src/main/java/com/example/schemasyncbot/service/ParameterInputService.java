package com.example.schemasyncbot.service;

import com.example.schemasyncbot.model.SessionData;
import com.example.schemasyncbot.utils.MessageTemplates;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles free-text parameter input from the user.
 * <p>
 * Supports two modes:
 * <ol>
 *   <li>Editing mode — user is typing a raw value for {@code session.getEditingParam()}</li>
 *   <li>Key=value mode — user sends "KEY=value" text directly</li>
 * </ol>
 * <p>
 * The caller (TelegramCommandHandler) is responsible for saving the session
 * after this service returns {@code true}.
 */
@Service
public class ParameterInputService implements IParameterInputService {

    private static final Logger log = LoggerFactory.getLogger(ParameterInputService.class);

    private final TelegramBot bot;

    public ParameterInputService(TelegramBot bot, RedisSessionService redisSessionService) {
        this.bot = bot;
    }

    @Override
    public boolean handle(Long chatId, SessionData session, String text) {
        // ── 1. Editing a specific parameter (raw value input) ───────────
        if (session.getEditingParam() != null && !text.startsWith("/")) {
            String param = session.getEditingParam();
            String raw = text.trim();

            // If user accidentally sent "key=value", extract just the value
            if (raw.contains("=")) {
                raw = raw.split("=", 2)[1].trim();
            }

            session.getEnv().put(param, raw);
            session.setEditingParam(null);
            log.debug("Set parameter '{}' for chat {}", param, chatId);

            bot.execute(new SendMessage(chatId, MessageTemplates.parameterValueSet(param, raw))
                    .parseMode(ParseMode.HTML));
            sendParameterButtons(chatId, session);
            return true;
        }

        // ── 2. Key=value input ──────────────────────────────────────────
        if (text.contains("=") && !text.startsWith("/")) {
            String[] kv = text.split("=", 2);
            String key = kv[0].trim();
            String value = kv[1].trim();

            session.getEnv().put(key, value);
            log.debug("Set parameter '{}' = '{}' for chat {}", key, value, chatId);

            bot.execute(new SendMessage(chatId, MessageTemplates.parameterValueSet(key, value))
                    .parseMode(ParseMode.HTML));
            sendParameterButtons(chatId, session);
            return true;
        }

        return false;
    }

    private void sendParameterButtons(Long chatId, SessionData session) {
        List<String> params = session.getPendingParams();
        if (params == null || params.isEmpty()) return;

        Map<String, String> env = session.getEnv();
        Set<String> confirmed = session.getConfirmedParams();

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        for (String param : params) {
            String value = env.getOrDefault(param, "");
            String icon = confirmed.contains(param) ? "✅ " : (value.isEmpty() ? "⬜ " : "✏️ ");
            String display = icon + param;
            if (!value.isEmpty()) {
                String truncated = value.length() > 25 ? value.substring(0, 25) + "…" : value;
                display += ": " + truncated;
            }
            keyboard.addRow(new InlineKeyboardButton(display).callbackData("param:" + param));
        }

        boolean allConfirmed = params.stream().allMatch(confirmed::contains);
        if (allConfirmed) {
            keyboard.addRow(new InlineKeyboardButton("✅ All Confirmed — Use /diff").callbackData("confirm_all_params"));
        }
        keyboard.addRow(new InlineKeyboardButton("❌ Cancel").callbackData("cancel_params"));

        String text = MessageTemplates.parameterList(
                session.getSelectedPipeline(), params, env, confirmed);
        bot.execute(new SendMessage(chatId, text)
                .parseMode(ParseMode.HTML)
                .replyMarkup(keyboard));
    }
}
