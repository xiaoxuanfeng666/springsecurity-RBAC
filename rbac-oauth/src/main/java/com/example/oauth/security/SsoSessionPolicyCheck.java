package com.example.oauth.security;

import com.example.oauth.entity.ClientDetails;
import com.example.oauth.mapper.ClientDetailsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时的 SSO 会话策略自检
 * <p>
 * 会话寿命是全局策略（空闲超时 + 绝对上限），与各客户端的 {@code refresh_token_validity} 相互独立。
 * 这里只做一件事：如果某个客户端的刷新令牌有效期比会话空闲超时还长，打一条告警 ——
 * 那意味着该客户端的刷新令牌会先于会话失效，如果是有意为之可以忽略，否则说明配置需要调整。
 * <p>
 * 只告警、不阻断启动：两者不一致本身不一定错误，属于安全策略的取舍。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SsoSessionPolicyCheck implements ApplicationRunner {

    private final ClientDetailsMapper clientDetailsMapper;

    @Value("${rbac.sso.session-idle-ttl-seconds:604800}")
    private long idleTtlSeconds;

    @Value("${rbac.sso.session-max-ttl-seconds:2592000}")
    private long maxTtlSeconds;

    @Override
    public void run(ApplicationArguments args) {
        List<ClientDetails> clients = clientDetailsMapper.selectList(null);
        int longest = 0;
        String longestClient = "-";
        for (ClientDetails client : clients) {
            Integer validity = client.getRefreshTokenValidity();
            if (validity != null && validity > longest) {
                longest = validity;
                longestClient = client.getAppId();
            }
        }
        log.info("SSO 会话策略: idle={}s, max={}s; 客户端刷新令牌最长={}s ({})",
                idleTtlSeconds, maxTtlSeconds, longest, longestClient);
        if (longest > idleTtlSeconds) {
            log.warn("客户端 {} 的 refresh_token_validity={}s 超过会话空闲超时 {}s，"
                            + "其刷新令牌会先于 SSO 会话失效；如非有意，请调整 "
                            + "rbac.sso.session-idle-ttl-seconds 或 clientdetails 配置",
                    longestClient, longest, idleTtlSeconds);
        }
    }
}
