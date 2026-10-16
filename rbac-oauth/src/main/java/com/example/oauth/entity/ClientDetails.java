package com.example.oauth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * OAuth2 客户端配置
 * <p>
 * 表 clientdetails 沿用 Spring Security OAuth 的约定，列名为驼峰且主键是非自增的 appId，
 * 与项目其余表的蛇形命名不同，因此每个字段都需显式声明 {@link TableField}，
 * 否则 MyBatis-Plus 会按默认的驼峰转蛇形规则拼出 app_id 之类的列名。
 * <p>
 * 本表无逻辑删除与时间戳列，故不使用 {@code @TableLogic} 与自动填充。
 */
@Data
@TableName("clientdetails")
public class ClientDetails implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 客户端标识，主键，由使用方指定 */
    @TableId(value = "appId", type = IdType.INPUT)
    private String appId;

    /** 资源ID，逗号分隔 */
    @TableField("resourceIds")
    private String resourceIds;

    /** 客户端密钥 */
    @TableField("appSecret")
    private String appSecret;

    /** 授权范围，逗号分隔 */
    @TableField("scope")
    private String scope;

    /** 授权模式，逗号分隔 */
    @TableField("grantTypes")
    private String grantTypes;

    /** 回调地址，逗号分隔 */
    @TableField("redirectUrl")
    private String redirectUrl;

    /** 客户端本身拥有的权限，逗号分隔 */
    @TableField("authorities")
    private String authorities;

    /** 访问令牌有效期（秒） */
    @TableField("access_token_validity")
    private Integer accessTokenValidity;

    /** 刷新令牌有效期（秒） */
    @TableField("refresh_token_validity")
    private Integer refreshTokenValidity;

    /** 扩展信息（JSON） */
    @TableField("additionalInformation")
    private String additionalInformation;

    /** 自动放行的授权范围，逗号分隔 */
    @TableField("autoApproveScopes")
    private String autoApproveScopes;
}
