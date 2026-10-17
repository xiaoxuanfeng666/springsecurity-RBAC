package com.example.audit.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/**
 * 统一 LocalDateTime 的 JSON 格式为 {@code yyyy-MM-dd HH:mm:ss}
 * <p>
 * 不能只靠 {@code spring.jackson.date-format} —— 那个配置只作用于 {@code java.util.Date}，
 * 对 {@code LocalDateTime} 无效：Jackson 默认按 ISO-8601（{@code 2026-11-01T00:00:00}）处理，
 * 前端日期控件与查询参数用的都是 {@code yyyy-MM-dd HH:mm:ss}，两边对不上就会报
 * "could not be parsed at index 10"。这里把序列化与反序列化都固定成同一种格式。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeCustomizer() {
        return builder -> {
            builder.serializers(new LocalDateTimeSerializer(DATE_TIME_FORMATTER));
            builder.deserializers(new LocalDateTimeDeserializer(DATE_TIME_FORMATTER));
        };
    }
}
