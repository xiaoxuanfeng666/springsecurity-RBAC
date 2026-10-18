package com.example.oauth.oauth;

import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.oauth2.common.exceptions.InvalidGrantException;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.code.AuthorizationCodeServices;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * 基于 Redis 的授权码存储
 * <p>
 * 替换库默认的 {@code InMemoryAuthorizationCodeServices}：
 * <ul>
 *   <li><b>多节点可用</b>：授权请求与换令牌请求可能落在不同实例，内存存储会直接报 invalid_grant；</li>
 *   <li><b>重启不丢</b>：未兑换的授权码在服务重启后仍然有效；</li>
 *   <li><b>自动过期</b>：授权码本身短命，Redis 键带 TTL（默认 300 秒），不会无限堆积。</li>
 * </ul>
 * <p>
 * 值存的是 {@link OAuth2Authentication} 的 JDK 序列化结果（包含用户、客户端、scope 与授权请求），
 * 换令牌时原样取回，行为与内存实现一致。键为 {@code oauth2:code:{code}}。
 * <p>
 * <b>一次性消费</b>：用 Lua 脚本在 Redis 内原子地 GET + DEL（没有用 {@code GETDEL}：
 * 该命令要求 Redis 6.2+，Lua 在更老的版本上也能达到同样效果），
 * 并发拿同一个 code 只会有一次成功，避免「授权码被重放两次」。
 * <p>
 * 取舍：JDK 序列化要求相关类（{@code OAuth2Authentication}、{@code LoginUser}、{@code SysUser} 等）
 * 保持 {@link java.io.Serializable}；升级类结构后，未消费的旧 code 可能反序列化失败，此时按无效授权码处理即可
 * —— 授权码本来就只有几分钟寿命。
 */
public class RedisAuthorizationCodeServices implements AuthorizationCodeServices {

    private static final String KEY_PREFIX = "oauth2:code:";

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int CODE_BYTES = 32;

    /**
     * 原子 GET + DEL。返回原始字节，由调用方用值序列化器反序列化。
     * 不用 {@code GETDEL}：本地/部分生产 Redis 仍低于 6.2。
     */
    private static final byte[] GET_AND_DELETE_SCRIPT = (
            "local v = redis.call('GET', KEYS[1]); "
                    + "if v then redis.call('DEL', KEYS[1]) end; "
                    + "return v"
    ).getBytes(StandardCharsets.UTF_8);

    private final RedisTemplate<String, OAuth2Authentication> redisTemplate;

    private final Duration ttl;

    public RedisAuthorizationCodeServices(RedisTemplate<String, OAuth2Authentication> redisTemplate,
                                          Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    @Override
    public String createAuthorizationCode(OAuth2Authentication authentication) {
        byte[] bytes = new byte[CODE_BYTES];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redisTemplate.opsForValue().set(KEY_PREFIX + code, authentication, ttl);
        return code;
    }

    @Override
    public OAuth2Authentication consumeAuthorizationCode(String code) throws InvalidGrantException {
        if (code == null || code.isEmpty()) {
            throw new InvalidGrantException("Invalid authorization code");
        }
        // 原子取出并删除：并发重放同一 code 时只有一个请求能拿到值。
        // 这里直接用 RedisCallback 取原始字节：走 ScriptExecutor 的话，结果会被
        // 值序列化器反序列化成 OAuth2Authentication，再按脚本声明的 byte[] 强转，导致 ClassCastException。
        // 键序列化器固定为 StringRedisSerializer，直接用 UTF-8 字节即可。
        byte[] key = (KEY_PREFIX + code).getBytes(StandardCharsets.UTF_8);
        byte[] data = redisTemplate.execute((RedisCallback<byte[]>) connection ->
                connection.eval(GET_AND_DELETE_SCRIPT, ReturnType.VALUE, 1, key));
        if (data == null || data.length == 0) {
            throw new InvalidGrantException("Invalid authorization code: " + code);
        }
        RedisSerializer<?> valueSerializer = redisTemplate.getValueSerializer();
        Object authentication = valueSerializer.deserialize(data);
        if (!(authentication instanceof OAuth2Authentication)) {
            throw new InvalidGrantException("Invalid authorization code: " + code);
        }
        return (OAuth2Authentication) authentication;
    }
}
