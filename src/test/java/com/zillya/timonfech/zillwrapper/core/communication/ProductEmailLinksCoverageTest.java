package com.zillya.timonfech.zillwrapper.core.communication;

import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "users.sync.enabled=false",
        "telegram.bot.zill.bot-token=test-token",
        "telegram.bot.zill.bot-name=test-bot"
})
class ProductEmailLinksCoverageTest {

    @Autowired
    private ProductRegistry productRegistry;

    @Test
    void emailTemplateShouldUseUkOnlineLinkKey() throws IOException {
        String template = Files.readString(Path.of("src/main/resources/templates/license_email.html"));
        assertTrue(template.contains("uk_online_direct_link"),
                "Template must use uk_online_direct_link for product download buttons");
        assertFalse(template.contains("ua_online_direct_link"),
                "Template must not reference deprecated ua_online_direct_link key");
    }

    @Test
    void allProductsShouldExposeAtLeastOneDownloadLinkForEmail() {
        List<ProductInfo> products = productRegistry.getAllProducts();
        assertFalse(products.isEmpty(), "Products list must not be empty");

        for (ProductInfo product : products) {
            Map<String, String> online = product.getProperties(Locale.ENGLISH, KeyType.ONLINE);
            Map<String, String> offline = product.getProperties(Locale.ENGLISH, KeyType.OFFLINE);

            assertTrue(hasAnyDownloadLink(online),
                    "ONLINE email links are missing for product " + product.brandId() + "/" + product.productId());
            assertTrue(hasAnyDownloadLink(offline),
                    "OFFLINE email links are missing for product " + product.brandId() + "/" + product.productId());
        }
    }

    private boolean hasAnyDownloadLink(Map<String, String> props) {
        if (props == null || props.isEmpty()) {
            return false;
        }
        return hasText(props.get("direct_link"))
                || hasText(props.get("uk_online_direct_link"))
                || hasText(props.get("ru_online_direct_link"))
                || hasText(props.get("en_online_direct_link"))
                || hasText(props.get("offline_direct_link"))
                || hasText(props.get("google_play_link"));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

