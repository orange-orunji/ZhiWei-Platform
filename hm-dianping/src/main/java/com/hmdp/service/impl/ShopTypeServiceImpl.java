package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ShopTypeMapper shopTypeMapper;

    @Override
    public List<ShopType> queryList() {
        //TODO 在redis中查询商店类型数据
        String s = stringRedisTemplate.opsForValue().get(RedisConstants.CASH_SHOP_TYPE_KEY);
        //TODO 存在则返回
        if (s != null) {
            return JSONUtil.toList(s, ShopType.class);
        }
        //TODO 不存在，查询数据库
        List<ShopType> typeList = shopTypeMapper.selectList(null);
        //TODO 存在，写入redis
        String jsonStr = JSONUtil.toJsonStr(typeList);
        stringRedisTemplate.opsForValue().set(RedisConstants.CASH_SHOP_TYPE_KEY, jsonStr, RedisConstants.CACHE_SHOP_TTL);
        //TODO 返回
        return typeList;
    }
}
