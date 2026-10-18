package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rbac.common.BusinessException;
import com.example.rbac.common.ResultCode;
import com.example.rbac.dto.IdentityProviderOption;
import com.example.rbac.dto.UserIdentityForm;
import com.example.rbac.entity.SysUser;
import com.example.rbac.entity.SysUserIdentity;
import com.example.rbac.identity.ExternalIdentityProviders;
import com.example.rbac.mapper.SysUserIdentityMapper;
import com.example.rbac.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 用户外部身份绑定
 * <p>
 * 支持多个身份提供方（企业微信 / 钉钉 / OIDC ...），按
 * (provider, corp_id, external_user_id) 唯一确定一条绑定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserIdentityService {

    private static final String DEFAULT_PROVIDER = "wecom";

    private final SysUserIdentityMapper identityMapper;
    private final SysUserMapper userMapper;

    /**
     * 支持绑定的提供方列表（供前端下拉）
     */
    public List<IdentityProviderOption> supportedProviders() {
        List<IdentityProviderOption> options = new ArrayList<>();
        for (Map.Entry<String, String> entry : ExternalIdentityProviders.labels().entrySet()) {
            options.add(new IdentityProviderOption(entry.getKey(), entry.getValue()));
        }
        return options;
    }

    public List<SysUserIdentity> list(Long userId) {
        requireUser(userId);
        return identityMapper.selectList(new LambdaQueryWrapper<SysUserIdentity>()
                .eq(SysUserIdentity::getUserId, userId)
                .orderByDesc(SysUserIdentity::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(Long userId, UserIdentityForm form) {
        SysUser user = requireUser(userId);
        String provider = StringUtils.hasText(form.getProvider())
                ? form.getProvider().toLowerCase(Locale.ROOT) : DEFAULT_PROVIDER;
        if (!ExternalIdentityProviders.supports(provider)) {
            throw new BusinessException("不支持的身份提供方: " + provider);
        }

        Long exists = identityMapper.selectCount(new LambdaQueryWrapper<SysUserIdentity>()
                .eq(SysUserIdentity::getProvider, provider)
                .eq(SysUserIdentity::getCorpId, form.getCorpId())
                .eq(SysUserIdentity::getExternalUserId, form.getExternalUserId()));
        if (exists != null && exists > 0) {
            throw new BusinessException("该外部账号已绑定（可能绑定在其他用户上）");
        }

        LocalDateTime now = LocalDateTime.now();
        SysUserIdentity row = new SysUserIdentity();
        row.setUserId(userId);
        row.setProvider(provider);
        row.setCorpId(form.getCorpId().trim());
        row.setExternalUserId(form.getExternalUserId().trim());
        row.setUnionId(form.getUnionId());
        row.setStatus(1);
        row.setBindTime(now);
        row.setRemark(form.getRemark());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        identityMapper.insert(row);
        log.info("已绑定外部身份: username={}, provider={}, externalUserId={}",
                user.getUsername(), provider, row.getExternalUserId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysUserIdentity exist = identityMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("绑定关系不存在");
        }
        identityMapper.deleteById(id);
        log.info("已解绑外部身份: id={}, provider={}, externalUserId={}",
                id, exist.getProvider(), exist.getExternalUserId());
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        return user;
    }
}
