package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rbac.common.BusinessException;
import com.example.rbac.common.ResultCode;
import com.example.rbac.dto.DeptForm;
import com.example.rbac.entity.SysDept;
import com.example.rbac.entity.SysUser;
import com.example.rbac.mapper.SysDeptMapper;
import com.example.rbac.mapper.SysUserMapper;
import com.example.rbac.util.TreeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysDeptService {

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;

    /**
     * 部门树（部门管理页与部门选择器共用）
     */
    public List<SysDept> tree() {
        List<SysDept> depts = deptMapper.selectList(
                new LambdaQueryWrapper<SysDept>().orderByAsc(SysDept::getParentId, SysDept::getSort));
        return TreeUtils.buildTree(depts);
    }

    public SysDept getById(Long id) {
        SysDept dept = deptMapper.selectById(id);
        if (dept == null) {
            throw new BusinessException(ResultCode.DEPT_NOT_EXIST);
        }
        return dept;
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(DeptForm form) {
        if (isRealParent(form.getParentId())) {
            getById(form.getParentId());
        }
        SysDept dept = new SysDept();
        copy(form, dept);
        deptMapper.insert(dept);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(DeptForm form) {
        getById(form.getId());
        if (isRealParent(form.getParentId())) {
            if (form.getParentId().equals(form.getId())) {
                throw new BusinessException("上级部门不能是自己");
            }
            getById(form.getParentId());
            if (isDescendant(form.getId(), form.getParentId())) {
                throw new BusinessException("上级部门不能是自己的下级部门");
            }
        }
        SysDept dept = new SysDept();
        dept.setId(form.getId());
        copy(form, dept);
        deptMapper.updateById(dept);
    }

    /** 是否指定了真实的上级部门（null 与 0 都表示顶级） */
    private boolean isRealParent(Long parentId) {
        return parentId != null && parentId != 0L;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getById(id);
        Long children = deptMapper.selectCount(
                new LambdaQueryWrapper<SysDept>().eq(SysDept::getParentId, id));
        if (children != null && children > 0) {
            throw new BusinessException(ResultCode.DEPT_HAS_CHILDREN);
        }
        Long users = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeptId, id));
        if (users != null && users > 0) {
            throw new BusinessException(ResultCode.DEPT_IN_USE);
        }
        deptMapper.deleteById(id);
    }

    /**
     * 自 candidateParentId 向上回溯，判断 targetId 是否为其祖先。
     * 用于阻止把上级部门设成自己的子孙，形成环。
     */
    private boolean isDescendant(Long targetId, Long candidateParentId) {
        Long current = candidateParentId;
        while (current != null && current != 0L) {
            if (current.equals(targetId)) {
                return true;
            }
            SysDept parent = deptMapper.selectById(current);
            current = parent == null ? null : parent.getParentId();
        }
        return false;
    }

    private void copy(DeptForm form, SysDept dept) {
        dept.setParentId(form.getParentId() == null ? 0L : form.getParentId());
        dept.setDeptName(form.getDeptName());
        dept.setType(form.getType());
        dept.setSort(form.getSort() == null ? 0 : form.getSort());
        dept.setLeader(form.getLeader());
        dept.setPhone(form.getPhone());
        dept.setEmail(form.getEmail());
        dept.setStatus(form.getStatus() == null ? 1 : form.getStatus());
    }
}
