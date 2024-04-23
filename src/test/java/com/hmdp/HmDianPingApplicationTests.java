package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testSaveShop() {
        Shop shop = shopService.getById(2L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 2L, shop, 10L, TimeUnit.SECONDS);
    }

    private final ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdGenerator() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("cost time: " + (end - begin));
    }

    @Test
    void loadShopData() {
        shopService.list().stream()
                .collect(Collectors.groupingBy(Shop::getTypeId))
                .forEach((k, v) -> {
                    List<RedisGeoCommands.GeoLocation<String>> geoLocation = new ArrayList<>(v.size());
                    v.forEach(ele -> geoLocation.add(new RedisGeoCommands.GeoLocation<>(
                            ele.getId().toString(),
                            new Point(ele.getX(), ele.getY()))));
                    String key = SHOP_GEO_KEY + k;
                    stringRedisTemplate.opsForGeo().add(key, geoLocation);
                });
    }
}
