package com.zillya.timonfech.zillwrapper.apis;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Slf4j
public class AbstractWhiteAdminClient {

    public final CloseableHttpClient httpClient;
    public final String BASE_URL;
    private final ReentrantLock loginLock = new ReentrantLock();
    private final String LOGIN_ENDPOINT;

    public AbstractWhiteAdminClient(
            CloseableHttpClient whiteAdminHttpClient,
            @Value("${whiteAdminPanel.target}") String baseUrl,
            @Value("${whiteAdminPanel.auth.loginPage}") String loginEndpoint) {

        this.httpClient = Objects.requireNonNull(whiteAdminHttpClient, "whiteAdminHttpClient should not be null");
        this.BASE_URL = Objects.requireNonNull(baseUrl, "baseUrl");
        LOGIN_ENDPOINT = loginEndpoint;
    }


    public String executeGetWithRetry(String endpoint, List<NameValuePair> params, boolean ajax) throws IOException {
        try {
            URI requestUri = buildRequestUri(endpoint, params);
            log.debug("WhiteAdmin GET request uri={} ajax={}", requestUri, ajax);
            HttpGet req = newRequest(requestUri, ajax);

            int firstStatus = -1;
            try (CloseableHttpResponse resp = httpClient.execute(req)) {
                firstStatus = resp.getStatusLine() != null ? resp.getStatusLine().getStatusCode() : -1;
                log.debug("WhiteAdmin request first attempt endpoint={} status={}", endpoint, firstStatus);
                if (isSuccess(resp)) {
                    return entityToString(resp);
                }
            } catch (RuntimeException re) {
                if (re.getCause() instanceof URISyntaxException) {
                    throw new IOException(re.getCause());
                }
                throw re;
            }

            loginOnceIfNeeded();

            req = newRequest(requestUri, ajax);
            int secondStatus = -1;
            try (CloseableHttpResponse resp2 = httpClient.execute(req)) {
                secondStatus = resp2.getStatusLine() != null ? resp2.getStatusLine().getStatusCode() : -1;
                log.debug("WhiteAdmin request second attempt endpoint={} status={}", endpoint, secondStatus);
                if (isSuccess(resp2)) {
                    return entityToString(resp2);
                }
            }

        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        throw new IOException("Request failed after retry: " + endpoint);
    }

    private URI buildRequestUri(String endpoint, List<NameValuePair> params) throws URISyntaxException {
        URIBuilder ub = new URIBuilder(resolveEndpoint(endpoint));
        if (params != null && !params.isEmpty()) {
            ub.addParameters(params);
        }
        return ub.build();
    }

    private HttpGet newRequest(URI uri, boolean ajax) {
        HttpGet req = new HttpGet(uri);
        if (ajax) {
            req.setHeader("X-Requested-With", "XMLHttpRequest");
        }
        return req;
    }

    private String resolveEndpoint(String endpoint) {
        String ep = endpoint == null ? "" : endpoint.trim();
        if (ep.startsWith("http://") || ep.startsWith("https://")) {
            return ep;
        }
        URI base = URI.create(BASE_URL);
        return base.resolve(ep).toString();
    }

    public Document loadDocument(String endpoint, List<NameValuePair> params, boolean ajax) throws IOException {
        String html = executeGetWithRetry(endpoint, params, ajax);
        return Jsoup.parse(html);
    }

    public String executePostForm(String endpoint, List<NameValuePair> params, boolean ajax) throws IOException {
        URI requestUri;
        try {
            requestUri = new URIBuilder(resolveEndpoint(endpoint)).build();
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        String body = buildFormBody(params);
        PostAttemptResult first = executePostAttempt(endpoint, requestUri, body, ajax);
        if (first.successful() && !first.loginPage()) {
            return first.responseBody();
        }

        log.info("WhiteAdmin POST retry after login endpoint={} status={} loginPage={}",
                endpoint,
                first.status(),
                first.loginPage());
        loginOnceIfNeeded();

        PostAttemptResult second = executePostAttempt(endpoint, requestUri, body, ajax);
        if (second.successful() && !second.loginPage()) {
            return second.responseBody();
        }
        throw new IOException("POST failed after retry status=" + second.status() + " endpoint=" + endpoint);
    }

    private PostAttemptResult executePostAttempt(String endpoint, URI requestUri, String body, boolean ajax) throws IOException {
        HttpPost req = new HttpPost(requestUri);
        if (ajax) {
            req.setHeader("X-Requested-With", "XMLHttpRequest");
        }
        req.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        req.setEntity(new StringEntity(body, ContentType.APPLICATION_FORM_URLENCODED));
        try (CloseableHttpResponse resp = httpClient.execute(req)) {
            int status = resp.getStatusLine() != null ? resp.getStatusLine().getStatusCode() : -1;
            String location = resp.getFirstHeader("Location") != null ? resp.getFirstHeader("Location").getValue() : null;
            String responseBody = entityToString(resp);
            boolean success = status == 200 || status == 302;
            boolean loginPage = looksLikeLoginPage(responseBody, location);
            log.info("WhiteAdmin POST endpoint={} status={} location={} ajax={} loginPage={}",
                    endpoint,
                    status,
                    location,
                    ajax,
                    loginPage);
            return new PostAttemptResult(status, success, loginPage, responseBody);
        }
    }

    private String buildFormBody(List<NameValuePair> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (NameValuePair pair : params) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(URLEncoder.encode(pair.getName(), StandardCharsets.UTF_8));
            sb.append("=");
            sb.append(URLEncoder.encode(pair.getValue() == null ? "" : pair.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private boolean looksLikeLoginPage(String html, String location) {
        if (location != null && !location.isBlank()) {
            String lowLoc = location.toLowerCase(Locale.ROOT);
            if (lowLoc.contains("login")) {
                return true;
            }
        }
        if (html == null || html.isBlank()) {
            return false;
        }
        String low = html.toLowerCase(Locale.ROOT);
        return low.contains("name=\"login\"")
                || low.contains("name='login'")
                || low.contains("name=\"password\"")
                || low.contains("name='password'")
                || low.contains("admin login");
    }

    private record PostAttemptResult(int status, boolean successful, boolean loginPage, String responseBody) {
    }

    public Elements getTable(Document doc, String tableXpath) {
        Element table = doc.selectXpath(tableXpath).first();
        return table.select("tbody")
                .select("tr");
    }

    public List<Map<String, Element>> parseToRows(Document doc, String tablePath) {
        Elements tableRows = this.getTable(doc, tablePath);
        if (tableRows.isEmpty())
            return new ArrayList<>();

        List<String> headers = tableRows.first().select("th").stream()
                .map(Element::text)
                .toList();

        List<Map<String, Element>> resultList = new ArrayList<>();

        for (int i = 1; i < tableRows.size(); i++) {
            Elements cells = tableRows.get(i).select("td");
            Map<String, Element> rowMap = new LinkedHashMap<>();

            for (int ii = 0; ii < headers.size() && ii < cells.size(); ii++) {
                rowMap.put(headers.get(ii), cells.get(ii));
            }
            resultList.add(rowMap);
        }
        return resultList;
    }

    protected void loginOnceIfNeeded() throws IOException {
        if (loginLock.tryLock()) {
            try {
                performLogin();
            } finally {
                loginLock.unlock();
            }
        } else {
            loginLock.lock();
            loginLock.unlock();
        }
    }

    public void relogin() throws IOException {
        loginOnceIfNeeded();
    }

    protected void performLogin() throws IOException {
        String loginUrl = resolveEndpoint(LOGIN_ENDPOINT);
        log.info("Performing login to White Admin Panel endpoint={}", loginUrl);
        HttpGet get = new HttpGet(loginUrl);
        try (CloseableHttpResponse resp = httpClient.execute(get)) {
            EntityUtils.consumeQuietly(resp.getEntity());
            int status = resp.getStatusLine().getStatusCode();
            if (status != 200) {
                throw new IOException("Login failed with status: " + status);
            }
            log.info("Login successful status={}", status);
        }
    }

    private String entityToString(CloseableHttpResponse resp) throws IOException {
        if (resp.getEntity() == null)
            return "";
        return EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
    }

    protected boolean isSuccess(CloseableHttpResponse resp) {
        return resp != null && resp.getStatusLine() != null && resp.getStatusLine().getStatusCode() == 200;
    }

}
