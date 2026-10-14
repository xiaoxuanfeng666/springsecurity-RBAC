package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rbac.common.BusinessException;
import com.example.rbac.common.ResultCode;
import com.example.rbac.dto.PostForm;
import com.example.rbac.entity.SysPost;
import com.example.rbac.entity.SysUserPost;
import com.example.rbac.mapper.SysPostMapper;
import com.example.rbac.mapper.SysUserPostMapper;
import com.example.rbac.util.UniqueKeyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysPostService {

    /** sys_post.post_code 列长度 */
    private static final int POST_CODE_MAX_LENGTH = 64;

    private final SysPostMapper postMapper;
    private final SysUserPostMapper userPostMapper;

    public Page<SysPost> page(long pageNum, long pageSize, String postName) {
        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<SysPost>()
                .like(StringUtils.hasText(postName), SysPost::getPostName, postName)
                .orderByAsc(SysPost::getSort);
        return postMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 全部启用岗位（用户表单多选使用）
     */
    public List<SysPost> listAll() {
        return postMapper.selectList(new LambdaQueryWrapper<SysPost>()
                .eq(SysPost::getStatus, 1)
                .orderByAsc(SysPost::getSort));
    }

    public SysPost getById(Long id) {
        SysPost post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_EXIST);
        }
        return post;
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(PostForm form) {
        checkCodeUnique(form.getPostCode(), null);
        SysPost post = new SysPost();
        copy(form, post);
        postMapper.insert(post);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(PostForm form) {
        getById(form.getId());
        checkCodeUnique(form.getPostCode(), form.getId());
        SysPost post = new SysPost();
        post.setId(form.getId());
        copy(form, post);
        postMapper.updateById(post);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysPost post = getById(id);
        Long used = userPostMapper.selectCount(
                new LambdaQueryWrapper<SysUserPost>().eq(SysUserPost::getPostId, id));
        if (used != null && used > 0) {
            throw new BusinessException(ResultCode.POST_IN_USE);
        }
        releasePostCode(post);
        postMapper.deleteById(id);
    }

    /**
     * 腾出 post_code 唯一键：逻辑删除后行仍在表中，不改名同编码的岗位就再也建不出来
     */
    private void releasePostCode(SysPost post) {
        SysPost renamed = new SysPost();
        renamed.setId(post.getId());
        renamed.setPostCode(
                UniqueKeyUtils.release(post.getPostCode(), post.getId(), POST_CODE_MAX_LENGTH));
        postMapper.updateById(renamed);
    }

    private void checkCodeUnique(String postCode, Long excludeId) {
        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<SysPost>()
                .eq(SysPost::getPostCode, postCode);
        if (excludeId != null) {
            wrapper.ne(SysPost::getId, excludeId);
        }
        Long count = postMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.POST_CODE_EXISTS);
        }
    }

    private void copy(PostForm form, SysPost post) {
        post.setPostCode(form.getPostCode());
        post.setPostName(form.getPostName());
        post.setSort(form.getSort() == null ? 0 : form.getSort());
        post.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        post.setRemark(form.getRemark());
    }
}
