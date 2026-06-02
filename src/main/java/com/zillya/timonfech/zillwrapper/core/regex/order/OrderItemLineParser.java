package com.zillya.timonfech.zillwrapper.core.regex.order;

import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.order.OutputType;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriod;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriodUnit;
import com.zillya.timonfech.zillwrapper.core.regex.MatchingException;
import com.zillya.timonfech.zillwrapper.core.regex.NaturalDurationMatcher;
import com.zillya.timonfech.zillwrapper.core.regex.flags.*;
import com.zillya.timonfech.zillwrapper.core.routing.OrderItemOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OrderItemLineParser {

    private static final String PERIOD_UNIT_PATTERN =
            "(?:y(?:ears?|rs?)?|m(?:on(?:ths?)?)?|d(?:ays?)?|"
                    + "р(?:[іо]к\\p{L}*)?|г(?:од\\p{L}*)?|л(?:ет\\p{L}*)?|"
                    + "м(?:[іе]с\\p{L}*)?|д(?:е?н\\p{L}*)?)";
    private static final String PC_GROUP = "(?<pc>\\d+)\\s*(?:пк|pc)?";
    private static final String PERIOD_GROUP = "(?<period>\\d+(?:\\s*" + PERIOD_UNIT_PATTERN + ")?)";
    private static final Pattern CORE_PATTERN = Pattern.compile(
            "^" + PC_GROUP + "\\s*[\\\\/]\\s*" + PERIOD_GROUP + "(?<tail>.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
    );
    private static final Pattern KEY_TYPES_PATTERN = Pattern.compile(
            "(?<keyTypes>"
                    + KeyTypeAliasParser.TOKEN_PATTERN
                    + "(?:\\s*(?:" + KeyTypeAliasParser.SEPARATOR_PATTERN + ")\\s*(?:" + KeyTypeAliasParser.TOKEN_PATTERN + "))*)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
    );
    private static final Pattern COUNT_PATTERN = Pattern.compile(
            "(?<!\\S)(?:-\\s*(?<dashCount>\\d+)\\s*(?:шт|pcs?)?|(?<unitCount>\\d+)\\s*(?:шт|pcs?))\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
    );
    private static final Pattern ITEM_SHAPE_PATTERN = Pattern.compile(
            ".*\\d+\\s*(?:пк|pc)?\\s*[\\\\/]\\s*\\d+.*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
    );

    private final OrderItemLineTokenizer tokenizer;
    private final KeyTypeAliasParser keyTypeAliasParser;
    private final FlagParser flagParser;
    private final ParameterFlagParser parameterFlagParser;
    private final NaturalDurationMatcher durationMatcher = new NaturalDurationMatcher();

    public OrderItemLineParser(OrderItemLineTokenizer tokenizer,
                               KeyTypeAliasParser keyTypeAliasParser,
                               @Qualifier("orderFlagParser") FlagParser flagParser,
                               @Qualifier("orderItemParameterFlagParser") ParameterFlagParser parameterFlagParser) {
        this.tokenizer = tokenizer;
        this.keyTypeAliasParser = keyTypeAliasParser;
        this.flagParser = flagParser;
        this.parameterFlagParser = parameterFlagParser;
    }

    public boolean looksLikeItemLine(String line) {
        return tokenizer.looksLikeItemLine(line);
    }

    public boolean looksLikeItemCandidate(String line) {
        return line != null && ITEM_SHAPE_PATTERN.matcher(line).matches();
    }

    public ParsedOrderItem parse(String line, int lineNumber, ParsedOrderFlags globalFlags) throws OrderParseException {
        OrderItemLineParts parts = tokenizer.tokenizeOrThrow(line);
        Matcher matcher = CORE_PATTERN.matcher(parts.specText());
        if (!matcher.matches()) {
            throw new OrderParseException("Order item line " + lineNumber + " has invalid format\n"
                    + coreFormatFailure(line, parts));
        }

        ConsumableTail tail = new ConsumableTail(matcher.group("tail"));
        int count = consumeCount(tail);
        List<KeyType> keyTypes = consumeKeyTypes(tail);
        ParsedOrderFlags itemFlags = consumeFlags(tail);
        ParsedOrderFlags parameterizedItemFlags = consumeParameterFlags(tail, itemFlags);
        String leftover = tail.leftover();
        if (!leftover.isBlank()) {
            throw new OrderParseException("Unknown item tail on line " + lineNumber + ": " + leftover);
        }

        int computers = parseIntOrDefault(matcher.group("pc"), 1);
        BusinessPeriod period = parsePeriod(matcher.group("period"));
        ProductInfo product = parts.product();
        validateProductSpecificPeriod(product, period, lineNumber);
        Boolean resolvedSubscribeFlag = parameterizedItemFlags.subscribe() != null
                ? parameterizedItemFlags.subscribe()
                : globalFlags.subscribe();
        boolean subscribed = resolvedSubscribeFlag != null ? resolvedSubscribeFlag : true;
        boolean subscribeExplicit = resolvedSubscribeFlag != null;
        boolean detailed = parameterizedItemFlags.detailed() != null ? parameterizedItemFlags.detailed() : Boolean.TRUE.equals(globalFlags.detailed());
        boolean notifyClient = parameterizedItemFlags.notifyClient() != null ? parameterizedItemFlags.notifyClient() : Boolean.TRUE.equals(globalFlags.notifyClient());
        String warningLeadRaw = parameterizedItemFlags.warningLeadRaw() != null ? parameterizedItemFlags.warningLeadRaw() : globalFlags.warningLeadRaw();
        Integer intervalMinutes = parameterizedItemFlags.subscriptionIntervalMinutes() != null
                ? parameterizedItemFlags.subscriptionIntervalMinutes()
                : globalFlags.subscriptionIntervalMinutes();
        boolean excel = resolveExcel(count, globalFlags, itemFlags);
        boolean textExplicit = Boolean.TRUE.equals(itemFlags.text() != null ? itemFlags.text() : globalFlags.text());

        List<OutputType> outputTypes = new ArrayList<>();
        if (textExplicit || !excel) {
            outputTypes.add(OutputType.TEXT);
        }
        if (excel) {
            outputTypes.add(OutputType.EXCEL);
        }

        Integer serverNumber = null;
        if (product.brandId() == 2 && product.productId() == 4) {
            String raw = product.properties() == null ? null : product.properties().get("server_number");
            serverNumber = parseNullableInt(raw).orElse(1);
        }

        return new ParsedOrderItem(
                product,
                count,
                period,
                computers,
                outputTypes,
                keyTypes,
                subscribed,
                new OrderItemOptions(serverNumber, detailed, notifyClient, warningLeadRaw, intervalMinutes, subscribeExplicit)
        );
    }

    private int consumeCount(ConsumableTail tail) throws OrderParseException {
        Matcher matcher = COUNT_PATTERN.matcher(tail.text());
        Integer count = null;
        while (matcher.find()) {
            if (!tail.canConsume(matcher.start(), matcher.end())) {
                continue;
            }
            if (count != null) {
                throw new OrderParseException("Duplicate order item count: " + matcher.group());
            }
            String raw = matcher.group("dashCount") != null ? matcher.group("dashCount") : matcher.group("unitCount");
            count = Integer.parseInt(raw);
            tail.consume(matcher.start(), matcher.end());
        }
        return count == null ? 1 : count;
    }

    private List<KeyType> consumeKeyTypes(ConsumableTail tail) {
        Matcher matcher = KEY_TYPES_PATTERN.matcher(tail.text());
        EnumSet<KeyType> keyTypes = EnumSet.noneOf(KeyType.class);
        while (matcher.find()) {
            if (!tail.canConsume(matcher.start(), matcher.end())) {
                continue;
            }
            keyTypes.addAll(keyTypeAliasParser.parse(matcher.group("keyTypes")));
            tail.consume(matcher.start(), matcher.end());
        }
        return keyTypes.isEmpty() ? keyTypeAliasParser.parse(null) : List.copyOf(keyTypes);
    }

    private ParsedOrderFlags consumeFlags(ConsumableTail tail) {
        Boolean excel = null;
        Boolean subscribe = null;
        Boolean text = null;
        Boolean detailed = null;
        Boolean notifyClient = null;
        for (FlagParser.FlagMatch match : flagParser.findKnownFlags(tail.text())) {
            if (!tail.canConsume(match.start(), match.end())) {
                continue;
            }
            if (ExcelFlagDefinition.KEY.equals(match.key())) {
                excel = match.value();
            } else if (SubscribeFlagDefinition.KEY.equals(match.key())) {
                subscribe = match.value();
            } else if (TextFlagDefinition.KEY.equals(match.key())) {
                text = match.value();
            } else if (SubscribeDetailedFlagDefinition.KEY.equals(match.key())) {
                detailed = match.value();
            } else if (NotifyClientFlagDefinition.KEY.equals(match.key())) {
                notifyClient = match.value();
            }
            tail.consume(match.start(), match.end());
        }
        return new ParsedOrderFlags(excel, subscribe, null, text, detailed, notifyClient, null, null);
    }

    private ParsedOrderFlags consumeParameterFlags(ConsumableTail tail, ParsedOrderFlags baseFlags) throws OrderParseException {
        ParameterFlagParseResult result = parameterFlagParser.parse(tail.text());
        String warningLead = result.get(WarningLeadFlagDefinition.KEY);
        Integer interval = parseInterval(result.get(SubscriptionIntervalFlagDefinition.KEY));
        for (var match : parameterFlagParser.findKnownFlags(tail.text())) {
            if (tail.canConsume(match.start(), match.end())) {
                tail.consume(match.start(), match.end());
            }
        }
        return new ParsedOrderFlags(
                baseFlags.excel(),
                baseFlags.subscribe(),
                baseFlags.partner(),
                baseFlags.text(),
                baseFlags.detailed(),
                baseFlags.notifyClient(),
                warningLead,
                interval
        );
    }

    private Integer parseInterval(String raw) throws OrderParseException {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 1) {
                throw new OrderParseException("Subscription interval must be >= 1 minute");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new OrderParseException("Invalid subscription interval: " + raw, ex);
        }
    }

    private boolean resolveExcel(int count, ParsedOrderFlags globalFlags, ParsedOrderFlags itemFlags) {
        boolean excel = count > 1;
        if (globalFlags.excel() != null) {
            excel = globalFlags.excel();
        }
        if (itemFlags.excel() != null) {
            excel = itemFlags.excel();
        }
        return excel;
    }

    private BusinessPeriod parsePeriod(String raw) throws OrderParseException {
        if (raw == null || raw.isBlank()) {
            return new BusinessPeriod(1, BusinessPeriodUnit.YEAR);
        }
        String trimmed = raw.trim();
        if (trimmed.matches("\\d+")) {
            return new BusinessPeriod(Integer.parseInt(trimmed), BusinessPeriodUnit.YEAR);
        }
        try {
            return durationMatcher.matchOrThrow(trimmed);
        } catch (MatchingException ex) {
            throw new OrderParseException("Invalid order item period: " + raw + "\n" + ex.getMessage(), ex);
        }
    }

    private void validateProductSpecificPeriod(ProductInfo product, BusinessPeriod period, int lineNumber) throws OrderParseException {
        if (product == null || period == null) {
            return;
        }
        boolean whiteAdminPack = product.brandId() == 2 && (product.productId() == 3 || product.productId() == 4);
        if (!whiteAdminPack) {
            return;
        }
        if (period.unit() == BusinessPeriodUnit.DAY && period.amount() % 30 != 0) {
            throw new OrderParseException("Order item line " + lineNumber
                    + " has invalid period for ZAB/ZIS2: day-based period must be a multiple of 30");
        }
    }

    private String coreFormatFailure(String line, OrderItemLineParts parts) {
        int specStart = Math.max(0, line.indexOf(parts.specText()));
        String expected = "<pc>/<period>, where period can be: 1y, 1д, 1year, 12month, 30days";
        return "Matching failed at index " + specStart + ". Expected: " + expected + "\n"
                + line + "\n"
                + " ".repeat(specStart) + "^\n"
                + "Item spec was: " + parts.specText();
    }

    private int parseIntOrDefault(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(raw.trim());
    }

    private java.util.Optional<Integer> parseNullableInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ex) {
            return java.util.Optional.empty();
        }
    }

    private static final class ConsumableTail {

        private final String text;
        private final boolean[] consumed;

        private ConsumableTail(String text) {
            this.text = text == null ? "" : text;
            this.consumed = new boolean[this.text.length()];
        }

        private String text() {
            return text;
        }

        private boolean canConsume(int start, int end) {
            for (int i = start; i < end; i++) {
                if (consumed[i]) {
                    return false;
                }
            }
            return true;
        }

        private void consume(int start, int end) {
            for (int i = start; i < end; i++) {
                consumed[i] = true;
            }
        }

        private String leftover() {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                if (!consumed[i]) {
                    builder.append(text.charAt(i));
                }
            }
            return builder.toString().trim();
        }
    }
}
