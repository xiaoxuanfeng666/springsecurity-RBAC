package com.example.oauth.identity;

import com.example.oauth.security.LoginUser;
import com.example.oauth.security.OauthAuthStore;
import com.example.oauth.security.SsoSessionStore;
import com.example.oauth.service.SsoSessionRecordService;
import com.example.oauth.util.DeviceInfoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 建立与密码登录完全一致的 SSO 登录态（单会话模型）
 */
@Service
@RequiredArgsConstructor
public class IdpSessionService {

    private final OauthAuthStore oauthAuthStore;
    private final SsoSessionStore ssoSessionStore;
    private final SsoSessionRecordService sessionRecordService;

    public void establish(LoginUser loginUser, HttpServletRequest request,
                          HttpServletResponse response, String loginType) {
        String username = loginUser.getUsername();
        String previousToken = ssoSessionStore.getBrowserSessionId(username);
        if (previousToken != null) {
            oauthAuthStore.delete(previousToken);
        }
        String token = oauthAuthStore.create(loginUser, request);
        OauthAuthStore.writeCookie(response, token);
        ssoSessionStore.bindBrowserSession(username, token);
        sessionRecordService.recordLogin(loginUser,
                DeviceInfoUtils.ip(request), DeviceInfoUtils.userAgent(request), loginType);
    }
}
