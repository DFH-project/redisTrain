package com.hmdp;

import com.hmdp.utils.RedisIDWorker;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
public class CountDownLatchTest {

    // 定义一个线程池
    private ExecutorService es = Executors.newFixedThreadPool(100);

    @Resource
    private RedisIDWorker redisIDWorker;


    public void idTest() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        String prefix = "key:";

        Runnable task = ()-> {
            for (int i = 0; i < 100; i++) {
                long l = redisIDWorker.nextId(prefix);
            }
            latch.countDown();
        };
        long start = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await(); //等待所有线程都执行完
        long end = System.currentTimeMillis();
    }

}
