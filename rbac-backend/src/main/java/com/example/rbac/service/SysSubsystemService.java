package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rbac.common.BusinessException;
import com.example.rbac.common.ResultCode;
import com.example.rbac.dto.SubsystemForm;
import com.example.rbac.entity.SysSubsystem;
import com.example.rbac.entity.SysSubsystemMenu;
import com.example.rbac.mapper.SysSubsystemMapper;
import com.example.rbac.mapper.SysSubsystemMenuMapper;
import com.example.rbac.util.UniqueKeyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 子系统登记
 */
@Service
@RequiredArgsConstructor
public class SysSubsystemService {

    /** sys_subsystem.subsystem_code 列长度 */
    private static final int CODE_MAX_LENGTH = 64;

    private final SysSubsystemMapper subsystemMapper;
    private final SysSubsystemMenuMapper subsystemMenuMapper;

    public Page<SysSubsystem> page(long pageNum, long pageSize, String subsystemName) {
        LambdaQueryWrapper<SysSubsystem> wrapper = new LambdaQueryWrapper<SysSubsystem>()
                .like(StringUtils.hasText(subsystemName), SysSubsystem::getSubsystemName, subsystemName)
                .orderByAsc(SysSubsystem::getSort);
        return subsystemMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 全部启用子系统（子系统菜单页、角色授权页的下拉框使用）
     */
    public List<SysSubsystem> listAll() {
        return subsystemMapper.selectList(new LambdaQueryWrapper<SysSubsystem>()
                .eq(SysSubsystem::getStatus, 1)
                .orderByAsc(SysSubsystem::getSort));
    }

    public SysSubsystem getById(Long id) {
        SysSubsystem subsystem = subsystemMapper.selectById(id);
        if (subsystem == null) {
            throw new BusinessException(ResultCode.SUBSYSTEM_NOT_EXIST);
        }
        return subsystem;
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(SubsystemForm form) {
        Long count = subsystemMapper.selectCount(new LambdaQueryWrapper<SysSubsystem>()
                .eq(SysSubsystem::getSubsystemCode, form.getSubsystemCode()));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.SUBSYSTEM_CODE_EXISTS);
        }
        SysSubsystem subsystem = new SysSubsystem();
        copy(form, subsystem);
        subsystemMapper.insert(subsystem);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SubsystemForm form) {
        SysSubsystem exist = getById(form.getId());
        if (!exist.getSubsystemCode().equals(form.getSubsystemCode())) {
            Long count = subsystemMapper.selectCount(new LambdaQueryWrapper<SysSubsystem>()
                    .eq(SysSubsystem::getSubsystemCode, form.getSubsystemCode()));
            if (count != null && count > 0) {
                throw new BusinessException(ResultCode.SUBSYSTEM_CODE_EXISTS);
            }
        }
        SysSubsystem subsystem = new SysSubsystem();
        subsystem.setId(form.getId());
        copy(form, subsystem);
        subsystemMapper.updateById(subsystem);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysSubsystem subsystem = getById(id);
        Long menus = subsystemMenuMapper.selectCount(new LambdaQueryWrapper<SysSubsystemMenu>()
                .eq(SysSubsystemMenu::getSubsystemId, id));
        if (menus != null && menus > 0) {
            throw new BusinessException(ResultCode.SUBSYSTEM_HAS_MENUS);
        }
        releaseCode(subsystem);
        subsystemMapper.deleteById(id);
    }

    /**
     * 腾出 subsystem_code 唯一键：逻辑删除后行仍在表中，不改名同编码的子系统就再也建不出来
     */
    private void releaseCode(SysSubsystem subsystem) {
        SysSubsystem renamed = new SysSubsystem();
        renamed.setId(subsystem.getId());
        renamed.setSubsystemCode(UniqueKeyUtils.release(
                subsystem.getSubsystemCode(), subsystem.getId(), CODE_MAX_LENGTH));
        subsystemMapper.updateById(renamed);
    }

    private void copy(SubsystemForm form, SysSubsystem subsystem) {
        subsystem.setSubsystemCode(form.getSubsystemCode());
        subsystem.setSubsystemName(form.getSubsystemName());
        subsystem.setSort(form.getSort() == null ? 0 : form.getSort());
        subsystem.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        subsystem.setRemark(form.getRemark());
    }
}
