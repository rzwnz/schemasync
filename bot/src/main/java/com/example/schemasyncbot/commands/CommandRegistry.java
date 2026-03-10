package com.example.schemasyncbot.commands;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CommandRegistry {
    private final Map<String, BotCommand> commandMap = new ConcurrentHashMap<>();

    public void register(BotCommand command) {
        commandMap.put(command.getCommand(), command);
    }

    public BotCommand get(String command) {
        return commandMap.get(command);
    }

    public boolean contains(String command) {
        return commandMap.containsKey(command);
    }

    public Map<String, BotCommand> getAll() {
        return commandMap;
    }
} 