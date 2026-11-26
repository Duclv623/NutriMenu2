package com.example.mealoptimizerservice.client;

import com.example.mealoptimizerservice.exception.OptimizerBusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

@Component
public class MealAccountClient {
    private final RestClient users;
    public record MealAccount(long serviceUserId, Profile profile, Map<String, PlanMetadata> previousPlans) {}
    public record Profile(int age, String gender, BigDecimal height, BigDecimal weight, String activity, String goal,
                          Set<String> allergies, Set<String> excludedFoods, boolean confirmed, boolean pregnant,
                          boolean specialDiet, long version) {}
    public record PlanMetadata(long profileVersion, String createdAt) {}

    public MealAccountClient(@Value("${app.services.user-url}") String url) {
        var http = new SimpleClientHttpRequestFactory();
        http.setConnectTimeout(3000);
        http.setReadTimeout(5000);
        users = RestClient.builder().baseUrl(url).requestFactory(http).build();
    }

    public MealAccount account(String authorization) {
        if (!authorization.startsWith("Bearer ")) throw new OptimizerBusinessException(HttpStatus.UNAUTHORIZED, "Cần đăng nhập");
        try {
            var result = users.get().uri("/api/internal/meal-account").header("Authorization", authorization)
                    .retrieve().body(MealAccount.class);
            if (result == null || result.serviceUserId() <= 0) throw new OptimizerBusinessException(HttpStatus.BAD_GATEWAY, "Thiếu thông tin tài khoản");
            return result;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403)
                throw new OptimizerBusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
            throw new OptimizerBusinessException(HttpStatus.BAD_GATEWAY, "User Service trả về lỗi");
        } catch (RestClientException e) {
            throw new OptimizerBusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Không kết nối được User Service");
        }
    }
}
