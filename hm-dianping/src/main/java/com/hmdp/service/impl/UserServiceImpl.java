package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 发送验证码
     *
     * @param phone   手机号
     * @param session
     * @return
     */
    @Override
    public Result sentCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误,请重新输入");
        }
        //2.生成验证码
        String code = RandomUtil.randomNumbers(6);
//        在session中本地存储
//        session.setAttribute("code", code);
        //3.保存验证码到redis存储
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY+ phone
                ,code
                ,RedisConstants.LOGIN_CODE_TTL
                , TimeUnit.MINUTES);
        //Todo 4.发送验证码
        log.info("发送验证码成功,验证码为：{}", code);
        //5.返回验证码
        return Result.ok();
    }

    /**
     * 登录功能
     *
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     * @return 登录结果
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //从session中获取对应的手机号
        String phone = loginForm.getPhone();
        //1.校验手机号是否合法
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误,请重新输入");
        }
        String caCheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY+phone);
        //2.从redis中获取phone对象校验验证码
        if (caCheCode != null && !loginForm.getCode().equals(caCheCode)) {
            return Result.fail("验证码错误,请检查手机号或验证码是否正确");
        }
        //3.查询用户 select * from user where phone = phone
        User user = query().eq("phone", phone).one();
        //4.验证用户是否存在,不存在则创建
        if (user == null) {
            user = createByPhone(phone);
        }
        //生成token作为登录令牌
        String token = UUID.randomUUID().toString();
        //将user转换为userDTO对象
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //5.保存Hash对象到redis
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY+token,
                BeanUtil.beanToMap(userDTO,new HashMap<>(), CopyOptions.create()
                        .ignoreNullValue().setFieldValueEditor((fleid, value)->value.toString())));
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY+token,
                RedisConstants.LOGIN_USER_TTL,
                TimeUnit.MINUTES);
        return Result.ok(token);
    }

    @Override
    public Result logout(HttpServletRequest request) {
        // 1. 从请求头中获取 token
        String token = request.getHeader("authorization");
        if (token == null || token.isEmpty()) {
            return Result.fail("用户未登录");
        }
        // 2. 删除 Redis 中的用户 token
        stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + token);
        // 3. 清除 ThreadLocal 中的用户信息
        UserHolder.removeUser();
        return Result.ok();
    }

    /**
     * 签到功能
     * @return
     */
    @Override
    public Result sign() {
        //1.获取当前用户
        UserDTO user = UserHolder.getUser();
        //2.获取当前日期
        LocalDateTime current = LocalDateTime.now();
        //3.定义字符串
        String key = RedisConstants.USER_SIGN_KEY + user.getId() + current.format(DateTimeFormatter.ofPattern("yyyyMM"));
        //4.获取当前日在当月第几天
        int day = current.getDayOfMonth();
        //5.签到
        stringRedisTemplate.opsForValue().setBit(key, day - 1, true);
        return Result.ok();
    }

    /**
     * 签到统计功能
     * @return
     */
    @Override
    public Result signCount() {
        //1.获取当前用户
        UserDTO user = UserHolder.getUser();
        //2.获取当前日期
        LocalDateTime current = LocalDateTime.now();
        //3.定义字符串
        String key = RedisConstants.USER_SIGN_KEY + user.getId() + current.format(DateTimeFormatter.ofPattern("yyyyMM"));
        //4.获取当前日在当月第几天
        int dayOfMonth = current.getDayOfMonth();
        //5.获取当月签到信息
        List<Long> lists =
                stringRedisTemplate.opsForValue()
                        .bitField(key,BitFieldSubCommands
                                .create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        //6.统计连续签到次数
        if(lists == null || lists.isEmpty()){
            return Result.ok(0);
        }
        Long l = lists.get(0);
        int count = 0;
        //统计
        while ((l & 1) != 0) {
            count++;
            l >>>= 1;
        }
        return Result.ok(count);
    }


    /**
     * 创建新用户
     *
     * @param phone
     * @return
     */
    private User createByPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
