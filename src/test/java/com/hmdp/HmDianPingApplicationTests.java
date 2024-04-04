package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Test
    void testSaveShop() throws InterruptedException {
        shopService.saveShop2Redis(1L, 10L);
    }

    @Test
    void test(){
        stringRedisTemplate.opsForValue().setIfAbsent("key", "1", 10, TimeUnit.SECONDS);
    }
}
