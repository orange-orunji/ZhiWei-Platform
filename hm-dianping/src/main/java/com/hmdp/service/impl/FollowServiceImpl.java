package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Resource
    private final StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    public FollowServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 关注或取关
     * @param id       关注的用户id
     * @param isTrue   是否关注
     * @return
     */
    @Override
    public Result follow(Long id, Boolean isTrue) {
        Long userId = UserHolder.getUser().getId();
        String key = "follow:"+ id;
        if (isTrue) {
            Follow follow = new Follow();
            follow.setFollowUserId( id);
            follow.setUserId(userId);
            follow.setCreateTime(LocalDateTime.now());
            //关注
            boolean isSuccess = save(follow);
            if(isSuccess){
                stringRedisTemplate.opsForSet().add(key,userId.toString());
            }
        } else {
            //取关
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("follow_user_id", id).eq("user_id", userId));
            if(isSuccess){
                stringRedisTemplate.opsForSet().remove(key,userId.toString());
            }
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
        Long user = Long.valueOf(query().eq("user_id", userId).eq("follow_user_id", id).count());
        return Result.ok(user > 0);
    }

    @Override
    public Result common(Long id) {
        String key1 = "follow:"+ id;
        String key2 = "follow:"+ UserHolder.getUser().getId();
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if(intersect == null || intersect.isEmpty()){
            return Result.ok(0);
        }
        List<Long> list = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("id",list);
        List<User> result = userService.list(queryWrapper);
        return Result.ok(result);
    }
}
