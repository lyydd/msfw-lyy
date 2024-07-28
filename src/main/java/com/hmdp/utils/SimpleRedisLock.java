package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private StringRedisTemplate stringRedisTemplate;
    private String name;



    private static final String KEY_PREFIX="lock:";
    private static final String ID_PREFIX= UUID.randomUUID().toString(true)+"-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT=new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    public SimpleRedisLock(String name,StringRedisTemplate stringRedisTemplate ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }
    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程标识
        long threadId = Thread.currentThread().getId();
        //获取锁,超时释放
        Boolean success=stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX+name,ID_PREFIX+threadId,timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
//        //获取线程标识
//        String threadId=ID_PREFIX+Thread.currentThread().getId();
//        //获取锁里面的线程标识
//        String id=stringRedisTemplate.opsForValue().get(KEY_PREFIX+name);
//        if(threadId.equals(id)){
//            stringRedisTemplate.delete(KEY_PREFIX+name);
//        }
        //调用lua脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT
                , Collections.singletonList(KEY_PREFIX+name)
                ,ID_PREFIX+Thread.currentThread().getId());

    }
}
