package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jodd.util.StringUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;


    @Override
    public List<Blog> queryHotBlogs(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.extracted(blog);
            this.isBlogLiked(blog);
        });
        return records;
    }

    private void extracted(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
    private void isBlogLiked(Blog blog){
        UserDTO user = UserHolder.getUser();
        if (user == null){
            blog.setIsLike(false);
            return;
        }
        long id = blog.getUserId();
        Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + id, String.valueOf(id));
        blog.setIsLike(score != null);
    }

    @Override
    public Object queryBlog(Long id) {
        //1.根据id查询博客
        Blog blog = getById(id);
        //2.根据博客id查询用户相关信息
        extracted(blog);
        //3.查询当前用户是否点赞过该博客
        isBlogLiked(blog);
        return blog;
    }

    /**
     * 获取当前用户是否点赞过该博客
     * @param id 博客id
     * @return
     */
    public Result isLiked(Long id) {
        String userId = UserHolder.getUser().getId().toString();
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        // 判断当前用户是否点赞过该博客
        Double score = stringRedisTemplate.opsForZSet().score(key, userId);
        if(score == null) {
            // 如果没有点赞
            boolean b = update().setSql("liked = liked + 1").eq("id", id).update();
            // 更新缓存
            if(b){
                stringRedisTemplate.opsForZSet().add(key, userId, System.currentTimeMillis());
            }
        }
        else {
            // 如果有点赞
            boolean b = update().setSql("liked = liked - 1").eq("id", id).update();
            // 更新缓存
            if(b){
                stringRedisTemplate.opsForZSet().remove(key,userId);
            }
        }
        return Result.ok(true);
    }

    /**
     * 查询博客点赞排行榜
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5 == null || top5.isEmpty()) return Result.ok();
        List<Long> ids = top5.stream().map(Long::parseLong).collect(Collectors.toList());
        String linkedId = StringUtil.join(ids, ",");
        List<User> users = userService.query().select("id", "nick_name", "icon").in("id", ids)
                .last("order by field(id," + linkedId +")").list();
        List<UserDTO> collect = users.stream().map(user -> {
            UserDTO userDTO = new UserDTO();
            BeanUtils.copyProperties(user, userDTO);
            return userDTO;
        }).collect(Collectors.toList());
        return Result.ok(collect);
    }

    @Override
    public Result saveBlog(Blog blog) {
        //1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 2. 保存探店博文
        boolean isSuccess = save(blog);
        //3. 保存成功后,查询当前博主的所有粉丝数据
        if(!isSuccess){
            return Result.fail("发布失败");
        }
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        List<Long> ids = follows.stream().map(Follow::getUserId).collect(Collectors.toList());
        //4. 推送数据给粉丝邮箱
        for (Long id : ids) {
            String key1 = RedisConstants.FEED_KEY + id;
            stringRedisTemplate.opsForZSet().add(key1, blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max,Long offset) {
        //1.获取当前用户相关信息
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        String key = RedisConstants.FEED_KEY + userId;
        //2.获取当前用户的收件箱
        Set<String> set = stringRedisTemplate.opsForZSet().reverseRangeByScore(key, 0, max, offset.intValue(), 3);
        if(set == null || set.isEmpty())return Result.ok();
        List<Long> ids = new ArrayList<>(set.size());
        long min = 0L;
        int count = 1;
        //3.获取当前用户的收件箱中的id并更行要返回的各个参数
        for (String s : set) {
            long id = Long.parseLong(s);
            ids.add(id);
            if(id == min){
                count++;
            }else{
                min = id;
                count = 1;
            }
        }
        //4.根据id查询博客
        String linkedId = StringUtil.join(ids, ",");
        List<Blog> blogs = query().in("id", ids).last("order by field(id, " + linkedId + ")").list();
        //5.定义好博客的内部类
        for (Blog blog : blogs) {
            //2.根据博客id查询用户相关信息
            extracted(blog);
            //3.查询当前用户是否点赞过该博客
            isBlogLiked(blog);
        }

        //6.封装返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setOffset(count);
        scrollResult.setList(blogs);
        scrollResult.setMinTime(min);

        return Result.ok(scrollResult);
    }
}
