package com.example.oauth.identity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 身份登录的 state 内容：服务提供方 + 登录后要回到的授权地址
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdpState implements Serializable {

    private static final long serialVersionUID = 1L;

    private String provider;

    private String returnTo;
}
