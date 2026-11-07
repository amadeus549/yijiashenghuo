package com.yijia.service;

/**
 * <p>
 * UV 统计服务：用 HyperLogLog 记录每天的独立访客数
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUVService {

    /**
     * 记录一次访问，同一个用户同一天重复调用不会重复计数
     */
    void record(Long userId);

    /**
     * 今天的 UV
     */
    long todayCount();

    /**
     * 指定日期的 UV，日期格式 yyyy-MM-dd
     */
    long countByDate(String date);

    /**
     * 一段日期区间的 UV（跨天去重），日期格式 yyyy-MM-dd
     */
    long countRange(String start, String end);
}
