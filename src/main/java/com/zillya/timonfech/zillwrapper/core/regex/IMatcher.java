package com.zillya.timonfech.zillwrapper.core.regex;


import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public interface IMatcher<T> {

    Optional<T> match(String text) throws MatchingException;

    MatchingException getCause();

    default T matchOrThrow(String text) throws MatchingException {
        return match(text).orElseThrow(this::getCause);
    }

    default MatchingException fail(int index, String expected, String text, String matchedPart) {
        StringBuilder sb = new StringBuilder();
        if (index < 0) {
            sb.append("Matching failed at the beginning. Expected: ").append(expected)
                    .append("\nText: ").append(text);
        } else {
            sb.append("Matching failed at index ").append(index)
                    .append(". Expected: ").append(expected).append("\n")
                    .append(text).append("\n");
            sb.append(" ".repeat(index));
            sb.append("^");
        }
        return new MatchingException(sb.toString(), index, expected, text, matchedPart);
    }

    static <T> IMatcher<T> regex(String regex, Function<Matcher, T> mapper) {
        return new IMatcher<T>() {
            private final Pattern pattern = Pattern.compile(regex,
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
            private MatchingException cause;

            @Override
            public Optional<T> match(String text) throws MatchingException {
                if (text == null) {
                    this.cause = fail(-1, regex, "null", "");
                    return Optional.empty();
                }
                Matcher m = pattern.matcher(text);
                if (m.find()) {
                    try {
                        T result = mapper.apply(m);
                        if (result != null)
                            return Optional.of(result);
                    } catch (Exception e) {
                        String message = String.format(
                                "Mapping failed: %s: %s",
                                e.getClass().getSimpleName(),
                                e.getMessage()
                        );
                        this.cause = new MatchingException(
                                message,
                                m.start(),
                                regex,
                                text,
                                m.group(0)
                        );
                        return Optional.empty();
                    }
                    // mapper returned null
                    this.cause = fail(m.start(), regex, text, m.group());
                    return Optional.empty();
                }
                // nothing matched
                this.cause = fail(-1, regex, text, "");
                return Optional.empty();
            }

            @Override
            public MatchingException getCause() {
                return cause;
            }
        };
    }

    static <T> IMatcher<T> firstOf(String description, List<IMatcher<T>> matchers) {
        return new IMatcher<T>() {
            private MatchingException cause;

            @Override
            public Optional<T> match(String text) throws MatchingException {
                for (var m : matchers) {
                    var res = m.match(text);
                    if (res.isPresent())
                        return res;
                }
                this.cause = fail(-1, description, text, "");
                return Optional.empty();
            }

            @Override
            public MatchingException getCause() {
                return cause;
            }
        };
    }
}
