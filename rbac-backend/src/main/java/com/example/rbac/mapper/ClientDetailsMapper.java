package com.example.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rbac.entity.ClientDetails;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ClientDetailsMapper extends BaseMapper<ClientDetails> {
}
