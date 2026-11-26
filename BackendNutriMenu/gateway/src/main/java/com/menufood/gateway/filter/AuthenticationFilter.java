package com.menufood.gateway.filter;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Component
@Order(-100)
public class AuthenticationFilter implements GlobalFilter {
    private final ReactiveJwtDecoder decoder;
    private final ObjectMapper json;

    public AuthenticationFilter(ReactiveJwtDecoder decoder, ObjectMapper json) {
        this.decoder = decoder;
        this.json = json;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        String path = request.getURI().getRawPath();
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        boolean admin = route != null && route.getId().startsWith("admin-");
        if (!admin && isPublic(request.getMethod(), path)) return chain.filter(exchange);

        String token = accessToken(request, path);
        if (token == null || token.isBlank()) return reject(exchange, HttpStatus.UNAUTHORIZED);

        return Mono.defer(() -> decoder.decode(token))
                .timeout(Duration.ofSeconds(5))
                .map(jwt -> admin && !(jwt.getClaims().get("roles") instanceof List<?> roles && roles.contains("ADMIN"))
                        ? HttpStatus.FORBIDDEN : HttpStatus.OK)
                .onErrorReturn(AuthenticationFilter::keyServiceUnavailable, HttpStatus.SERVICE_UNAVAILABLE)
                .onErrorReturn(JwtException.class, HttpStatus.UNAUTHORIZED)
                .flatMap(status -> {
                    if (status != HttpStatus.OK) return reject(exchange, status);
                    var authenticated = request.mutate().headers(h -> h.setBearerAuth(token)).build();
                    return chain.filter(exchange.mutate().request(authenticated).build());
                });
    }

    private static boolean isPublic(HttpMethod method, String path) {
        return (method == HttpMethod.GET && (path.equals("/api/auth/csrf") || path.equals("/api/auth/jwks")))
                || (method == HttpMethod.POST && (path.equals("/api/auth/login") || path.equals("/api/auth/register")));
    }

    private static String accessToken(ServerHttpRequest request, String path) {
        List<String> headers = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (headers != null) {
            if (headers.size() != 1) return null;
            String header = headers.getFirst();
            return header.regionMatches(true, 0, "Bearer ", 0, 7) ? header.substring(7) : null;
        }
        if (request.getMethod() == HttpMethod.GET && path.startsWith("/api/chatbot/")) {
            var cookies = request.getCookies().get("nutriguide_access");
            if (cookies != null && cookies.size() == 1) return cookies.getFirst().getValue();
        }
        return null;
    }

    private static boolean keyServiceUnavailable(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof WebClientException || cause instanceof TimeoutException) return true;
        }
        return false;
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setCacheControl("no-store");
        String message = "Dịch vụ xác thực tạm thời chưa sẵn sàng.";
        if (status == HttpStatus.UNAUTHORIZED) {
            response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            message = "Vui lòng đăng nhập lại. JWT bị thiếu, không hợp lệ hoặc đã hết hạn.";
        }
        if (status == HttpStatus.FORBIDDEN) message = "C?n quy?n qu?n tr?.";
        byte[] body = json.writeValueAsBytes(Map.of("timestamp", Instant.now(), "status", status.value(),
                "message", message, "path", exchange.getRequest().getURI().getRawPath(), "errors", Map.of()));
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
