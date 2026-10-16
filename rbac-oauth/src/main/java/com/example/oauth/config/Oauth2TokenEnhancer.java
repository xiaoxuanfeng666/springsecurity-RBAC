package com.example.oauth.config;

import com.example.oauth.security.LoginUser;
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

        // 客户端凭证模式（client_credentials）没有用户参与，principal 是客户端 id 字符串而非登录用户，
        // 直接强转会 ClassCastException。这里先判断类型：不是登录用户就只签发基础令牌，
        // 不附加 userId / nickName 这类用户相关声明。
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof LoginUser)) {
            return accessToken;
        }
        LoginUser loginUser = (LoginUser) principal;

        final Map<String, Object> additionalInfo = new HashMap<>();
        final Map<String, Object> retMap = new HashMap<>();
        //todo 这里暴露userId到Jwt的令牌中,后期可以根据自己的业务需要 进行添加字段
        additionalInfo.put("userId", loginUser.getUser().getId());
        additionalInfo.put("nickName", loginUser.getUser().getNickname());
        retMap.put("additionalInfo", additionalInfo);
        ((DefaultOAuth2AccessToken) accessToken).setAdditionalInformation(retMap);
        return accessToken;
    }
}