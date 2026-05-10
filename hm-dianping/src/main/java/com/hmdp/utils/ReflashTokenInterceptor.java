package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ReflashTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate  stringRedisTemplate;

    public ReflashTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                @Nullable Exception ex) throws Exception {
        UserHolder.removeUser();
    }

    @Override
    public boolean preHandle(HttpServletRequest request
            , @NonNull HttpServletResponse response
            , @NonNull Object handler) throws Exception {
        //  1.从请求头中获取 token
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank( token)){
            return true;
        }
        // 2.从redis中根据token为key获取用户
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> user = stringRedisTemplate.opsForHash().entries(key);
        //3.判断用户是否存在
        if (user == null) {
            //4.不存在,拦截
            return true;
        }
        // 5.将用户转换为UserDTO
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 6.存在保存用户信息到本地ThreadLocal
        UserHolder.saveUser(userDTO);
        // 7.刷新token有效期
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        //8.放行
        return true;
    }
}
