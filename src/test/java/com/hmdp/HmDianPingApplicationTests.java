package com.hmdp;

import com.hmdp.utils.RedisIDWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private RedisIDWorker redisIDWorker;

    // 定义一个 线程池
    private ExecutorService es = Executors.newFixedThreadPool(500);


    @Test
    public void idTest() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = ()->{
            for (int i = 0; i < 100; i++) {
                long id = redisIDWorker.nextId("order");
                System.out.println(id);
            };
            latch.countDown();
        };
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
    }
}
