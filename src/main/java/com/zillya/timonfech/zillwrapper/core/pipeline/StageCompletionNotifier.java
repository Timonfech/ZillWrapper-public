package com.zillya.timonfech.zillwrapper.core.pipeline;

public interface StageCompletionNotifier {

    void notifyStageCompletion(StageCompletionNotification notification);
}

