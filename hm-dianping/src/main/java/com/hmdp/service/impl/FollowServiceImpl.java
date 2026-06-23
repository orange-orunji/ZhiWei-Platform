package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private final StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    @Resource
    private IUserInfoService userInfoService;

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
        String key = "follow:" + id;
        if (isTrue) {
            Follow follow = new Follow();
            follow.setFollowUserId(id);
            follow.setUserId(userId);
            follow.setCreateTime(LocalDateTime.now());
            //关注
            boolean isSuccess = save(follow);
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(key, userId.toString());
                updateCount(userId, id, true);
            }
        } else {
            //取关
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("follow_user_id", id).eq("user_id", userId));
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key, userId.toString());
                updateCount(userId, id, false);
            }
        }
        return Result.ok();
    }

    private void updateCount(Long userId, Long targetId, boolean isFollow) {
        ensureUserInfoExists(userId);
        ensureUserInfoExists(targetId);
        String sql = isFollow ? " + 1" : " - 1";
        userInfoService.update(new UpdateWrapper<UserInfo>()
                .eq("user_id", userId).setSql("followee = followee" + sql));
        userInfoService.update(new UpdateWrapper<UserInfo>()
                .eq("user_id", targetId).setSql("fans = fans" + sql));
    }

    private void ensureUserInfoExists(Long userId) {
        if (userInfoService.getById(userId) == null) {
            UserInfo info = new UserInfo();
            info.setUserId(userId);
            info.setFans(0);
            info.setFollowee(0);
            info.setCredits(0);
            info.setLevel(false);
            info.setCreateTime(LocalDateTime.now());
            info.setUpdateTime(LocalDateTime.now());
            userInfoService.save(info);
        }
    }

    @Override
    public Result isFollow(Long id) {
        Long userId = UserHolder.getUser().getId();
        Long user = Long.valueOf(query().eq("user_id", userId).eq("follow_user_id", id).count());
        return Result.ok(user > 0);
    }

    @Override
    public Result common(Long id) {
        String key1 = "follow:" + id;
        String key2 = "follow:" + UserHolder.getUser().getId();
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(0);
        }
        List<Long> list = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("id", list);
        List<User> result = userService.list(queryWrapper);
        return Result.ok(result);
    }

    @Override
    public Result queryFans() {
        Long userId = UserHolder.getUser().getId();
        List<Follow> follows = query().eq("follow_user_id", userId).list();
        if (follows.isEmpty()) return Result.ok(Collections.emptyList());
        List<Long> ids = follows.stream().map(Follow::getUserId).collect(Collectors.toList());
        List<User> users = userService.listByIds(ids);
        return Result.ok(buildUserList(users));
    }

    @Override
    public Result queryFollowee() {
        Long userId = UserHolder.getUser().getId();
        List<Follow> follows = query().eq("user_id", userId).list();
        if (follows.isEmpty()) return Result.ok(Collections.emptyList());
        List<Long> ids = follows.stream().map(Follow::getFollowUserId).collect(Collectors.toList());
        List<User> users = userService.listByIds(ids);
        return Result.ok(buildUserList(users));
    }

    private List<Map<String, Object>> buildUserList(List<User> users) {
        return users.stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("nickName", u.getNickName());
            m.put("icon", u.getIcon() != null ? u.getIcon() : "/imgs/icons/default-icon.png");
            return m;
        }).collect(Collectors.toList());
    }
}
