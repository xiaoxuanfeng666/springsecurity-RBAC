package com.example.rbac.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具类
 * 支持 RS256 (非对称: 私钥签, 公钥验) 和 HS256 (对称: 同一密钥)
 */
public class JwtUtils {
    private static final String myPrivateKey = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCCyI+3xQp0Jm9B4wyyQwyh/+wuLtqWfw1c5m8OoLS27qZgGXUY2rdQosuXamXqYpQWuzmjYCqPOXNBEub0ZDdzQxSGdZ626/WxRBznfS4UeOmVmvoojNO46x6KZwnRk1GbWKxRXCv+WmwbXD23nYI5t6xP/kvpyrJnP2mNO3QCtHrgkWM4Q/3/1Y1QkXVb08bU1DPxMvIDlMlu0mWgVm/yG2RKfigxst+m0YsOr4t75QvPkyB7TAzbMoHQeHXCXM6sHi2/46xD/HII8fN5ilUkuMMl+Rus3H6Sz5c4cObZoh0haWSkvhzBQjNavJ2yqJHsbroOGOQmp8478yJuXA5vAgMBAAECggEAfwgimMSab9F5Vc0dYvU/iwdTuFQoW/AS4b6t9s5gWiOalVOE6txfHMLT8EIXtj+ESkQXKzrYe/+4lGewz4ecWKe6gxQyUskE/+cCEFQF8zqy4G8FN2f6qu4t3w9QeTGYHcVWszxNOyVHRpglFnm2qStgAt8li3QoL6Nnehhaidig4k5FhFYHR5IUY8eXXgnuRyVOHYfberrT1Dh3/e+jkfL9ugFdLtaFIToxbDKoIEZx+hdggsROql5PoAfdmSA6vcdUctywuIsYk3QEvSHdDoTmXEd9kJ7kaqdI72HLqrmpWg3QtxGpIrA3YBeouPCAOMD1WwHlcTeObPT9IsdrGQKBgQD7L2EvAbLUSCOktXEpKIMDkuFdBycvo58SdpDGPMGBv7ARzNmoNBQ2RyAxXYpSdeGqMgMFyZApXL7l9wax403Fk74QwRcHrAECuF1K90Ltkf1jMrhE1xmAqxkTYTQrN/OOSiP2Ws7MH43fL6Y7nmv8f+GadBdLHZ1NoSw8iB6xHQKBgQCFSlgw449b3CH/XEgLzQB/0ePC++0Fwvs1vrgCG8Mn+vOjWifxlCK4tHlOsgq5Mt4IOexpnuXQzI7sVuMAYTBXskm8hojG4nAclc8w8IHcci15i/K+yzDy76gel2qQ/jNNE90AA6cUOf286p0fOfMrajS6sWL0KcrlF9VhGLhT+wKBgQDZhq9/2NW4dvy/IA5umVHUX3fha1dpLyaMA7fepTDjH1DoKWH5A5jZZIEhnyMxxDp4fLUXZvX8jAAaAPKATRvF1euMcNtz3mZFp8nLKRSBzqMELCFeJHVGc2XU3aIjJ2I5wEUijIN4TH89X/NywAA+e7SxYIXnnC7k0oYPqcYClQKBgHASUpTIwYE7Vlqf/PPvKrxOfnMz39c4WoCWG6GqNi0nlDOFWpCFPayQEm9YxCtz2byQ93bXY+RsGsAdTkDDdyJFhjo95ENVoW5niA5eKtJwLkxEn5ZMwpuBZppSNggztG7ahLe+Wn1nRTHTEslusEeQxUU0YT9q5G472+4IGvSBAoGAOH2zI5aDXtvA/X9CML+LWggCOxd2q7V5bQoZySZuPF91B+ut2RuqPThGRtPgCoKJmv/tdRhEML+OwEgrM5b9ZgkisP1+eoWSFRte8bGKAmmQWIRKBkz2GMVbN8DtXJ2O7dNwffl5Vn5l6Kc1SYBbU7phgjCfajBI1zp/uKPk7TI=";
    private static final String myPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAgsiPt8UKdCZvQeMMskMMof/sLi7aln8NXOZvDqC0tu6mYBl1GNq3UKLLl2pl6mKUFrs5o2AqjzlzQRLm9GQ3c0MUhnWetuv1sUQc530uFHjplZr6KIzTuOseimcJ0ZNRm1isUVwr/lpsG1w9t52CObesT/5L6cqyZz9pjTt0ArR64JFjOEP9/9WNUJF1W9PG1NQz8TLyA5TJbtJloFZv8htkSn4oMbLfptGLDq+Le+ULz5Mge0wM2zKB0Hh1wlzOrB4tv+OsQ/xyCPHzeYpVJLjDJfkbrNx+ks+XOHDm2aIdIWlkpL4cwUIzWrydsqiR7G66DhjkJqfOO/MiblwObwIDAQAB";


    // ================= 配置项 =================
    // 实际项目中建议从配置文件读取
    private static final String RSA_PRIVATE_KEY_PEM = "-----BEGIN PRIVATE KEY-----\n" +
            myPrivateKey + "\n" +
            "-----END PRIVATE KEY-----";

    private static final String RSA_PUBLIC_KEY_PEM = "-----BEGIN PUBLIC KEY-----\n" +
            myPublicKey + "\n" +
            "-----END PUBLIC KEY-----";

    private static final String HS256_SECRET = "your-very-long-secret-key-for-hs256-must-be-at-least-32-bytes!";

    // Token 过期时间 (例如: 2小时)
    private static final long EXPIRATION_TIME_MS = 2 * 60 * 60 * 1000;

    // ================= 核心方法 =================

    /**
     * 生成 JWT Token (使用 RS256 非对称加密)
     *
     * @param userId    用户ID
     * @param username  用户名
     * @param sessionId 会话ID
     * @param type      Token类型
     * @return JWT 字符串
     */
    public static String generateTokenWithRSA(Long userId, String username, String sessionId, String type) {
        try {
            PrivateKey privateKey = getPrivateKeyFromPem(RSA_PRIVATE_KEY_PEM);

            Date now = new Date();
            Date expiry = new Date(now.getTime() + EXPIRATION_TIME_MS);

            return Jwts.builder()
                    .setSubject(String.valueOf(userId))
                    .claim("username", username)
                    .claim("sessionId", sessionId)
                    .claim("tokenType", type)
                    .setIssuedAt(now)
                    .setExpiration(expiry)
                    // 关键：使用私钥签名，算法为 RS256
                    .signWith(privateKey, SignatureAlgorithm.RS256)
                    .compact();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    /**
     * 验证并解析 JWT Token (使用 RS256 非对称加密)
     *
     * @param token JWT 字符串
     * @return Claims (包含用户信息)
     */
    public static Claims verifyTokenWithRSA(String token) {
        try {
            PublicKey publicKey = getPublicKeyFromPem(RSA_PUBLIC_KEY_PEM);

            return Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            throw new RuntimeException("Token has expired", e);
        } catch (JwtException e) {
            throw new RuntimeException("Invalid JWT token", e);
        } catch (Exception e) {
            throw new RuntimeException("Invalid JWT token", e);
        }
    }


    // ================= 辅助方法：密钥处理 =================

    /**
     * 从 PEM 格式字符串加载 RSA 私钥
     */
    private static PrivateKey getPrivateKeyFromPem(String pem) throws Exception {
        // 去除头尾标识和换行符
        String privateKeyPEM = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] decoded = Base64.getDecoder().decode(privateKeyPEM);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    /**
     * 从 PEM 格式字符串加载 RSA 公钥
     */
    private static PublicKey getPublicKeyFromPem(String pem) throws Exception {
        // 去除头尾标识和换行符
        String publicKeyPEM = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] decoded = Base64.getDecoder().decode(publicKeyPEM);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    public static void main(String[] args) throws Exception {
// 假设用户登录成功
        Long userId = 1001L;
        String username = "zhangsan";
        String sessionId = UUID.randomUUID().toString();
// 生成 Token
        String token = JwtUtils.generateTokenWithRSA(userId, username, sessionId, "ACCESS");

// 返回给前端
        System.out.println("Generated Token: " + token);
        Claims claims = verifyTokenWithRSA(token);
        System.out.println("Generated Token: " + claims);


    }
}