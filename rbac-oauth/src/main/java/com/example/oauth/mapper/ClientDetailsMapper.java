package com.example.oauth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.oauth.entity.ClientDetails;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ClientDetailsMapper extends BaseMapper<ClientDetails> {
}
