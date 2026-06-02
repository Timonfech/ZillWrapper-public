package com.zillya.timonfech.zillwrapper.core.transport.telegram.commands;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import org.springframework.stereotype.Component;

@Component
public class SearchCommand implements TelegramCommand {
    @Override
    public String getName() {
        return "s";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"search", "find"};
    }

    @Override
    public OperationType getTargetOperationType() {
        return OperationType.LICENSE_SEARCH;
    }
}

