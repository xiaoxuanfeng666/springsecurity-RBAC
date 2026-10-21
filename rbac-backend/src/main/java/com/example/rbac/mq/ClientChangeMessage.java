package com.example.rbac.mq;

import lombok.Data;

import java.io.Serializable;

/**
 * 「客户端配置变更」的 MQ 消息
 * <p>
 * 管理端编辑 / 删除客户端后由本服务发出，rbac-oauth 消费后删掉
 * {@code oauth2:client:{clientId}} 缓存，使下一次令牌请求回源数据库。
 * 两个服务没有共享模块，这个类在两边各有一份 —— 字段名即契约，
 * 改动时需与 {@code com.example.oauth.mq.ClientChangeMessage} 同步。
 */
@Data
public class ClientChangeMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 新增客户端 */
    public static final String TYPE_CREATE = "CREATE";
    /** 修改客户端 */
    public static final String TYPE_UPDATE = "UPDATE";
    /** 删除客户端 */
    public static final String TYPE_DELETE = "DELETE";

    private String type;

    /** 客户端标识（clientdetails.appId） */
    private String clientId;

    /** 毫秒时间戳 */
    private Long timestamp;
}
