package com.zillya.timonfech.zillwrapper.apis;

import org.apache.http.Header;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import java.util.ArrayList;
import java.util.List;
@Configuration
public class WhiteAdminHttpClientConfig {
    private static final Logger log = LoggerFactory.getLogger(WhiteAdminHttpClientConfig.class);

    @Value("${whiteAdminPanel.target}")
    private String target;

    @Value("${whiteAdminPanel.username:}")
    private String username;

    @Value("${whiteAdminPanel.password:}")
    private String password;

    @Value("${whiteAdminPanel.ssl.insecure:false}")
    private boolean insecureTls;

    private final CookieStore cookieStore = new BasicCookieStore();

    @Bean
    public CookieStore whiteAdminCookieStore() {
        return cookieStore;
    }

    @Bean
    public CloseableHttpClient whiteAdminHttpClient() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(30_000)
                .setConnectionRequestTimeout(30_000)
                .setSocketTimeout(30_000)
                .build();

        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        boolean usernamePresent = username != null && !username.isBlank();
        boolean passwordPresent = password != null && !password.isBlank();
        if (usernamePresent && passwordPresent) {
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
        }

        List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader("Accept", "*/*"));
        headers.add(new BasicHeader("Connection", "keep-alive"));

        log.info("WhiteAdmin HTTP config: target={}, usernamePresent={}, passwordPresent={}, insecureTls={}",
                target,
                usernamePresent,
                passwordPresent,
                insecureTls);

        var builder = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setDefaultHeaders(headers)
                .setDefaultCookieStore(cookieStore)
                .setDefaultCredentialsProvider(credentialsProvider)
                .evictExpiredConnections()
                .useSystemProperties();

        if (insecureTls) {
            try {
                SSLContext sslContext = SSLContextBuilder.create()
                        .loadTrustMaterial((TrustStrategy) (chain, authType) -> true)
                        .build();
                SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                        sslContext,
                        NoopHostnameVerifier.INSTANCE
                );
                builder.setSSLSocketFactory(sslSocketFactory);
                log.warn("WhiteAdmin HTTP client is running with INSECURE TLS (trust-all). Use only for local debugging.");
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize insecure TLS mode for WhiteAdmin client", e);
            }
        }

        return builder.build();
    }
}
