package com.example.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rbac.entity.SsoSessionRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SsoSessionRecordMapper extends BaseMapper<SsoSessionRecord> {
}
