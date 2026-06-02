package com.zillya.timonfech.zillwrapper.core.regex;

import lombok.Getter;

@Getter
public class MatchingException extends Exception {
    private final int index;
    private final String expectedRegex;
    private final String text;
    private final String matchedPart;

    public MatchingException(String message, int index, String expectedRegex, String text, String matchedPart) {
        super(message);
        this.index = index;
        this.expectedRegex = expectedRegex;
        this.text = text;
        this.matchedPart = matchedPart;
    }
}
