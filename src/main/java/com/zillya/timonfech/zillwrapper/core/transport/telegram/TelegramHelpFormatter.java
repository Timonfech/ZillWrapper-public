package com.zillya.timonfech.zillwrapper.core.transport.telegram;

import com.zillya.timonfech.zillwrapper.core.transport.telegram.commands.TelegramCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TelegramHelpFormatter {

    private final MessageSource messageSource;

    public String format(Locale locale, List<TelegramCommand> commands) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>").append(esc(msg("telegram.help.header", locale))).append("</b>").append("\n");
        sb.append(esc(msg("telegram.help.intro", locale))).append("\n\n");

        sb.append("<b>").append(esc(msg("telegram.help.section.commands", locale))).append("</b>").append("\n");
        sb.append(commandBlock("/start, /menu", "-", esc(msg("telegram.help.cmd.start", locale)), locale));
        sb.append(commandBlock("/help", "-", esc(msg("telegram.help.cmd.help", locale)), locale));
        sb.append(commandBlock("/enrichment", "-", esc(msg("telegram.help.cmd.enrichment", locale)), locale));
        sb.append(commandBlock("/cancel", "-", esc(msg("telegram.help.cmd.cancel", locale)), locale));

        List<TelegramCommand> sorted = commands.stream()
                .sorted(Comparator.comparing(c -> c.getName().toLowerCase(Locale.ROOT)))
                .toList();

        appendCommand(sorted, sb, locale, "resend");
        appendCommand(sorted, sb, locale, "s");

        sb.append("  ").append(esc(msg("telegram.help.search.usedby", locale))).append("\n\n");
        appendCommand(sorted, sb, locale, "block");
        appendCommand(sorted, sb, locale, "allow");
        appendCommand(sorted, sb, locale, "detach");

        sb.append("\n<b>").append(esc(msg("telegram.help.section.search", locale))).append("</b>").append("\n");
        sb.append("<b>").append(esc(msg("telegram.help.label.usage", locale))).append("</b>").append("\n");
        sb.append("  <code>/s [entity=order|license] [selectors]</code>\n");
        sb.append("  <code>/block|/allow|/detach|/resend [selectors]</code>\n");
        sb.append("  ").append(esc(msg("telegram.help.search.forms", locale))).append("\n\n");
        sb.append("<b>").append(esc(msg("telegram.help.label.parameters", locale))).append("</b>").append("\n");
        sb.append(paramLine("wzid=<id> | wzid <id>", esc(msg("telegram.help.search.wzid", locale))));
        sb.append(paramLine("wid2=<id> | wid2 <id>", esc(msg("telegram.help.search.wid2", locale))));
        sb.append(paramLine("pid=<id> | pid <id>", esc(msg("telegram.help.search.pid", locale))));
        sb.append(paramLine("woid=<id> | woid <id>", esc(msg("telegram.help.search.woid", locale))));
        sb.append(paramLine("lex=<id> | lex <id>", esc(msg("telegram.help.search.lex", locale))));
        sb.append(paramLine("kid=<id> | kid <id>", esc(msg("telegram.help.search.kid", locale))));
        sb.append(paramLine("kon=<str> | kon <str>", esc(msg("telegram.help.search.kon", locale))));
        sb.append(paramLine("kof=<str> | kof <str>", esc(msg("telegram.help.search.kof", locale))));
        sb.append(paramLine("comment=<text> | comment \"text\"", esc(msg("telegram.help.search.comment", locale))));
        sb.append(paramLine("-p <name>", esc(msg("telegram.help.search.product", locale))));
        sb.append("\n<b>").append(esc(msg("telegram.help.label.examples", locale))).append("</b>").append("\n");
        sb.append(esc(msg("telegram.help.search.examples", locale))).append("\n");

        sb.append("\n<b>").append(esc(msg("telegram.help.section.order", locale))).append("</b>").append("\n");
        sb.append("<b>").append(esc(msg("telegram.help.label.syntax", locale))).append("</b>").append("\n");
        sb.append("<code>").append(esc(msg("telegram.help.order.syntax", locale))).append("</code>").append("\n\n");
        sb.append("<b>").append(esc(msg("telegram.help.label.example", locale))).append("</b>").append("\n");
        sb.append(esc(msg("telegram.help.order.example", locale))).append("\n\n");
        sb.append("<b>").append(esc(msg("telegram.help.label.flags", locale))).append("</b>").append("\n");
        sb.append(paramLine("-e / -excel (neg: -ne / -nexcel)", esc(msg("telegram.help.order.flag.excel", locale))));
        sb.append(paramLine("-t / -text", esc(msg("telegram.help.order.flag.text", locale))));
        sb.append(paramLine("-s / -subscribe (neg: -uns / -unsubscribe)", esc(msg("telegram.help.order.flag.subscribe", locale))));
        sb.append(paramLine("-sd / -subscribe-detailed", esc(msg("telegram.help.order.flag.subscribe_detailed", locale))));
        sb.append(paramLine("-c / -client", esc(msg("telegram.help.order.flag.client", locale))));
        sb.append(paramLine("-p / -partner (neg: -np / -notpartner)", esc(msg("telegram.help.order.flag.partner", locale))));
        sb.append("\n<b>").append(esc(msg("telegram.help.label.parameters", locale))).append("</b>").append("\n");
        sb.append(paramLine("-l <locale>", esc(msg("telegram.help.order.param.locale", locale))));
        sb.append(paramLine("-wl <lead>", esc(msg("telegram.help.order.param.warning", locale))));
        sb.append(paramLine("-si <minutes>", esc(msg("telegram.help.order.param.interval", locale))));
        sb.append(paramLine("-p / -partner", esc(msg("telegram.help.order.param.partner_on", locale))));
        sb.append(paramLine("-np / -notpartner", esc(msg("telegram.help.order.param.partner_off", locale))));
        sb.append("\n<b>").append(esc(msg("telegram.help.label.notes", locale))).append("</b>").append("\n");
        sb.append(esc(msg("telegram.help.order.note", locale))).append("\n");

        sb.append("\n<b>").append(esc(msg("telegram.help.section.enrichment_settings", locale))).append("</b>").append("\n");
        sb.append("- ").append(esc(msg("telegram.help.enrichment.settings.license", locale))).append("\n");
        sb.append("- ").append(esc(msg("telegram.help.enrichment.settings.activations", locale))).append("\n");
        sb.append("- ").append(esc(msg("telegram.help.enrichment.settings.warning", locale))).append("\n");

        return sb.toString();
    }

    private String commandBlock(String usage, String aliases, String desc, Locale locale) {
        String safeAliases = aliases == null || aliases.isBlank() ? "-" : aliases;
        StringBuilder sb = new StringBuilder();
        sb.append("  <code>").append(esc(usage)).append("</code>").append("\n")
                .append("    ").append(desc).append("\n");
        if (!"-".equals(safeAliases)) {
            sb.append("    ").append(esc(msg("telegram.help.label.aliases", locale))).append(": ").append(esc(safeAliases)).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String paramLine(String key, String description) {
        return "  <code>" + esc(key) + "</code> - " + esc(description) + "\n";
    }

    private String formatAliases(String[] aliases) {
        if (aliases == null || aliases.length == 0) {
            return "-";
        }
        return Arrays.stream(aliases)
                .filter(a -> a != null && !a.isBlank())
                .map(a -> "/" + a.trim())
                .collect(Collectors.joining(", "));
    }

    private void appendCommand(List<TelegramCommand> sorted, StringBuilder sb, Locale locale, String name) {
        sorted.stream()
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .findFirst()
                .ifPresent(cmd -> {
                    String usageKey = "telegram.help.cmd." + cmd.getName() + ".usage";
                    String descKey = "telegram.help.cmd." + cmd.getName() + ".desc";
                    String aliases = formatAliases(cmd.getAliases());
                    sb.append(commandBlock(msg(usageKey, locale), aliases, msg(descKey, locale), locale));
                });
    }

    private String msg(String code, Locale locale) {
        return messageSource.getMessage(code, null, code, locale);
    }

    private String esc(String raw) {
        if (raw == null) {
            return "";
        }
        return raw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
