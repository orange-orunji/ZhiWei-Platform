package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {


    /**
     * 关注或取关
     * @param id       关注的用户id
     * @param isTrue   是否关注
     * @return
     */
    @Override
    public Result follow(Long id, Boolean isTrue) {
        Long userId = UserHolder.getUser().getId();
        if (isTrue) {
            Follow follow = new Follow();
            follow.setFollowUserId( id);
            follow.setUserId(userId);
            follow.setCreateTime(LocalDateTime.now());
            //关注
            save(follow);
        } else {
            //取关
            remove(new QueryWrapper<Follow>().eq("follow_user_id", id).eq("user_id", userId));

        }
        return Result.ok();
    }

    /**
     * 判断是否关注
     * @param id
     * @return
     */
    @Override
    public Result isFollow(Long id) {
        Long userId = UserHolder.getUser().getId();
        Integer user = query().eq("id", userId).eq("follow_id", id).count();
        return Result.ok(user != 0);
    }
}
