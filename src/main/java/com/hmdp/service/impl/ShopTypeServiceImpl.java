package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

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

    @Override
    public Result getList() {
        // 1. 从redis中获取商铺类型列表
        String key = "shop:type:";
        List<String> range = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (range != null && !range.isEmpty()){
            List<ShopType> list = range.stream().map(type -> {
                ShopType shopType = JSONUtil.toBean(type,ShopType.class);
                return shopType;
            }).sorted((o1, o2) -> o1.getSort()-o2.getSort()).collect(Collectors.toList());
            return Result.ok(list);
        }
        //2 redis 中不存在，先查数据库
        List<ShopType> list = query().orderByAsc("sort").list();

        //3. 将数据缓存到redis中
        if (list.size()>0){
            list.forEach(type -> {
                stringRedisTemplate.opsForList().rightPush(key, JSONUtil.toJsonStr(type));
                stringRedisTemplate.expire(key,CACHE_SHOP_TTL, TimeUnit.MINUTES);
            });
        }else{
            stringRedisTemplate.opsForValue().set(key, Collections.emptyList().toString(),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }
        return Result.ok(list);
    }
}
