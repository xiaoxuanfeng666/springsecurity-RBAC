package com.example.marketing;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 营销系统后端
 * <p>
 * 独立服务、独立数据库（marketing），没有自己的用户体系与权限管理：
 * 登录授权走 rbac-oauth，权限数据由 RBAC 经 RocketMQ 下发。
 * <p>
 * 排除 {@code UserDetailsServiceAutoConfiguration}：本服务没有本地用户，
 * 不排除的话 Spring Security 会生成一个随机密码的内存用户，日志里还会打出来，
 * 看着像是存在一个可用的默认账号。
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
@MapperScan("com.example.marketing.mapper")
public class MarketingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketingApplication.class, args);
    }
}
