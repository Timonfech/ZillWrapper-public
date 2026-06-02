package com.zillya.timonfech.zillwrapper.core.transport.telegram.commands;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;

/**
 * Base interface for all Telegram bot commands.
 */
public interface TelegramCommand {
    /**
     * @return The primary command name without the leading slash (e.g. "resend")
     */
    String getName();

    /**
     * @return List of alternative names/aliases (e.g. ["переслати", "переслать"])
     */
    String[] getAliases();

    /**
     * @return The type of operation this command triggers.
     */
    OperationType getTargetOperationType();

    /**
     * Checks if the given text matches this command or its aliases.
     */
    default boolean matches(String commandText) {
        String clean = commandText.toLowerCase().replace("/", "");
        if (clean.equals(getName().toLowerCase())) return true;
        for (String alias : getAliases()) {
            if (clean.equals(alias.toLowerCase())) return true;
        }
        return false;
    }
}
