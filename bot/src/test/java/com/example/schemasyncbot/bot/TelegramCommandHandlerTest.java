package com.example.schemasyncbot.bot;

import com.example.schemasyncbot.commands.BotCommand;
import com.example.schemasyncbot.commands.CommandRegistry;
import com.example.schemasyncbot.commands.CallbackQueryHandler;
import com.example.schemasyncbot.model.SessionData;
import com.example.schemasyncbot.service.IJenkinsPollingService;
import com.example.schemasyncbot.service.IParameterInputService;
import com.example.schemasyncbot.service.JenkinsService;
import com.example.schemasyncbot.service.RedisSessionService;
import com.example.schemasyncbot.service.SchemaSyncService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.*;
import com.pengrad.telegrambot.request.SendMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TelegramCommandHandlerTest {
    private TelegramBot bot;
    private SchemaSyncService schemaSync;
    private JenkinsService jenkins;
    private RedisSessionService redisSessionService;
    private CommandRegistry commandRegistry;
    private IParameterInputService parameterInputService;
    private IJenkinsPollingService jenkinsPollingService;
    private CallbackQueryHandler callbackQueryHandler;
    private TelegramCommandHandler handler;
    private Update update;
    private Message message;
    private Chat chat;
    private SessionData session;
    private final Long chatId = 123L;

    @BeforeEach
    void setUp() {
        bot = mock(TelegramBot.class);
        schemaSync = mock(SchemaSyncService.class);
        jenkins = mock(JenkinsService.class);
        redisSessionService = mock(RedisSessionService.class);
        commandRegistry = mock(CommandRegistry.class);
        parameterInputService = mock(IParameterInputService.class);
        jenkinsPollingService = mock(IJenkinsPollingService.class);
        callbackQueryHandler = mock(CallbackQueryHandler.class);
        handler = new TelegramCommandHandler(bot, schemaSync, jenkins, redisSessionService,
                commandRegistry, parameterInputService, jenkinsPollingService,
                callbackQueryHandler, "pipelines");
        update = mock(Update.class);
        message = mock(Message.class);
        chat = mock(Chat.class);
        session = new SessionData();
        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(chatId);
        when(chat.type()).thenReturn(Chat.Type.Private);
        when(redisSessionService.getSession(chatId)).thenReturn(session);
    }

    @Test
    void testHandle_CallbackQuery_PrivateChat() {
        CallbackQuery callback = mock(CallbackQuery.class);
        Message cbMsg = mock(Message.class);
        Chat cbChat = mock(Chat.class);
        when(update.callbackQuery()).thenReturn(callback);
        when(callback.maybeInaccessibleMessage()).thenReturn(cbMsg);
        when(cbMsg.chat()).thenReturn(cbChat);
        when(cbChat.type()).thenReturn(Chat.Type.Private);
        handler.handle(update);
        verify(callbackQueryHandler, times(1)).handle(callback);
    }

    @Test
    void testHandle_CallbackQuery_NonPrivateChat() {
        CallbackQuery callback = mock(CallbackQuery.class);
        Message cbMsg = mock(Message.class);
        Chat cbChat = mock(Chat.class);
        when(update.callbackQuery()).thenReturn(callback);
        when(callback.maybeInaccessibleMessage()).thenReturn(cbMsg);
        when(cbMsg.chat()).thenReturn(cbChat);
        when(cbChat.type()).thenReturn(Chat.Type.group);
        handler.handle(update);
        verify(callbackQueryHandler, never()).handle(any());
    }

    @Test
    void testHandle_NonPrivateChat_Message() {
        when(chat.type()).thenReturn(Chat.Type.group);
        when(message.text()).thenReturn("/start");
        handler.handle(update);
        verify(bot).execute(argThat((SendMessage m) ->
                m.getParameters().get("text").toString().contains("I only work in private chats")));
    }

    @Test
    void testHandle_Command_Dispatch_Start() {
        when(message.text()).thenReturn("/start");
        BotCommand cmd = mock(BotCommand.class);
        when(commandRegistry.contains("/start")).thenReturn(true);
        when(commandRegistry.get("/start")).thenReturn(cmd);
        when(cmd.handle(update)).thenReturn(true);
        handler.handle(update);
        verify(cmd).handle(update);
    }

    @Test
    void testHandle_Command_Dispatch_Diff() {
        when(message.text()).thenReturn("/diff");
        BotCommand cmd = mock(BotCommand.class);
        when(commandRegistry.contains("/diff")).thenReturn(true);
        when(commandRegistry.get("/diff")).thenReturn(cmd);
        when(cmd.handle(update)).thenReturn(true);
        handler.handle(update);
        verify(cmd).handle(update);
    }

    @Test
    void testHandle_Command_Dispatch_Approve() {
        when(message.text()).thenReturn("/approve");
        BotCommand cmd = mock(BotCommand.class);
        when(commandRegistry.contains("/approve")).thenReturn(true);
        when(commandRegistry.get("/approve")).thenReturn(cmd);
        when(cmd.handle(update)).thenReturn(true);
        handler.handle(update);
        verify(cmd).handle(update);
    }

    @Test
    void testHandle_Command_Dispatch_Commands() {
        when(message.text()).thenReturn("/commands");
        BotCommand cmd = mock(BotCommand.class);
        when(commandRegistry.contains("/commands")).thenReturn(true);
        when(commandRegistry.get("/commands")).thenReturn(cmd);
        when(cmd.handle(update)).thenReturn(true);
        handler.handle(update);
        verify(cmd).handle(update);
    }

    @Test
    void testHandle_ParameterInput_EditingParam() {
        session.setEditingParam("foo");
        when(message.text()).thenReturn("bar");
        when(parameterInputService.handle(chatId, session, "bar")).thenReturn(true);
        handler.handle(update);
        verify(parameterInputService).handle(chatId, session, "bar");
        verify(redisSessionService).saveSession(chatId, session);
    }

    @Test
    void testHandle_ParameterInput_KeyValue() {
        when(message.text()).thenReturn("foo=bar");
        when(parameterInputService.handle(chatId, session, "foo=bar")).thenReturn(true);
        handler.handle(update);
        verify(parameterInputService).handle(chatId, session, "foo=bar");
        verify(redisSessionService).saveSession(chatId, session);
    }

    @Test
    void testHandle_UnknownInput_SendsHelpMessage() {
        when(message.text()).thenReturn("random text");
        when(parameterInputService.handle(anyLong(), any(), anyString())).thenReturn(false);
        handler.handle(update);
        verify(bot).execute(argThat((SendMessage m) ->
                m.getParameters().get("text").toString().contains("I didn't understand that")));
    }

    @Test
    void testHandle_NullText_Ignored() {
        when(message.text()).thenReturn(null);
        handler.handle(update);
        verify(commandRegistry, never()).contains(anyString());
        verify(bot, never()).execute(any(SendMessage.class));
    }

    @Test
    void testHandle_BlankText_Ignored() {
        when(message.text()).thenReturn("   ");
        handler.handle(update);
        verify(commandRegistry, never()).contains(anyString());
        verify(bot, never()).execute(any(SendMessage.class));
    }

    @Test
    void testHandle_NullMessage_Ignored() {
        when(update.message()).thenReturn(null);
        when(update.callbackQuery()).thenReturn(null);
        handler.handle(update);
        verify(bot, never()).execute(any(SendMessage.class));
    }
}
