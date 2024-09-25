package com.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

@SpringBootTest
public class NIOTest {

    private static String FROM = "E:\\IDM下载\\tensorflow_gpu-2.6.0-cp39-cp39-win_amd64.whl" ;
    private static String TO = "E:\\tensorflow_gpu-2.6.0-cp39-cp39-win_amd64.whl" ;


    static final int _1mb = 1024 * 1024 ;

    @Test
    public void testIO() throws IOException {
        long start =System.currentTimeMillis();
        FileInputStream inputStream = new FileInputStream(new File(FROM));
        FileOutputStream outputStream = new FileOutputStream(new File(TO));
        byte[] bytes=new byte[_1mb];
        while (true){
            int read = inputStream.read(bytes);
            if (read==-1) break;
            outputStream.write(bytes,0,read);
        }
        long end =System.currentTimeMillis();
        System.out.println("IO文件迁移用时："+(end-start)); //154
    }

    @Test
    public void testNIO () throws IOException{
        long start =System.currentTimeMillis();
        FileChannel fio = new FileInputStream(FROM).getChannel();
        FileChannel tp = new FileOutputStream(FROM).getChannel();
        ByteBuffer bb = ByteBuffer.allocateDirect(_1mb);
        while (true){
            int len = fio.read(bb);
            if (len == -1) break;
            bb.flip();
            tp.write(bb);
            bb.clear();
        }
        long end =System.currentTimeMillis();
        System.out.println("NIO文件迁移用时："+(end-start)); //32

    }
}
