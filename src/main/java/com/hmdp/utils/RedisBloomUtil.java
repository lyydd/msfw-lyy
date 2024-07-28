package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class RedisBloomUtil {
    @Autowired
    private RedisTemplate redisTemplate;
    // 初始化一个布隆过滤器
    public Boolean tryInitBloomFilter(String key, long expectedInsertions, double falseProbability) {
        Boolean keyExist = redisTemplate.hasKey(key);
        if(keyExist) {
            return false;
        }
        RedisScript<Boolean> script = new DefaultRedisScript<>(bloomInitLua(), Boolean.class);
        RedisSerializer stringSerializer = redisTemplate.getStringSerializer();
        redisTemplate.execute(script, stringSerializer, stringSerializer, Collections.singletonList(key), falseProbability+"", expectedInsertions+"");
        return true;
    }
    // 添加元素
    public Boolean addInBloomFilter(String key, Object arg) {
        RedisScript<Boolean> script = new DefaultRedisScript<>(addInBloomLua(), Boolean.class);
        return (Boolean) redisTemplate.execute(script, Collections.singletonList(key), arg);
    }
    @Transactional
    // 批量添加元素
    public Boolean batchAddInBloomFilter(String key, Object... args) {
        RedisScript<Boolean> script = new DefaultRedisScript<>(batchAddInBloomLua(), Boolean.class);
        return (Boolean) redisTemplate.execute(script, Collections.singletonList(key), args);
    }
    // 查看某个元素是否是存在
    public Boolean existInBloomFilter(String key, Object arg) {
        RedisScript<Boolean> script = new DefaultRedisScript<>(existInBloomLua(), Boolean.class);
        return (Boolean) redisTemplate.execute(script, Collections.singletonList(key), arg);
    }
    // 批量查看元素是否存在
    public List batchExistInBloomFilter(String key, Object... args) {
        RedisScript<List> script = new DefaultRedisScript(batchExistInBloomLua(), List.class);
        List<Long> results = (List) redisTemplate.execute(script, Collections.singletonList(key), args);
        List<Boolean> booleanList = results.stream().map(res -> res == 1 ? true : false).collect(Collectors.toList());
        return booleanList;
    }


    private String bloomInitLua() {
        return "redis.call('bf.reserve', KEYS[1], ARGV[1], ARGV[2])";
    }
    private String addInBloomLua() {
        return "return redis.call('bf.add', KEYS[1], ARGV[1])";
    }
    private String batchAddInBloomLua() {
        StringBuilder sb = new StringBuilder();
        sb.append("for index, arg in pairs(ARGV)").append("\r\n");
        sb.append("do").append("\r\n");
        sb.append("redis.call('bf.add', KEYS[1], arg)").append("\r\n");
        sb.append("end").append("\r\n");
        sb.append("return true");
        return sb.toString();
    }
    private String existInBloomLua() {
        return "return redis.call('bf.exists', KEYS[1], ARGV[1])";
    }
    private String batchExistInBloomLua() {
        StringBuilder sb = new StringBuilder();
        sb.append("local results = {}").append("\r\n");
        sb.append("for index, arg in pairs(ARGV)").append("\r\n");
        sb.append("do").append("\r\n");
        sb.append("local exist = redis.call('bf.exists', KEYS[1], arg)").append("\r\n");
        sb.append("table.insert(results, exist)").append("\r\n");
        sb.append("end").append("\r\n");
        sb.append("return results;");
        return sb.toString();
    }
}
