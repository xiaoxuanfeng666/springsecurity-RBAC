package com.example.rbac.config;

import com.example.rbac.security.LoginUser;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.TokenEnhancer;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
@Component
public class Oauth2TokenEnhancer implements TokenEnhancer {
    @Override
    public OAuth2AccessToken enhance(OAuth2AccessToken accessToken, OAuth2Authentication authentication) {

        LoginUser loginUser= (LoginUser) authentication.getPrincipal();
        final Map<String, Object> additionalInfo = new HashMap<>();
        final Map<String, Object> retMap = new HashMap<>();
        //todo 这里暴露userId到Jwt的令牌中,后期可以根据自己的业务需要 进行添加字段
        additionalInfo.put("userId",loginUser.getUser().getId());
        additionalInfo.put("nickName",loginUser.getUser().getNickname());
        retMap.put("additionalInfo",additionalInfo);
        ((DefaultOAuth2AccessToken) accessToken).setAdditionalInformation(retMap);
        return accessToken;
    }
}