package com.ld.poetry.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.User;
import com.ld.poetry.vo.BaseRequestVO;
import com.ld.poetry.vo.UserVO;

import java.util.List;

/**
 * <p>
 * 用户信息表 服务类
 * </p>
 *
 * @author sara
 * @since 2021-08-12
 */
public interface UserService extends IService<User> {

    /**
     * 用户名、邮箱、手机号/密码登录
     *
     * @param account
     * @param password
     * @return
     */
    PoetryResult<UserVO> login(String account, String password, Boolean isAdmin);

    PoetryResult exit();

    PoetryResult<UserVO> regist(UserVO user);

    PoetryResult<UserVO> updateUserInfo(UserVO user);

    PoetryResult getCode(Integer flag);

    PoetryResult getCodeForBind(String place, Integer flag);

    PoetryResult<UserVO> updateSecretInfo(String place, Integer flag, String code, String password);

    PoetryResult getCodeForForgetPassword(String place, Integer flag);

    PoetryResult updateForForgetPassword(String place, Integer flag, String code, String password);

    PoetryResult<Page> listUser(BaseRequestVO baseRequestVO);

    PoetryResult<List<UserVO>> getUserByUsername(String username);

    PoetryResult<UserVO> token(String userToken);

    PoetryResult<UserVO> subscribe(Integer labelId, Boolean flag);

    /**
     * 第三方登录
     *
     * @param provider 平台类型
     * @param uid 用户在第三方平台的唯一标识
     * @param username 用户名
     * @param email 邮箱
     * @param avatar 头像
     * @return 登录结果
     */
    PoetryResult<UserVO> thirdLogin(String provider, String uid, String username, String email, String avatar);
}
