package com.yijia.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 我的订单列表项：订单本身 + 套餐/服务商信息，避免前端二次请求
 */
@Data
public class DealOrderVO {

    private Long id;
    private Long dealId;
    private Long providerId;

    /** 1 待支付 2 已支付 4 已取消 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime payTime;

    /** 套餐标题、封面图、实付金额（分） */
    private String title;
    private String image;
    private Long payValue;

    private String providerName;

    /** 该订单对应的抢购是否还在进行中，用于决定能否取消 */
    private Boolean cancelable;
}
