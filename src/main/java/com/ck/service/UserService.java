package com.ck.service;

import com.ck.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ck.model.vo.LoginUserVO;

import javax.servlet.http.HttpServletRequest;

/**
* 用户服务
* @author 15925
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2024-03-11 09:46:41
*/
public interface UserService extends IService<User> {

    /**
     * 用户注册
     * @param userAccount 用户账户
     * @param userPassword 用户密码
     * @param checkPassword 校验密码
     * @return 新用户id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    LoginUserVO getLoginUserVO(User user);

    User getLoginUser(HttpServletRequest request);
}
