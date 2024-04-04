package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author xuxin
 * @since 2024-04-03
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        RedisData redisData = new RedisData(LocalDateTime.now().plusSeconds(expireSeconds), shop);

        // 2.写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 利用逻辑过期解决缓存击穿问题,针对热点店铺
     *
     * @param id 商铺id
     * @return 商铺实体
     */
    public Shop queryWithLogicalExpire(Long id) {
        // 1.从redis查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2.判断是否存在，若不存在则返回null
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }

        // 3.命中，需要json反序列化
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 4.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回
            return shop;
        }
        // 已过期,需要缓存重建
        String lockKey = LOCK_SHOP_KEY + id;
        boolean b = tryLock(lockKey);
        if (b) {
            // 开启独立线程实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id, 20L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        // 返回过期的信息
        return shop;
    }

    @Override
    public Result queryById(Long id) {
        // 缓存击穿解决办法(逻辑过期)
        Shop shop = queryWithLogicalExpire(id);
        if (shop == null) {
            return Result.fail("店铺不存在!");
        }
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能位空！");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
