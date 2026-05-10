package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;

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
        Shop shop = queryWithMutex(id);
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        //返回
        return Result.ok(shop);
    }

    public Shop queryWithMutex(Long id) {
        //1.查询redis是否存在店铺信息
        String sp = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
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
        //再次获取缓存的对象
        sp = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        if(StrUtil.isNotBlank(sp)){
            return JSONUtil.toBean(sp, Shop.class);
        }
        //4.2判断查询到的数据是否为空字符串
        if(sp != null){
            return null;
        }
        //4.2获取失败则休眠(递归等待)
        Shop shop;
        try {
            while (!isLock){
                Thread.sleep(50);
                isLock = tryLock(id);
            }
            sp = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
            if(StrUtil.isNotBlank(sp)){
                return JSONUtil.toBean(sp, Shop.class);
            }
            //4.3不存在，查询数据库
            shop = getById(id);
            //5.店铺不存在
            if(shop == null) {
                //将空值写入redis
                stringRedisTemplate.opsForValue()
                        .set(RedisConstants.CACHE_SHOP_KEY+id,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            //6.将查询到的id信息保存到redis
            stringRedisTemplate.opsForValue()
                    .set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
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
        String sp = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
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
            stringRedisTemplate.opsForValue()
                    .set(RedisConstants.CACHE_SHOP_KEY+id,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //保存到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    /**
     * 逻辑锁解决缓存击穿
     * @param id
     * @return
     */
    private boolean tryLock(Long id){
        return BooleanUtil.isTrue(stringRedisTemplate.opsForValue().setIfAbsent(
                RedisConstants.LOCK_SHOP_KEY+id,
                "",
                RedisConstants.LOCK_SHOP_TTL,
                TimeUnit.SECONDS
        ));
    }

    private boolean unLock(Long id){
        return BooleanUtil.isTrue(stringRedisTemplate.delete(RedisConstants.LOCK_SHOP_KEY+id));
    }

    /**
     * 更新店铺信息
     * @param shop 店铺信息
     * @return 无
     */
    @Override
    public void updateShop(Shop shop) {
        // 更新数据库
        updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY+shop.getId());
    }
}
