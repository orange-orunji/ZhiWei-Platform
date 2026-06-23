package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.UserInfo;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IUserInfoService extends IService<UserInfo> {

    Result updateUserInfo(UserInfo userInfo);
}
