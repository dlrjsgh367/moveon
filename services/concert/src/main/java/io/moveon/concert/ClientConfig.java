package io.moveon.concert;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ClientConfig {

    // payment 서비스로 동기 호출할 클라이언트(게이트웨이 안 거치고 내부 DNS 로 직접).
    @Bean
    public RestClient paymentRestClient(@Value("${payment.url}") String url) {
        return RestClient.builder().baseUrl(url).build();
    }
}
