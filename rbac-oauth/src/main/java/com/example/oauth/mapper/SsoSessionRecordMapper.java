package com.example.oauth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.oauth.entity.SsoSessionRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SsoSessionRecordMapper extends BaseMapper<SsoSessionRecord> {
}
