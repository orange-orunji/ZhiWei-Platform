package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import io.lettuce.core.RedisException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    /**
     * 根据id查询店铺信息
     * @param id 店铺id
     * @return 店铺信息
     */
    @Override
    public Result queryById(Long id) {
        //缓存穿透
//        Shop shop = queryWithPassThrough(id);
        //互斥锁解决缓存击穿(缓存穿透解决(保存空缓存)模板上加上了缓存击穿)
//        Shop shop = queryWithMutex(id);
        //逻辑锁解决缓存击穿问题
        Shop shop = queryWithExpireTime(id);
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        //返回
        return Result.ok(shop);
    }

    /**
     * 逻辑锁解决缓存击穿
     * @param id
     * @return
     */

    public Shop queryWithMutex(Long id) {
        //1.查询redis是否存在店铺信息
        String sp = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2.存在返回
        if(StrUtil.isNotBlank(sp)){
            return JSONUtil.toBean(sp, Shop.class);
        }
        //3.判断查询到的数据是否为null(获取到空缓存的情况)
        if(sp != null){
            return null;
        }
        //4.1获得互斥锁对象
        boolean isLock = tryLock(id);
        //4.2获取失败则休眠(递归等待)
        Shop shop;
        try {
            while (!isLock){
                Thread.sleep(50);
                isLock = tryLock(id);
            }
            sp = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            if(StrUtil.isNotBlank(sp)){
                return JSONUtil.toBean(sp, Shop.class);
            }
            //4.3不存在，查询数据库
            shop = getById(id);
            //5.店铺不存在
            if(shop == null) {
                //将空值写入redis
                stringRedisTemplate.opsForValue()
                        .set(CACHE_SHOP_KEY+id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            //6.将查询到的id信息保存到redis
            stringRedisTemplate.opsForValue()
                    .set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //7.释放锁
            unLock(id);
        }
        //8.返回
        return shop;
    }
    /**
     * 缓存穿透解决方法
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        //查询redis是否存在店铺信息
        String sp = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //存在返回
        if(StrUtil.isNotBlank(sp)){
            return JSONUtil.toBean(sp, Shop.class);
        }
        //判断查询到的数据是否为null
        if(sp != null){
            return null;
        }
        //不存在，查询数据库
        Shop shop = getById(id);
        //店铺不存在
        if(shop == null) {
            //设置空缓存
            stringRedisTemplate.opsForValue()
                    .set(CACHE_SHOP_KEY+id,"", CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //保存到redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    /**
     * 逻辑锁解决缓存击穿
     * @param id
     * @return
     */
    private boolean tryLock(Long id){
        return BooleanUtil.isTrue(stringRedisTemplate.opsForValue().setIfAbsent(
                LOCK_SHOP_KEY+id,
                "",
                RedisConstants.LOCK_SHOP_TTL,
                TimeUnit.SECONDS
        ));
    }

    private boolean unLock(Long id){
        return BooleanUtil.isTrue(stringRedisTemplate.delete(LOCK_SHOP_KEY+id));
    }

    //获取线程池
    private static final ExecutorService CACHE_THREAD_POOL= Executors.newFixedThreadPool(10);
    /**
     * 逻辑锁解决缓存击穿问题
     * @param id
     * @return
     */
    public Shop queryWithExpireTime(Long id) {
        //1.查询redis是否存在店铺信息
        String sp = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2.不存在返回,说明不是热门商品
        if(StrUtil.isBlank(sp)){
            return null;
        }
        //3.数据不为空
        RedisData data = BeanUtil.toBean(sp, RedisData.class);
        Shop shop = BeanUtil.toBean(data.getData(), Shop.class);
        //4.命中判断缓存是否过期未过期,返回
        if(data.getExpireTime() != null && data.getExpireTime().isAfter(LocalDateTime.now())){
            return shop;
        }
        //5.过期则尝试获取互斥锁对象
        boolean lock = tryLock(id);
        //6.获取成功则开启当独线程访问数据库并修改缓存
        if(lock){
            CACHE_THREAD_POOL.submit(()->{
                try {
                    saveShop2Redis(id,30L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unLock(id);
                }
            });
        }
        //7.默认返回
        return shop;
    }
    public void saveShop2Redis(Long id,Long expireSeconds) {
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    /**
     * 更新店铺信息
     * @param shop 店铺信息
     * @return 无
     */
    @Transactional
    @Override
    public void updateShop(Shop shop) {
        // 更新数据库
        updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
    }
}
