package com.hmdp;

import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
public class CountDownLatch {


    // 定义一个线程池
    private ExecutorService executorService = Executors.newFixedThreadPool(300);



    @Test
    public void test() throws InterruptedException {
        java.util.concurrent.CountDownLatch countDownLatch = new java.util.concurrent.CountDownLatch(200);
        int n = 0 ;
        List<Integer> list = new CopyOnWriteArrayList<>();
        Runnable task = ()->{
            for (int i = 0; i < 100; i++) {
                list.add(i);
            }
            countDownLatch.countDown();
        };

        long start = System.currentTimeMillis();
        for (int i = 0; i < 200; i++) {
            executorService.submit(task);
        }
        long end = System.currentTimeMillis();
        countDownLatch.await();
        System.out.println("time = " + (end - start));
        System.out.println("n = " + list.size());


    }


}
