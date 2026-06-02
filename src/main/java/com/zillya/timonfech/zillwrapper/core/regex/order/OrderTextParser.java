package com.zillya.timonfech.zillwrapper.core.regex.order;

import com.zillya.timonfech.zillwrapper.core.regex.flags.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class OrderTextParser {

    private final OrderReferenceLineParser referenceLineParser;
    private final EmailLineParser emailLineParser;
    private final OrderItemLineParser itemLineParser;
    private final FlagParser flagParser;
    private final ParameterFlagParser parameterFlagParser;

    public OrderTextParser(OrderReferenceLineParser referenceLineParser,
                           EmailLineParser emailLineParser,
                           OrderItemLineParser itemLineParser,
                           @Qualifier("orderFlagParser") FlagParser flagParser,
                           @Qualifier("orderParameterFlagParser") ParameterFlagParser parameterFlagParser) {
        this.referenceLineParser = referenceLineParser;
        this.emailLineParser = emailLineParser;
        this.itemLineParser = itemLineParser;
        this.flagParser = flagParser;
        this.parameterFlagParser = parameterFlagParser;
    }

    public Optional<ParsedOrderRequest> tryParse(String text) throws OrderParseException {
        if (!looksLikeStructuredOrder(text)) {
            return Optional.empty();
        }
        return Optional.of(parse(text));
    }

    public boolean looksLikeStructuredOrder(String text) {
        List<String> lines = normalizedLines(text);
        if (lines.size() < 3 || !referenceLineParser.matches(lines.getFirst())) {
            return false;
        }
        int emailIndex = emailLineParser.findEmailLineIndex(lines);
        if (emailIndex < 2) {
            return false;
        }
        return lines.subList(1, emailIndex).stream().anyMatch(itemLineParser::looksLikeItemCandidate);
    }

    private ParsedOrderRequest parse(String text) throws OrderParseException {
        List<String> lines = normalizedLines(text);
        ParsedOrderReference reference = referenceLineParser.parse(lines.getFirst());
        int emailIndex = emailLineParser.findEmailLineIndex(lines);
        if (emailIndex < 2) {
            throw new OrderParseException("Email line is required after at least one order item");
        }

        Set<String> uniqueEmails = new LinkedHashSet<>();
        int lastEmailIndex = emailIndex - 1;

        for (int i = emailIndex; i < lines.size(); i++) {
            List<String> rowEmails = emailLineParser.extractEmails(lines.get(i));
            if (rowEmails.isEmpty()) {
                break;
            }
            uniqueEmails.addAll(rowEmails);
            lastEmailIndex = i;
        }
        if (uniqueEmails.isEmpty()) {
            throw new OrderParseException("At least one e-mail is required. Maybe the regex isn't working as it should.");
        }
        List<String> emails = new ArrayList<>(uniqueEmails);
        String email = emails.getFirst();

        ParsedGlobalOptions globalOptions = parseGlobalFlags(lines.subList(lastEmailIndex + 1, lines.size()));
        ParsedOrderFlags globalFlags = new ParsedOrderFlags(
                globalOptions.flags().excel(),
                globalOptions.flags().subscribe(),
                globalOptions.flags().partner(),
                globalOptions.flags().text(),
                globalOptions.flags().detailed(),
                globalOptions.flags().notifyClient(),
                globalOptions.warningLeadRaw(),
                globalOptions.subscriptionIntervalMinutes()
        );
        List<ParsedOrderItem> items = new ArrayList<>();
        for (int i = 1; i < emailIndex; i++) {
            items.add(itemLineParser.parse(lines.get(i), i + 1, globalFlags));
        }
        if (items.isEmpty()) {
            throw new OrderParseException("At least one order item is required");
        }
        return new ParsedOrderRequest(reference, email, emails, items, globalOptions.localeTag(), globalOptions.flags().partner());
    }

    private ParsedGlobalOptions parseGlobalFlags(List<String> lines) throws OrderParseException {
        if (lines.isEmpty()) {
            return new ParsedGlobalOptions(ParsedOrderFlags.empty(), null, null, null);
        }
        String joined = String.join(" ", lines);
        ParameterFlagParseResult parameterFlags = parameterFlagParser.parse(joined);
        String withoutParamFlags = parameterFlagParser.removeKnownFlags(joined);

        ParsedOrderFlags flags = parseFlags(withoutParamFlags);
        String withoutFlags = flagParser.removeKnownFlags(withoutParamFlags);
        if (!withoutFlags.isBlank()) {
            throw new OrderParseException("Unknown global flags: " + withoutFlags);
        }
        return new ParsedGlobalOptions(
                flags,
                parameterFlags.get(LocaleFlagDefinition.KEY),
                parameterFlags.get(WarningLeadFlagDefinition.KEY),
                parsePositiveInt(parameterFlags.get(SubscriptionIntervalFlagDefinition.KEY))
        );
    }

    private ParsedOrderFlags parseFlags(String flagsText) {
        if (flagsText == null || flagsText.isBlank()) {
            return ParsedOrderFlags.empty();
        }
        FlagParseResult result = flagParser.parse(flagsText);
        return new ParsedOrderFlags(
                result.get(ExcelFlagDefinition.KEY),
                result.get(SubscribeFlagDefinition.KEY),
                result.get(PartnerFlagDefinition.KEY),
                result.get(TextFlagDefinition.KEY),
                result.get(SubscribeDetailedFlagDefinition.KEY),
                result.get(NotifyClientFlagDefinition.KEY),
                null,
                null
        );
    }

    private List<String> normalizedLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return text.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private Integer parsePositiveInt(String raw) throws OrderParseException {
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

    private record ParsedGlobalOptions(ParsedOrderFlags flags,
                                       String localeTag,
                                       String warningLeadRaw,
                                       Integer subscriptionIntervalMinutes) {
    }
}
