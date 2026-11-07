package com.yijia.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yijia.entity.Deal;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface DealMapper extends BaseMapper<Deal> {

    List<Deal> queryDealOfProvider(@Param("providerId") Long providerId);
}
