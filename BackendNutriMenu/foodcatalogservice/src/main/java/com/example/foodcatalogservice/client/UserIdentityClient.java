package com.example.foodcatalogservice.client;

import com.example.foodcatalogservice.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class UserIdentityClient {
    private final RestClient users;
    public record Identity(long serviceUserId) {}

    public UserIdentityClient(@Value("${app.services.user-url}") String url) {
        var http = new SimpleClientHttpRequestFactory();
        http.setConnectTimeout(3000);
        http.setReadTimeout(5000);
        users = RestClient.builder().baseUrl(url).requestFactory(http).build();
    }

    public record DietaryProfile(java.util.Set<String> allergies, java.util.Set<String> excludedFoods) {}

    public DietaryProfile profile(String token) {
        var profile = users.get().uri("/api/me/profile").header("Authorization", token)
                .retrieve().body(DietaryProfile.class);
        if (profile == null || profile.allergies() == null || profile.excludedFoods() == null)
            throw new BusinessRuleException(HttpStatus.BAD_GATEWAY, "Không lấy được hồ sơ dinh dưỡng.");
        return profile;
    }

    public Identity identity(String authorization) {
        if (!authorization.startsWith("Bearer ")) throw new BusinessRuleException(HttpStatus.UNAUTHORIZED, "Cần đăng nhập");
        try {
            var result = users.get().uri("/api/internal/identity").header("Authorization", authorization)
                    .retrieve().body(Identity.class);
            if (result == null || result.serviceUserId() <= 0) throw new BusinessRuleException(HttpStatus.BAD_GATEWAY, "Thiếu thông tin tài khoản");
            return result;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403)
                throw new BusinessRuleException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
            throw new BusinessRuleException(HttpStatus.BAD_GATEWAY, "User Service trả về lỗi");
        } catch (RestClientException e) {
            throw new BusinessRuleException(HttpStatus.SERVICE_UNAVAILABLE, "Không kết nối được User Service");
        }
    }
}
