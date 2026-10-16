package com.example.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.order.entity.OrderUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderUserMapper extends BaseMapper<OrderUser> {

    /**
     * 按登录名查用户
     * <p>
     * 登录名是 OAuth2 令牌里唯一可靠的身份（令牌中只有 user_name 声明），
     * 因此每个请求都靠它把令牌映射到本地同步过来的用户行。
     */
    @Select("SELECT * FROM order_user WHERE username = #{username}")
    OrderUser selectByUsername(@Param("username") String username);
}
