package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {


    /**
     * 关注或取消关注
     *
     * @param id       关注的用户id
     * @param isTrue   是否关注
     * @return
     */
    Result follow(Long id, Boolean isTrue);

    /**
     * 判断当前用户是否关注了该用户
     *
     * @param id
     * @return
     */
    Result isFollow(Long id);

    /**
     * 查询共同关注
     * @param id
     * @return
     */
    Result common(Long id);
}
