package com.hmdp.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import com.hmdp.rabbitmq.MQSender;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private MQSender mqSender;

    //lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    @Override
    public Result sekillVoucher(Long voucherId) {
        //1.执行lua脚本
        Long userId = UserHolder.getUser().getId();

        Long r = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        System.out.println(r);
        //2.判断结果为0
        int result = r.intValue();
        if (result != 0) {
            //2.1不为0代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "该用户重复下单");
        }
        //2.2为0代表有购买资格,将下单信息保存到阻塞队列

        //2.3创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //2.4订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //2.5用户id
        voucherOrder.setUserId(userId);
        //2.6代金卷id
        voucherOrder.setVoucherId(voucherId);

        //2.7将信息放入MQ中
        mqSender.sendSeckillMessage(JSON.toJSONString(voucherOrder));

        //2.7 返回订单id
        return Result.ok(orderId);
    }

//    @Override
//    public Result sekillVoucher(Long voucherId) {
//        //1、查询优惠券是否存在,判断秒杀是否进行
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        if(voucher.getBeginTime().isAfter(LocalDateTime.now())||voucher.getEndTime().isBefore(LocalDateTime.now())){
//            return Result.fail("不在秒杀时间范围内");
//        }
//        //2、判断库存是否充足
//        if(voucher.getStock()<1){
//            return Result.fail("库存不足");
//        }
//        //3、获取锁对象
//        Long userId = UserHolder.getUser().getId();
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        boolean isLock = lock.tryLock(1200);
//        if(!isLock){
//            //获取锁失败，返回错误和重试
//            return Result.fail("一个人只能下一单");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }
//    }
//
//    @Transactional
//    //@Override
//    public Result createVoucherOrder(Long voucherId){
//        //3、判断优惠券id和用户id在订单是否存在
//        Long userId = UserHolder.getUser().getId();
//        long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//        if(count>0){
//            return Result.fail("用户已经购买一次");
//        }
//        //4、数据库扣减库存,乐观锁实现，在扣减库存的时候判断数据库内的库存是否大于0，
//        boolean success=seckillVoucherService.update()
//                .setSql("stock=stock-1").eq("voucher_id",voucherId)
//                .gt("stock",0)
//                .update();
//        if(!success){
//            return Result.fail("库存不足");
//        }
//        //创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        long orderId =redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(UserHolder.getUser().getId());
//        voucherOrder.setVoucherId(voucherId);
//        save(voucherOrder);
//        return Result.ok(orderId);
//    }
}
