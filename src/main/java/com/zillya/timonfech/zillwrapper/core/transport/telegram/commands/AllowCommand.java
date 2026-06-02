package com.zillya.timonfech.zillwrapper.core.transport.telegram.commands;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import org.springframework.stereotype.Component;

@Component
public class AllowCommand implements TelegramCommand {
    @Override
    public String getName() {
        return "allow";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"розблокувати", "разблокировать"};
    }

    @Override
    public OperationType getTargetOperationType() {
        return OperationType.MODIFY_STATUS;
    }
}

