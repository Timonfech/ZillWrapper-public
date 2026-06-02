package com.zillya.timonfech.zillwrapper.core.search;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
public class SearchSession {
    private String sessionId;
    private Long chatId;
    private Long userId;
    private Long sourceId;
    private Integer uiMessageId;
    private SearchEntityType entityType;
    private OperationType actionType;
    private String actionPayload;
    private String summaryView;
    private String warningView;
    private boolean bulkConfirmArmed;
    private List<Long> pageEntityIds;
    private List<String> pageTargetRefs;
    private List<String> views;
    private int pageIndex;
    private Instant expiresAt;
    private SearchSessionState state;
}
