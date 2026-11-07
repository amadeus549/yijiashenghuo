package com.yijia.service;

import com.yijia.dto.Result;
import com.yijia.entity.Provider;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IProviderService extends IService<Provider> {

    Result queryById(Long id);

    Result update(Provider provider);

    /**
     * 根据类型分页查询服务商；传了 x、y 就按距离由近到远排序
     */
    Result queryProviderByType(Integer typeId, Integer current, Double x, Double y);

    /**
     * 新增服务商，同时把坐标写入 Redis GEO
     */
    Result saveProvider(Provider provider);

    /**
     * 把数据库里所有服务商的坐标加载到 Redis GEO（已存在的类型不会重复写）
     */
    void loadProviderGeoData();
}
