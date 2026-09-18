package io.moveon.notification;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

@Configuration
public class StreamConfig {

    public static final String STREAM = "reservation-confirmed";
    public static final String GROUP = "notification";
    public static final String CONSUMER = "notification-1";

    // 소비자 그룹 생성(없으면 스트림까지 생성) + 리스너 컨테이너 기동.
    // receive(): 수동 ack 모드 -> 리스너가 발송 후 직접 XACK 한다.
    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamContainer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redis,
            ConfirmationListener listener) {

        try {
            redis.opsForStream().createGroup(STREAM, ReadOffset.from("0"), GROUP);
        } catch (Exception alreadyExists) {
            // 그룹이 이미 있으면 무시
        }

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(1))
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        container.receive(
                Consumer.from(GROUP, CONSUMER),
                StreamOffset.create(STREAM, ReadOffset.lastConsumed()),
                listener);
        container.start();
        return container;
    }
}
