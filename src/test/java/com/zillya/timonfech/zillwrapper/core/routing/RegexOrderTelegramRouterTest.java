package com.zillya.timonfech.zillwrapper.core.routing;

import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.order.OutputType;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductRegistry;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductYamlPatternRegistry;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.entities.source.SourceEntity;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriodUnit;
import com.zillya.timonfech.zillwrapper.core.regex.flags.*;
import com.zillya.timonfech.zillwrapper.core.regex.order.*;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;
import com.zillya.timonfech.zillwrapper.core.source.TelegramInboundEvent;
import com.zillya.timonfech.zillwrapper.core.transport.telegram.commands.TelegramCommand;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class RegexOrderTelegramRouterTest {
    private final Faker faker = new Faker();


    private final ProductRegistry productRegistry = Mockito.mock(ProductRegistry.class);
    private final OrderRepository orderRepository = Mockito.mock(OrderRepository.class);
    private final ProductYamlPatternRegistry patternRegistry = Mockito.mock(ProductYamlPatternRegistry.class);
    private final FlagParser flagParser = new FlagParser(List.of(
            new ExcelFlagDefinition(),
            new SubscribeFlagDefinition(),
            new TextFlagDefinition()
    ));
    private final ParameterFlagParser parameterFlagParser = new ParameterFlagParser(List.of(
            new LocaleFlagDefinition()
    ));
    private final OrderTextParser orderTextParser = new OrderTextParser(
            new OrderReferenceLineParser(orderRepository),
            new EmailLineParser(),
            new OrderItemLineParser(
                    new OrderItemLineTokenizer(productRegistry),
                    new KeyTypeAliasParser(patternRegistry),
                    flagParser,
                    parameterFlagParser
            ),
            flagParser,
            parameterFlagParser
    );
    private final RegexOrderTelegramRouter router = new RegexOrderTelegramRouter(orderTextParser, List.of());

    private void mockKeyTypePatterns() {
        when(patternRegistry.getKeyTypePatterns()).thenReturn(Map.of(
                KeyType.ONLINE, Pattern.compile("onl?i?n?e?", Pattern.CASE_INSENSITIVE),
                KeyType.OFFLINE, Pattern.compile("off?l?i?n?e?", Pattern.CASE_INSENSITIVE)
        ));
    }


    @Test
    void parseRandomAmountOfEmailsWithRandomSeparatorsOnSeparateLines() throws OrderParseException{
    int emailCount = faker.number().numberBetween(1, 11);
    List<String> emails = new ArrayList<>(10);
    StringBuilder sb = new StringBuilder();

        ProductInfo zis = product("ZIS", 6, 2);
        when(productRegistry.getAllProducts()).thenReturn(List.of(zis));
        when(productRegistry.findProductByText("zis")).thenReturn(Optional.of(zis));

    sb.append("Strange ref 333").append("\n");
    sb.append("zis 1/1").append("\n");

    for (int i=0; i < emailCount; i++){
        String randomEmail = faker.internet().emailAddress();
        emails.add(randomEmail);
        String dirtyPrefix = faker.regexify("[;, \t]{0,3}");
        sb.append(dirtyPrefix).append(randomEmail).append("\n");

    }
    Optional<ParsedOrderRequest> parsedOrderRequest = orderTextParser.tryParse(sb.toString());
    assertEquals(emailCount, parsedOrderRequest.get().emails().size());
    assertTrue(parsedOrderRequest.get().emails().containsAll(emails));
    }


    @Test
    void parsesPortalOrderWithoutToSection() {
        mockKeyTypePatterns();
        ProductInfo zis = product("ZIS 3.0", 6, 2);
        when(productRegistry.getAllProducts()).thenReturn(List.of(zis));
        when(productRegistry.findProductByText("ZIS 3.0")).thenReturn(Optional.of(zis));

        OrderOperationContext context = route("""
                123
                ZIS 3.0 1pc/1year
                client@example.com
                """);

        assertEquals(123L, context.getPortalId());
        assertNull(context.getWhiteAdminId());
        assertEquals("client@example.com", context.getEmail());
        assertEquals(List.of(KeyType.ONLINE), context.getItemSpecs().getFirst().keyTypes());
        assertEquals(List.of(OutputType.TEXT), context.getItemSpecs().getFirst().outputTypes());
        assertTrue(context.getItemSpecs().getFirst().subscribed());
        assertEquals("uk", context.getLocaleTag());
    }

    @Test
    void parsesWhiteAdminOrderWithOfflineExcelItemFlags() {
        mockKeyTypePatterns();
        ProductInfo zab = product("ZAB", 4, 2);
        when(productRegistry.getAllProducts()).thenReturn(List.of(zab));
        when(productRegistry.findProductByText("ZAB")).thenReturn(Optional.of(zab));

        OrderOperationContext context = route("""
                95470
                ZAB 10pc/1year off - 2шт -e
                client@example.com
                """);

        OrderItemSpec item = context.getItemSpecs().getFirst();
        assertNull(context.getPortalId());
        assertEquals(95470L, context.getWhiteAdminId());
        assertEquals(10, item.computers());
        assertEquals(2, item.count());
        assertEquals(List.of(KeyType.OFFLINE), item.keyTypes());
        assertEquals(List.of(OutputType.EXCEL), item.outputTypes());
    }

    @Test
    void parsesUserCommentAndGlobalSubscribe() {
        mockKeyTypePatterns();
        ProductInfo zis2 = product("ZIS 2.0", 3, 2);
        when(productRegistry.getAllProducts()).thenReturn(List.of(zis2));
        when(productRegistry.findProductByText("ZIS 2.0")).thenReturn(Optional.of(zis2));

        OrderOperationContext context = route("""
                ABC123
                ZIS 2.0 1/12month on
                client@example.com
                -s
                """);

        assertEquals("ABC123", context.getUserComment());
        assertNull(context.getPortalId());
        assertNull(context.getWhiteAdminId());
        assertTrue(context.getItemSpecs().getFirst().subscribed());
    }

    @Test
    void itemFlagsOverrideGlobalFlagsAndLastFlagWins() {
        mockKeyTypePatterns();
        ProductInfo zis = product("ZIS 3.0", 6, 2);
        ProductInfo zab = product("ZAB", 4, 2);

        when(productRegistry.getAllProducts()).thenReturn(List.of(zis, zab));
        when(productRegistry.findProductByText("ZIS 3.0")).thenReturn(Optional.of(zis));
        when(productRegistry.findProductByText("ZAB")).thenReturn(Optional.of(zab));

        OrderOperationContext context = route("""
                123
                ZIS 3.0 1/1рік 2шт -ne
                ZAB 10/1year - 2шт
                client@example.com
                -e
                """);

        assertFalse(context.getItemSpecs().get(0).outputTypes().contains(OutputType.EXCEL));
        assertEquals(List.of(OutputType.EXCEL), context.getItemSpecs().get(1).outputTypes());
    }

    @Test
    void itemExcelFlagOverridesGlobalNoExcelFlag() {
        mockKeyTypePatterns();
        ProductInfo zis = product("ZIS 3.0", 6, 2);
        when(productRegistry.getAllProducts()).thenReturn(List.of(zis));

        OrderOperationContext context = route("""
                123
                ZIS 3.0 1/1year -e
                client@example.com
                -ne
                """);

        assertEquals(List.of(OutputType.EXCEL), context.getItemSpecs().getFirst().outputTypes());
    }

    @Test
    void itemSubscribeFlagIsSupported() {
        mockKeyTypePatterns();
        ProductInfo zis = product("ZIS 3.0", 6, 2);
        when(productRegistry.getAllProducts()).thenReturn(List.of(zis));

        OrderOperationContext context = route("""
                123
                ZIS 3.0 1/1year -s
                client@example.com
                """);

        assertTrue(context.getItemSpecs().getFirst().subscribed());
    }

    @Test
    void optionalItemPartsCanBeInAnyOrder() {
        mockKeyTypePatterns();
        ProductInfo zab = product("ZAB", 4, 2);
        when(productRegistry.getAllProducts()).thenReturn(List.of(zab));

        OrderOperationContext context = route("""
                95470
                ZAB 10pc/1year -e off - 2шт
                ZAB 10pc/1year 2шт -e off
                ZAB 10pc/1year off - 2шт -e
                client@example.com
                """);

        for (OrderItemSpec item : context.getItemSpecs()) {
            assertEquals(2, item.count());
            assertEquals(List.of(KeyType.OFFLINE), item.keyTypes());
            assertEquals(List.of(OutputType.EXCEL), item.outputTypes());
        }
    }

    @Test
    void acceptsSafeCountFormsButRejectsBareTailNumber() {
        mockKeyTypePatterns();
        ProductInfo zis = product("ZIS 3.0", 6, 2);
        when(productRegistry.getAllProducts()).thenReturn(List.of(zis));

        OrderOperationContext context = route("""
                123
                ZIS 3.0 1/1год - 2
                ZIS 3.0 1/1year -2
                ZIS 3.0 1/1 2шт
                ZIS 3.0 1/1year 2 pcs
                client@example.com
                """);

        assertEquals(List.of(2, 2, 2, 2), context.getItemSpecs().stream().map(OrderItemSpec::count).toList());

        assertThrows(OrderParseException.class, () -> orderTextParser.tryParse("""
                123
                ZIS 3.0 1/1year 2
                client@example.com
                """));
    }

    @Test
    void textOutputIsImplicitOnlyUntilOtherOutputAppearsUnlessTextFlagIsExplicit() {
        mockKeyTypePatterns();
        ProductInfo zis = product("ZIS 3.0", 6, 2);
        when(productRegistry.getAllProducts()).thenReturn(List.of(zis));

        OrderOperationContext context = route("""
                123
                ZIS 3.0 1/1year
                ZIS 3.0 1/1year -e
                ZIS 3.0 1/1year -e -t
                ZIS 3.0 1/1year - 2шт
                ZIS 3.0 1/1year - 2шт -t
                ZIS 3.0 1/1year - 2шт -ne
                client@example.com
                """);

        assertEquals(List.of(OutputType.TEXT), context.getItemSpecs().get(0).outputTypes());
        assertEquals(List.of(OutputType.EXCEL), context.getItemSpecs().get(1).outputTypes());
        assertEquals(List.of(OutputType.TEXT, OutputType.EXCEL), context.getItemSpecs().get(2).outputTypes());
        assertEquals(List.of(OutputType.EXCEL), context.getItemSpecs().get(3).outputTypes());
        assertEquals(List.of(OutputType.TEXT, OutputType.EXCEL), context.getItemSpecs().get(4).outputTypes());
        assertEquals(List.of(OutputType.TEXT), context.getItemSpecs().get(5).outputTypes());
    }

    @Test
    void duplicateCountAndUnknownTailAreErrors() {
        mockKeyTypePatterns();
        ProductInfo zis = product("ZIS 3.0", 6, 2);
        when(productRegistry.getAllProducts()).thenReturn(List.of(zis));

        assertThrows(OrderParseException.class, () -> orderTextParser.tryParse("""
                123
                ZIS 3.0 1/1year - 2шт - 3шт
                client@example.com
                """));

        assertThrows(OrderParseException.class, () -> orderTextParser.tryParse("""
                123
                ZIS 3.0 1/1year weird
                client@example.com
                """));
    }

    @Test
    void malformedPeriodShowsMatcherStyleDetails() {
        mockKeyTypePatterns();
        ProductInfo zis2 = product("Zis", 3, 2);
        when(productRegistry.getAllProducts()).thenReturn(List.of(zis2));

        assertThrows(OrderParseException.class, () -> orderTextParser.tryParse("""
                Test
                Zis 2\\1abc
                test@gmail.com
                """));
    }

    @Test
    void periodUnitDefaultsToYearsAndPcSuffixIsOptional() {
        mockKeyTypePatterns();
        ProductInfo zis2 = product("Zis", 3, 2);
        when(productRegistry.getAllProducts()).thenReturn(List.of(zis2));

        OrderOperationContext context = route("""
                Test
                Zis 2\\1
                test@gmail.com
                """);

        OrderItemSpec item = context.getItemSpecs().getFirst();
        assertEquals(2, item.computers());
        assertEquals(1, item.period().amount());
        assertEquals(BusinessPeriodUnit.YEAR, item.period().unit());
    }

    @Test
    void rejectsWhiteAdminDayPeriodsThatAreNotMultipleOfThirtyAtParseTime() {
        mockKeyTypePatterns();
        ProductInfo zab = product("ZAB", 4, 2);
        when(productRegistry.getAllProducts()).thenReturn(List.of(zab));

        OrderParseException ex = assertThrows(OrderParseException.class, () -> orderTextParser.tryParse("""
                123
                ZAB 1/1d
                client@example.com
                """));
        assertTrue(ex.getMessage().contains("multiple of 30"));
    }

    @Test
    void acceptsWhiteAdminDayPeriodsThatCanBeConvertedToMonths() {
        mockKeyTypePatterns();
        ProductInfo zab = product("ZAB", 4, 2);
        when(productRegistry.getAllProducts()).thenReturn(List.of(zab));

        OrderOperationContext context = route("""
                123
                ZAB 1/30d
                client@example.com
                """);
        assertEquals(30, context.getItemSpecs().getFirst().period().amount());
        assertEquals(BusinessPeriodUnit.DAY, context.getItemSpecs().getFirst().period().unit());
    }

    @Test
    void keepsDayPeriodsForNonWhiteAdminProducts() {
        mockKeyTypePatterns();
        ProductInfo dino = product("ZTS 3.0", 1, 1);
        when(productRegistry.getAllProducts()).thenReturn(List.of(dino));

        OrderOperationContext context = route("""
                123
                ZTS 3.0 1/1d
                client@example.com
                """);
        assertEquals(1, context.getItemSpecs().getFirst().period().amount());
        assertEquals(BusinessPeriodUnit.DAY, context.getItemSpecs().getFirst().period().unit());
    }

    @Test
    void localeFlagOverridesDefaultLocale() {
        mockKeyTypePatterns();
        ProductInfo zis = product("ZIS 3.0", 6, 2);
        when(productRegistry.getAllProducts()).thenReturn(List.of(zis));

        OrderOperationContext context = route("""
                123
                ZIS 3.0 1/1year
                client@example.com
                -l en
                """);

        assertEquals("en", context.getLocaleTag());
    }

    @Test
    void skipsTextThatDoesNotMatchOrderStructure() {
        TelegramInboundEvent event = event("""
                just some text
                client@example.com
                """);

        assertFalse(router.canRoute(event));
    }

    @Test
    void skipsTextWithEmailButNoItem() {
        TelegramInboundEvent event = event("""
                ABC123
                some free text
                client@example.com
                """);

        assertFalse(router.canRoute(event));
    }

    @Test
    void throwsForUnknownProductInStructuredOrder() {
        mockKeyTypePatterns();
        ProductInfo known = product("ZIS 3.0", 6, 2);
        when(productRegistry.getAllProducts()).thenReturn(List.of(known));

        TelegramInboundEvent event = event("""
                123
                UnknownProduct 1/1year
                client@example.com
                """);

        assertThrows(OrderParseException.class, () -> orderTextParser.tryParse(event.getPayload().getMessage().getText()));
    }

    @Test
    void throwsForUnknownItemFlag() {
        mockKeyTypePatterns();
        ProductInfo zis = product("ZIS 3.0", 6, 2);
        when(productRegistry.getAllProducts()).thenReturn(List.of(zis));

        assertThrows(OrderParseException.class, () -> orderTextParser.tryParse("""
                123
                ZIS 3.0 1/1year -weird
                client@example.com
                """));
    }

    @Test
    void throwsForUnknownGlobalFlag() {
        mockKeyTypePatterns();
        ProductInfo zis = product("ZIS 3.0", 6, 2);
        when(productRegistry.getAllProducts()).thenReturn(List.of(zis));

        assertThrows(OrderParseException.class, () -> orderTextParser.tryParse("""
                123
                ZIS 3.0 1/1year
                client@example.com
                -weird
                """));
    }

    @Test
    void matchesProductsThroughProductInfoRegex() {
        mockKeyTypePatterns();
        ProductInfo product = product("ZIS\\s*3\\.0", 6, 2);
        when(productRegistry.getAllProducts()).thenReturn(List.of(product));

        OrderOperationContext context = route("""
                123
                ZIS 3.0 1/1year
                client@example.com
                """);

        assertEquals(6, context.getItemSpecs().getFirst().product().productId());
    }

    private OrderOperationContext route(String text) {
        TelegramInboundEvent event = event(text);
        assertTrue(router.canRoute(event));
        RoutingDecision decision = router.route(event);
        RoutingDecision.PreviewDecision preview = assertInstanceOf(RoutingDecision.PreviewDecision.class, decision);
        return preview.context();
    }

    private TelegramInboundEvent event(String text) {
        Chat chat = new Chat();
        chat.setId(100L);
        Message message = new Message();
        message.setText(text);
        message.setChat(chat);
        message.setEntities(new ArrayList<MessageEntity>());
        Update update = new Update();
        update.setMessage(message);
        return new TelegramInboundEvent(new SourceEntity(1L, SourceType.TELEGRAM, "test"), update);
    }

    private ProductInfo product(String regex, int productId, int brandId) {
        return new ProductInfo(
                productId,
                brandId,
                null,
                1,
                Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS),
                Map.of("en_short", regex),
                Map.of(),
                List.of(KeyType.ONLINE)
        );
    }

    @Test
    void commandSearchShouldReturnSearchDecisionWithSelectorPayload() {
        RegexOrderTelegramRouter cmdRouter = new RegexOrderTelegramRouter(orderTextParser, List.of(
                command("s", OperationType.LICENSE_SEARCH, "search", "find")
        ));
        TelegramInboundEvent event = event("/s kof b6157cfee -p zis");
        RoutingDecision decision = cmdRouter.route(event);
        RoutingDecision.SearchDecision search = assertInstanceOf(RoutingDecision.SearchDecision.class, decision);
        assertEquals(OperationType.LICENSE_SEARCH, search.intent().operationType());
        assertEquals("kof b6157cfee -p zis", search.intent().payload());
    }

    @Test
    void commandBlockShouldDecoratePayloadWithBlockedStatus() {
        RegexOrderTelegramRouter cmdRouter = new RegexOrderTelegramRouter(orderTextParser, List.of(
                command("block", OperationType.MODIFY_STATUS)
        ));
        TelegramInboundEvent event = event("/block woid 96441");
        RoutingDecision decision = cmdRouter.route(event);
        RoutingDecision.SearchDecision search = assertInstanceOf(RoutingDecision.SearchDecision.class, decision);
        assertEquals(OperationType.MODIFY_STATUS, search.intent().operationType());
        assertEquals("status=blocked woid 96441", search.intent().payload());
    }

    @Test
    void commandAllowShouldDecoratePayloadWithAllowStatus() {
        RegexOrderTelegramRouter cmdRouter = new RegexOrderTelegramRouter(orderTextParser, List.of(
                command("allow", OperationType.MODIFY_STATUS)
        ));
        TelegramInboundEvent event = event("/allow kid 10");
        RoutingDecision decision = cmdRouter.route(event);
        RoutingDecision.SearchDecision search = assertInstanceOf(RoutingDecision.SearchDecision.class, decision);
        assertEquals(OperationType.MODIFY_STATUS, search.intent().operationType());
        assertEquals("status=allow kid 10", search.intent().payload());
    }

    @Test
    void commandResendWithExplicitIdShouldReturnSearchDecisionInCurrentFlow() {
        RegexOrderTelegramRouter cmdRouter = new RegexOrderTelegramRouter(orderTextParser, List.of(
                command("resend", OperationType.RESEND_NOTIFICATION)
        ));
        TelegramInboundEvent event = event("/resend 95470");
        RoutingDecision decision = cmdRouter.route(event);
        RoutingDecision.SearchDecision search = assertInstanceOf(RoutingDecision.SearchDecision.class, decision);
        assertEquals(OperationType.RESEND_NOTIFICATION, search.intent().operationType());
        assertEquals("95470", search.intent().payload());
    }

    private TelegramCommand command(String name, OperationType op, String... aliases) {
        return new TelegramCommand() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String[] getAliases() {
                return aliases;
            }

            @Override
            public OperationType getTargetOperationType() {
                return op;
            }
        };
    }
}
