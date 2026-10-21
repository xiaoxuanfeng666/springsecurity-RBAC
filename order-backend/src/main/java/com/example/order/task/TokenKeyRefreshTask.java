package com.example.order.task;

import com.example.order.security.JwtTokenVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 授权服务签名公钥的定时刷新
 * <p>
 * 授权服务轮换密钥后，本服务最多一个周期即可接受新令牌，无需重启。
 * 拉取失败时保留旧公钥，避免授权服务短暂不可用时所有请求一起 401。
 * 定时任务集中放在 {@code task} 包，验签组件本身不感知调度。
 */
@Component
@RequiredArgsConstructor
public class TokenKeyRefreshTask {

    private final JwtTokenVerifier jwtTokenVerifier;

    @Scheduled(fixedDelayString = "${oauth2.token-key-refresh-ms:60000}")
    public void refresh() {
        jwtTokenVerifier.refreshPublicKey();
    }
}
