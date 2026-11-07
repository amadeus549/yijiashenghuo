package com.yijia.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yijia.dto.LoginFormDTO;
import com.yijia.dto.Result;
import com.yijia.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    /**
     * 当天签到，把本月对应日期的 bit 置为 1
     */
    Result sign();

    /**
     * 统计本月截至今天连续签到的天数
     */
    Result signCount();
}
