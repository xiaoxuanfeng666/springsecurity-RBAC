package com.example.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.order.dto.OrderStatusCount;
import com.example.order.entity.OrderInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderInfoMapper extends BaseMapper<OrderInfo> {

    /**
     * 按状态汇总：每个状态的单数与金额合计
     * <p>
     * 交给数据库 GROUP BY 而不是把全部订单捞出来在内存里累加 ——
     * 统计页是只读入口，订单量增长后不该跟着变慢。
     * 逻辑删除由调用方用 {@code del_flag = 0} 兜住（这里是手写 SQL，MyBatis-Plus 不介入）。
     */
    @Select("SELECT status, COUNT(*) AS count, COALESCE(SUM(order_amount), 0) AS amount " +
            "FROM order_info WHERE del_flag = 0 GROUP BY status ORDER BY status")
    List<OrderStatusCount> selectStatusCount();
}
