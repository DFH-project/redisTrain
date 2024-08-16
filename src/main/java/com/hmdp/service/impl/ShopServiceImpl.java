package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONNull;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
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


    @Override
    public Result queryById(Long id) {
        Shop shop = queryWithMutex(id);
        if (shop == null ) {
            return Result.ok("数据不存在");
        }
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result updateData(Shop shop) {
        Long id = shop.getId();
        if ( id == null )  return Result.fail("id为空");
        // 1. 先更新数据库
        updateById(shop);
        // 2. 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY +shop.getId());
        return Result.ok();
    }


    // 尝试获取锁
    private boolean tryLock(String key){
        Boolean absent = stringRedisTemplate.opsForValue().setIfAbsent(key, "", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(absent);
    }

    // 释放锁
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }


    /***
     * 缓存穿透
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id ;
        // 1 先从redis 缓存中查询数据
        String s = stringRedisTemplate.opsForValue().get(key);
        // 2。 判断是否存在
        if (StringUtils.isNotEmpty(s)) {
            // 3. 存在，则返回
            Shop shop = JSONUtil.toBean(s,Shop.class);
            return shop;
        }
        if (s != null){
            return null;
        }

        // 3. 不存在，则查询数据库
        Shop shopSelect = getById(id);
        // 4. 数据库不存在，返回错误
        if (shopSelect == null ) {
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        // 5. 存在，写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shopSelect),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shopSelect;
    }



    /***
     * 互斥锁解决缓存击穿
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id ;
        // 1 先从redis 缓存中查询数据
        String s = stringRedisTemplate.opsForValue().get(key);
        // 2。 判断是否存在
        if (StringUtils.isNotEmpty(s)) {
            System.out.println("yes");
            // 3. 存在，则返回
            Shop shop = JSONUtil.toBean(s,Shop.class);
            return shop;
        }
        // 判断是否命中空值
        if (s != null){
            return null;
        }

        // 实现缓存重建
        // 获取互斥锁
        Shop shopSelect = null;
        try {
            String lockKey = LOCK_SHOP_KEY + id;
            // 判断是否获取成功
            if (!tryLock(lockKey)){
                // 获取失败，休眠重试
                Thread.sleep(50);
                System.out.println("有锁正在赋值，睡眠中===");
                // 重试  递归
                return queryWithMutex(id);
            }
            // 获取成功，根据id查询数据库
            shopSelect = getById(id);
            // 不存在，返回错误
            if (shopSelect == null ) {
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            // 存在，写入redis
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shopSelect),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            // 释放互斥锁
            unLock(LOCK_SHOP_KEY + id);
        }
        return shopSelect;
    }


    public void saveRedisData(Long id,Long seconds){
        // 获取数据
        Shop shop = getById(id);
        // 数据存储
        RedisData redisData=new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(seconds));
        // redis 存储
        stringRedisTemplate.opsForValue().set( LOCK_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
    }

    // 定义一个线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    /***
     * 逻辑过期解决缓存击穿
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id ;
        String lockKey = LOCK_SHOP_KEY + id;
        // 1 先从redis 缓存中查询数据
        String s = stringRedisTemplate.opsForValue().get(key);
        // 2。 判断是否存在
        if (StringUtils.isEmpty(s)) {
            // 3. 默认是不会过期，所以未命中返回null
            return null;
        }

        // 判断缓存是否过期
        RedisData redisData=BeanUtil.toBean(s,RedisData.class);
        Shop shop = (Shop) redisData.getData();
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())){
            // 过期时间在当前时间之后，证明未过期
            return shop;
        }
        // 过期 需要重建缓存
        // 获取锁
        boolean b = tryLock(lockKey);
        if (b){
            // 成功则开启独立线程
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    saveRedisData(id , 10L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unLock(lockKey);
                }
            });
        }
        return shop;

    }
}
