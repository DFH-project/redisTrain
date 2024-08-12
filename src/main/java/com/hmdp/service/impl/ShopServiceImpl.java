package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONNull;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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


    @Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id ;
        // 1 先从redis 缓存中查询数据
        String s = stringRedisTemplate.opsForValue().get(key);
        // 2。 判断是否存在
        if (StringUtils.isNotEmpty(s)) {
            // 3. 存在，则返回
            Shop shop = JSONUtil.toBean(s,Shop.class);
            return Result.ok(shop);
        }
        // 3. 不存在，则查询数据库
        Shop shopSelect = getById(id);
        // 4. 数据库不存在，返回错误
        if (shopSelect == null ) return Result.ok();
        // 5. 存在，写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shopSelect));
        return Result.ok(shopSelect);
    }
}
