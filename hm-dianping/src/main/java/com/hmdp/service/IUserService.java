package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public interface IUserService extends IService<User> {

    /**
     * 发送验证码
     *
     * @param phone 手机号
     * @return 验证码发送结果
     */
    Result sentCode(String phone, HttpSession session);

    /**
     * 登录功能
     *
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     * @return 登录结果
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    /**
     * 登出功能
     *
     * @param request HTTP请求对象，用于获取请求头中的token
     * @return 登出结果
     */
    Result logout(HttpServletRequest request);

    /**
     * 签到功能
     * @return 签到结果
     */
    Result sign();

    /**
     * 统计签到功能
     * @return 签到结果
     */
    Result signCount();
}
