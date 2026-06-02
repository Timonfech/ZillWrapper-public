package com.zillya.timonfech.zillwrapper.core.transport.telegram.commands;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import org.springframework.stereotype.Component;

@Component
public class ResendCommand implements TelegramCommand {
    @Override
    public String getName() {
        return "resend";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"переслати", "переслать"};
    }

    @Override
    public OperationType getTargetOperationType() {
        return OperationType.RESEND_NOTIFICATION;
    }
}
