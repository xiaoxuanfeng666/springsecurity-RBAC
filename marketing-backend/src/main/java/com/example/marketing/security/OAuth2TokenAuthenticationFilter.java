package com.example.marketing.security;

import com.example.marketing.entity.MarketingUser;
import com.example.marketing.mapper.MarketingMenuMapper;
import com.example.marketing.mapper.MarketingUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 令牌认证过滤器
 * <p>
 * 每个请求做两件事：
 * <ol>
 *   <li>拿 Bearer 令牌去 rbac-oauth 的 {@code /oauth/check_token} 换回 <b>user_name</b>
 *       —— 令牌里唯一可靠的身份（没有 userId，自定义声明过不了 JWT 序列化）</li>
 *   <li>用 user_name 查本地的同步用户表，组装权限点后放进 SecurityContext</li>
 * </ol>
 * 权限点每次实时查库，因此 RBAC 改了授权、MQ 一同步，本系统立刻生效，无需重新登录。
 * <p>
 * {@code check_token} <b>不做缓存</b>，每个请求都实时校验。这里曾经缓存 5 分钟
 * （「令牌在有效期内身份不会变」），但那是在统一登出还不存在时的取舍 ——
 * 现在授权服务会在 {@code check_token} 时校验 SSO 会话是否仍然存在，
 * 缓存会让「用户已在别处登出」这件事最长滞后 5 分钟才生效，等于给统一登出开了个后门。
 * 实时校验的代价可控：授权服务那边是本地验签 + 一次 Redis GET，不查库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final OAuth2TokenClient tokenClient;
    private final MarketingUserMapper userMapper;
    private final MarketingMenuMapper menuMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            LoginUser loginUser = loadLoginUser(token);
            if (loginUser != null) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 令牌 → 用户名 → 本地用户 + 权限。任一步失败返回 null，由认证入口点统一回 401。
     */
    private LoginUser loadLoginUser(String token) {
        String username = resolveUsername(token);
        if (username == null) {
            return null;
        }
        MarketingUser user = userMapper.selectByUsername(username);
        if (user == null) {
            // 用户不存在于本系统的同步表：要么还没同步（RBAC 侧刚建/RocketMQ 未跑），
            // 要么已被停用/离职（下发的只含在职用户）
            log.warn("令牌有效但本地无对应用户，拒绝访问: username={}", username);
            return null;
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            return null;
        }

        Set<String> roleKeys = splitRoleKeys(user.getRoleKeys());
        Set<String> permissions = SuperAdmin.matches(roleKeys)
                ? new HashSet<>(Collections.singletonList(SuperAdmin.WILDCARD_PERMISSION))
                : new LinkedHashSet<>(menuMapper.selectPermsByUserId(user.getId()));
        return new LoginUser(user, permissions, roleKeys);
    }

    private Set<String> splitRoleKeys(String roleKeys) {
        if (roleKeys == null || roleKeys.trim().isEmpty()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(roleKeys.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String resolveUsername(String token) {
        Map<String, Object> info = tokenClient.checkToken(token);
        if (info == null) {
            return null;
        }
        Object active = info.get("active");
        if (active != null && !Boolean.TRUE.equals(active)) {
            return null;
        }
        Object username = info.get("user_name");
        if (username == null) {
            log.warn("令牌校验结果里没有 user_name，无法识别身份: keys={}", info.keySet());
            return null;
        }
        return username.toString();
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
