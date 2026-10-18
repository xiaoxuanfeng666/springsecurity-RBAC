package com.example.oauth.identity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.oauth.config.IdpProperties;
import com.example.oauth.entity.SysUser;
import com.example.oauth.entity.SysUserIdentity;
import com.example.oauth.mapper.JitUserMapper;
import com.example.oauth.mapper.SysUserIdentityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JIT（Just-In-Time）自动建号
 * <p>
 * 外部身份未绑定时，按配置自动创建本地用户并绑定默认角色。
 * 默认关闭；启用前请确认企业通讯录可信，避免任意企业微信成员都能进入系统。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JitUserProvisioner {

    private static final int MAX_USERNAME_LENGTH = 50;

    private final IdpProperties idpProperties;
    private final JitUserMapper jitUserMapper;
    private final SysUserIdentityMapper identityMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional(rollbackFor = Exception.class)
    public SysUserIdentity provision(ExternalIdentity external) {
        SysUserIdentity existing = findIdentity(external);
        if (existing != null) {
            return existing;
        }

        String username = buildUniqueUsername(external);
        SysUser user = new SysUser();
        user.setUsername(username);
        // 随机密码：JIT 用户只能通过外部 IdP 登录
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setNickname(StringUtils.hasText(external.getDisplayName())
                ? external.getDisplayName() : external.getExternalUserId());
        user.setDeptId(idpProperties.getJit().getDefaultDeptId());
        user.setEmail(external.getEmail());
        user.setPhone(external.getMobile());
        user.setStatus(1);
        user.setRemark("JIT 自动创建（" + external.getProvider() + "）");
        jitUserMapper.insertUser(user);

        assignDefaultRoles(user.getId());

        SysUserIdentity identity = new SysUserIdentity();
        identity.setUserId(user.getId());
        identity.setProvider(external.getProvider());
        identity.setCorpId(external.getCorpId());
        identity.setExternalUserId(external.getExternalUserId());
        identity.setUnionId(external.getUnionId());
        identity.setStatus(1);
        identity.setBindTime(LocalDateTime.now());
        identity.setRemark("JIT 自动绑定");
        identity.setCreateTime(LocalDateTime.now());
        identity.setUpdateTime(LocalDateTime.now());
        identityMapper.insert(identity);

        log.info("JIT 自动建号成功: provider={}, externalUserId={}, username={}",
                external.getProvider(), external.getExternalUserId(), username);
        return identity;
    }

    private SysUserIdentity findIdentity(ExternalIdentity external) {
        return identityMapper.selectOne(new LambdaQueryWrapper<SysUserIdentity>()
                .eq(SysUserIdentity::getProvider, external.getProvider())
                .eq(SysUserIdentity::getCorpId, external.getCorpId())
                .eq(SysUserIdentity::getExternalUserId, external.getExternalUserId())
                .eq(SysUserIdentity::getStatus, 1)
                .last("limit 1"));
    }

    private void assignDefaultRoles(Long userId) {
        String keys = idpProperties.getJit().getDefaultRoleKeys();
        if (!StringUtils.hasText(keys)) {
            return;
        }
        for (String key : keys.split(",")) {
            String roleKey = key.trim();
            if (!StringUtils.hasText(roleKey)) {
                continue;
            }
            Long roleId = jitUserMapper.selectRoleIdByKey(roleKey);
            if (roleId == null) {
                log.warn("JIT 默认角色不存在，已跳过: roleKey={}", roleKey);
                continue;
            }
            jitUserMapper.insertUserRole(userId, roleId);
        }
    }

    private String buildUniqueUsername(ExternalIdentity external) {
        String raw = external.getExternalUserId() == null ? "user" : external.getExternalUserId();
        String sanitized = raw.replaceAll("[^A-Za-z0-9_]", "_");
        String base = idpProperties.getJit().getUsernamePrefix() + sanitized;
        if (base.length() > MAX_USERNAME_LENGTH) {
            base = base.substring(0, MAX_USERNAME_LENGTH);
        }
        String candidate = base;
        int suffix = 2;
        while (jitUserMapper.countByUsername(candidate) > 0) {
            candidate = base + "_" + suffix;
            if (candidate.length() > MAX_USERNAME_LENGTH) {
                candidate = candidate.substring(0, MAX_USERNAME_LENGTH - 3) + "_" + suffix;
            }
            suffix++;
            if (suffix > 100) {
                throw new IllegalStateException("无法生成唯一用户名: " + base);
            }
        }
        return candidate;
    }
}
