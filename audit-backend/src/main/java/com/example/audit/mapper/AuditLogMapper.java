package com.example.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.audit.entity.AuditLogRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogRecord> {
}
