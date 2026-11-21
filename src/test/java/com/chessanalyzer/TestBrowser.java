package com.chessanalyzer;

import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.http.HttpClient;
import java.util.Optional;

/**
 * A tiny "browser" for integration tests. Like the real React app it:
 *  - keeps cookies (the session id after login, and the XSRF-TOKEN cookie);
 *  - copies the XSRF-TOKEN cookie into an X-XSRF-TOKEN header on every non-GET request.
 * Each TestBrowser is a separate visitor, so two of them can play each other.
 */
public class TestBrowser {

    private final CookieManager cookies = new CookieManager();
    public final RestClient http;

    public TestBrowser(int port) {
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory(client))
                .requestInterceptor((request, body, execution) -> {
                    if (request.getMethod() != HttpMethod.GET) {
                        cookie("XSRF-TOKEN").ifPresent(token -> request.getHeaders().add("X-XSRF-TOKEN", token));
                    }
                    return execution.execute(request, body);
                })
                .build();
        http.get().uri("/api/auth/me").retrieve().toBodilessEntity();   // receives the XSRF-TOKEN cookie
    }

    public Optional<String> cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(name)).map(HttpCookie::getValue).findFirst();
    }
}
