package com.zillya.timonfech.zillwrapper.core.transport.telegram.commands;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import org.springframework.stereotype.Component;

@Component
public class DetachCommand implements TelegramCommand {
    @Override
    public String getName() {
        return "detach";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"відкріпити", "открепить"};
    }

    @Override
    public OperationType getTargetOperationType() {
        return OperationType.DETACH_ACTIVATIONS;
    }
}
