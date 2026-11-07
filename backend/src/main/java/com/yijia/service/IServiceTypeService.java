package com.yijia.service;

import com.yijia.dto.Result;
import com.yijia.entity.ServiceType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IServiceTypeService extends IService<ServiceType> {

    /**
     * 查询服务商类型列表（带 Redis 缓存）
     * @return 服务商类型列表
     */
    Result queryTypeList();
}
