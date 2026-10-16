package com.example.oauth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.oauth.common.BusinessException;
import com.example.oauth.common.ResultCode;
import com.example.oauth.entity.SysUser;
import com.example.oauth.mapper.SysUserMapper;
import com.example.oauth.security.SessionStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 第三方开放接口所需的用户访问
 * <p>
 * 只是 rbac-backend 那套 {@code SysUserService} 的两方法子集：本服务不做用户管理，
 * 仅支撑 {@code /openapi/users} 的查询与状态变更。因此不含岗位填充、角色维护、
 * 操作日志等后台管理逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenApiUserService {

    /** 状态取值：1 在职 / 0 停用 / 2 离职 */
    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_RESIGNED = 2;

    private final SysUserMapper userMapper;
    private final SessionStore sessionStore;

    public Page<SysUser> page(long pageNum, long pageSize, String username, Integer status) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .like(StringUtils.hasText(username), SysUser::getUsername, username)
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getCreateTime);
        return userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 修改用户状态。停用(0)与离职(2)都会删除该用户的登录会话，使其立即下线
     * （本系统是单会话模型）—— 第三方调用前请确认业务影响。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        SysUser exist = userMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setStatus(status);
        userMapper.updateById(update);

        if (status != null && (status == STATUS_DISABLED || status == STATUS_RESIGNED)) {
            sessionStore.removeSession(id);
            log.info("第三方接口将用户置为{}, 已立即失效登录凭证: {}", status, exist.getUsername());
        }
    }
}
