package com.zillya.timonfech.zillwrapper.core.interactions.commands;

import com.zillya.timonfech.zillwrapper.core.search.SearchSession;

public interface SourceInteractionPresenter {
    void presentView(SearchSession session, boolean actionMode, String actionLabel, String sessionId, Integer editMessageId);
    void presentMessage(Long chatId, Integer messageThreadId, String text);
    void onSessionExpired(SearchSession session);
}
