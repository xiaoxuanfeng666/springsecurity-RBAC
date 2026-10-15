package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rbac.common.BusinessException;
import com.example.rbac.common.ResultCode;
import com.example.rbac.dto.ClientForm;
import com.example.rbac.entity.ClientDetails;
import com.example.rbac.mapper.ClientDetailsMapper;
import com.example.rbac.oauth.ClientSecretCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ClientDetailsService {

    private final ClientDetailsMapper clientDetailsMapper;
    private final ClientSecretCipher clientSecretCipher;

    /**
     * 分页查询
     * <p>
     * appSecret 在库中是 AES 密文，此处解密后以明文返回，供页面展示与复制给第三方。
     */
    public Page<ClientDetails> page(long pageNum, long pageSize, String appId) {
        LambdaQueryWrapper<ClientDetails> wrapper = new LambdaQueryWrapper<ClientDetails>()
                .like(StringUtils.hasText(appId), ClientDetails::getAppId, appId)
                .orderByAsc(ClientDetails::getAppId);
        Page<ClientDetails> result = clientDetailsMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        result.getRecords().forEach(client -> client.setAppSecret(clientSecretCipher.decrypt(client.getAppSecret())));
        return result;
    }

    /**
     * 详情：同样解密后返回明文
     */
    public ClientDetails getById(String appId) {
        ClientDetails client = clientDetailsMapper.selectById(appId);
        if (client == null) {
            throw new BusinessException(ResultCode.CLIENT_NOT_EXIST);
        }
        client.setAppSecret(clientSecretCipher.decrypt(client.getAppSecret()));
        return client;
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(ClientForm form) {
        if (!StringUtils.hasText(form.getAppSecret())) {
            throw new BusinessException(ResultCode.CLIENT_SECRET_REQUIRED);
        }
        if (clientDetailsMapper.selectById(form.getAppId()) != null) {
            throw new BusinessException(ResultCode.CLIENT_ID_EXISTS);
        }
        ClientDetails client = new ClientDetails();
        copy(form, client);
        clientDetailsMapper.insert(client);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ClientForm form) {
        requireExists(form.getAppId());
        ClientDetails client = new ClientDetails();
        // 密钥留空时 copy 会置 null，updateById 跳过该列，即保持原密钥不变
        copy(form, client);
        clientDetailsMapper.updateById(client);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String appId) {
        requireExists(appId);
        clientDetailsMapper.deleteById(appId);
    }

    private void requireExists(String appId) {
        if (clientDetailsMapper.selectById(appId) == null) {
            throw new BusinessException(ResultCode.CLIENT_NOT_EXIST);
        }
    }

    private void copy(ClientForm form, ClientDetails client) {
        client.setAppId(form.getAppId());
        client.setResourceIds(form.getResourceIds());
        // 密钥以 AES 加密存储（页面展示时解密回明文，授权服务器校验时解密比对）；
        // 留空则置 null —— 新增时会被前置校验拦下，修改时 updateById 跳过该列即「不修改」
        client.setAppSecret(StringUtils.hasText(form.getAppSecret())
                ? clientSecretCipher.encrypt(form.getAppSecret())
                : null);
        client.setScope(form.getScope());
        client.setGrantTypes(form.getGrantTypes());
        client.setRedirectUrl(form.getRedirectUrl());
        client.setAuthorities(form.getAuthorities());
        client.setAccessTokenValidity(form.getAccessTokenValidity());
        client.setRefreshTokenValidity(form.getRefreshTokenValidity());
        client.setAdditionalInformation(form.getAdditionalInformation());
        client.setAutoApproveScopes(form.getAutoApproveScopes());
    }
}
