package com.campusOrder.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campusOrder.dto.LoginFormDTO;
import com.campusOrder.dto.Result;
import com.campusOrder.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  鏈嶅姟绫?
 * </p>
 *
 * @author 铏庡摜
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();

}

