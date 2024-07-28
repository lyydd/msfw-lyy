package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        String typeKey= "cache:type";
        Long size=stringRedisTemplate.opsForList().size(typeKey);
        if(size!=null&&size!=0){
            List<String> typeJsonList = stringRedisTemplate.opsForList().range(typeKey,0,size-1);
            List<ShopType>typeList=new ArrayList<>();
            for(String typeJson:typeJsonList){
                typeList.add(JSONUtil.toBean(typeJson,ShopType.class));
            }
            return Result.ok(typeList);
        }
        List<ShopType> typeList = query().orderByAsc("sort").list();
        if (typeList==null){
            //数据库不存在数据
            return Result.fail("发生错误");
        }
        //写入redis
        List<String> typeJsonList = new ArrayList<>();
        for(ShopType shopType:typeList){
            typeJsonList.add(JSONUtil.toJsonStr(shopType));
        }
        stringRedisTemplate.opsForList().rightPushAll(typeKey,typeJsonList);
        return Result.ok(typeList);
    }
}
