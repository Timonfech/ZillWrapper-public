package com.zillya.timonfech.zillwrapper.core.transport.telegram.commands;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import org.springframework.stereotype.Component;

@Component
public class BlockCommand implements TelegramCommand {
    @Override
    public String getName() {
        return "block";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"заблокувати", "заблокировать"};
    }

    @Override
    public OperationType getTargetOperationType() {
        return OperationType.MODIFY_STATUS;
    }
}

