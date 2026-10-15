package com.example.rbac.oauth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 客户端密钥的可逆加密（AES/GCM）
 * <p>
 * 客户端密钥需要能在「客户端管理」页面上展示明文，因此不能像用户密码那样用单向哈希
 * （BCrypt 无法反推原值）。这里改为对称加密：库中存密文，展示时解密成明文。
 * 相比直接存明文，密钥泄露的风险从「拖库即得」降到「拖库 + 拿到应用配置密钥才得」。
 * <p>
 * 由配置项 {@code oauth2.client-secret-key} 派生密钥（SHA-256 取前 16 字节 → AES-128，
 * 不用 AES-256 是为了不受 JDK 加密强度策略文件限制）。
 * <p>
 * <b>该配置项一旦变更，库中已有的客户端密钥将无法解密</b>，对应客户端会认证失败，
 * 需要在「客户端管理」页面重新设置密钥。生产环境请把它换成随机值并妥善保存。
 */
@Slf4j
@Component
public class ClientSecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    /** GCM 推荐 12 字节 IV；标签 128 位 */
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public ClientSecretCipher(@Value("${oauth2.client-secret-key}") String configuredKey) {
        this.key = deriveKey(configuredKey);
    }

    /**
     * 明文 → Base64(IV || 密文)。每次调用 IV 随机，因此同一明文每次得到的密文不同
     */
    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("客户端密钥加密失败", e);
        }
    }

    /**
     * 密文 → 明文；密文非法密钥不符则返回 null（调用方据此判定认证失败）
     */
    public String decrypt(String encrypted) {
        if (!StringUtils.hasText(encrypted)) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            if (combined.length <= IV_LENGTH) {
                return null;
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            byte[] cipherText = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 例如密钥变更后遗留的旧密文，或历史明文/BCrypt 数据
            log.warn("客户端密钥解密失败（可能是密钥已变更或为历史数据），该客户端将无法通过认证: {}", e.getMessage());
            return null;
        }
    }

    private static SecretKey deriveKey(String configuredKey) {
        if (!StringUtils.hasText(configuredKey)) {
            throw new IllegalStateException("oauth2.client-secret-key 未配置，无法加解密客户端密钥");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(configuredKey.getBytes(StandardCharsets.UTF_8));
            byte[] aes128 = new byte[16];
            System.arraycopy(digest, 0, aes128, 0, 16);
            return new SecretKeySpec(aes128, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("派生客户端密钥加密密钥失败", e);
        }
    }

    /**
     * 校验明文密钥是否与库中密文匹配（解密后常量时间比较）
     * <p>
     * 授权端点与令牌端点都要做这件事，逻辑集中在此，避免各处重复实现比较。
     */
    public boolean matches(String rawSecret, String encryptedSecret) {
        if (!StringUtils.hasText(rawSecret)) {
            return false;
        }
        String plain = decrypt(encryptedSecret);
        return plain != null && MessageDigest.isEqual(
                plain.getBytes(StandardCharsets.UTF_8),
                rawSecret.getBytes(StandardCharsets.UTF_8));
    }
}
