package com.example.schemasyncbot.commands;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandRegistryTest {

    @Test
    void register_and_get_returnsCommand() {
        CommandRegistry registry = new CommandRegistry();
        BotCommand cmd = mock(BotCommand.class);
        when(cmd.getCommand()).thenReturn("/test");

        registry.register(cmd);

        assertThat(registry.get("/test")).isSameAs(cmd);
    }

    @Test
    void contains_registered_returnsTrue() {
        CommandRegistry registry = new CommandRegistry();
        BotCommand cmd = mock(BotCommand.class);
        when(cmd.getCommand()).thenReturn("/start");
        registry.register(cmd);

        assertThat(registry.contains("/start")).isTrue();
    }

    @Test
    void contains_notRegistered_returnsFalse() {
        CommandRegistry registry = new CommandRegistry();
        assertThat(registry.contains("/unknown")).isFalse();
    }

    @Test
    void getAll_returnsAllRegistered() {
        CommandRegistry registry = new CommandRegistry();
        BotCommand cmd1 = mock(BotCommand.class);
        when(cmd1.getCommand()).thenReturn("/a");
        BotCommand cmd2 = mock(BotCommand.class);
        when(cmd2.getCommand()).thenReturn("/b");
        registry.register(cmd1);
        registry.register(cmd2);

        assertThat(registry.getAll()).hasSize(2);
        assertThat(registry.getAll()).containsKeys("/a", "/b");
    }

    @Test
    void get_unregistered_returnsNull() {
        CommandRegistry registry = new CommandRegistry();
        assertThat(registry.get("/missing")).isNull();
    }
}
