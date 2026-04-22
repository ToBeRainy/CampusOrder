package com.campusOrder.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campusOrder.dto.Result;
import com.campusOrder.entity.VoucherOrder;
import com.campusOrder.mapper.VoucherOrderMapper;
import com.campusOrder.service.ISeckillVoucherService;
import com.campusOrder.service.IVoucherOrderService;
import com.campusOrder.utils.RedisIdWorker;
import com.campusOrder.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private static final Integer ORDER_STATUS_UNPAID = 1;
    private static final Integer ORDER_STATUS_PAID = 2;
    private static final Integer ORDER_STATUS_CANCELED = 4;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${campus-order.kafka.order-create-topic:campus-order-create}")
    private String orderCreateTopic;

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        Long executeResult = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        int resultCode = executeResult == null ? -1 : executeResult.intValue();
        if (resultCode != 0) {
            return Result.fail(resultCode == 1 ? "库存不足" : "不能重复下单");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setPayType(1);
        voucherOrder.setStatus(ORDER_STATUS_UNPAID);
        voucherOrder.setVersion(0);

        kafkaTemplate.send(orderCreateTopic, String.valueOf(orderId), JSONUtil.toJsonStr(voucherOrder));
        return Result.ok(orderId);
    }

    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = redisLock.tryLock();
        if (!isLock) {
            log.error("不允许重复下单, userId={}", userId);
            return;
        }

        try {
            int count = lambdaQuery()
                    .eq(VoucherOrder::getUserId, userId)
                    .eq(VoucherOrder::getVoucherId, voucherId)
                    .count();
            if (count > 0) {
                log.error("用户重复下单, userId={}, voucherId={}", userId, voucherId);
                return;
            }

            boolean stockSuccess = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();
            if (!stockSuccess) {
                log.error("库存不足, voucherId={}", voucherId);
                return;
            }

            voucherOrder.setStatus(ORDER_STATUS_UNPAID);
            if (voucherOrder.getPayType() == null) {
                voucherOrder.setPayType(1);
            }
            if (voucherOrder.getVersion() == null) {
                voucherOrder.setVersion(0);
            }
            save(voucherOrder);
        } finally {
            redisLock.unlock();
        }
    }

    @Override
    @Transactional
    public Result payVoucherOrder(Long orderId) {
        VoucherOrder voucherOrder = getById(orderId);
        if (voucherOrder == null) {
            return Result.fail("订单不存在");
        }
        if (!Objects.equals(voucherOrder.getStatus(), ORDER_STATUS_UNPAID)) {
            return Result.fail("订单状态不是未支付，无法支付");
        }

        voucherOrder.setStatus(ORDER_STATUS_PAID);
        voucherOrder.setPayTime(LocalDateTime.now());
        boolean success = updateById(voucherOrder);
        if (!success) {
            return Result.fail("订单状态已变化，请刷新后重试");
        }
        return Result.ok();
    }

    @Scheduled(cron = "0 * * * * ?")
    public void closeTimeoutOrders() {
        LocalDateTime timeoutLine = LocalDateTime.now().minusMinutes(15);
        List<VoucherOrder> timeoutOrders = lambdaQuery()
                .eq(VoucherOrder::getStatus, ORDER_STATUS_UNPAID)
                .lt(VoucherOrder::getCreateTime, timeoutLine)
                .last("limit 200")
                .list();
        for (VoucherOrder timeoutOrder : timeoutOrders) {
            tryCancelOrder(timeoutOrder);
        }
    }

    private void tryCancelOrder(VoucherOrder voucherOrder) {
        if (!Objects.equals(voucherOrder.getStatus(), ORDER_STATUS_UNPAID)) {
            return;
        }
        VoucherOrder updateEntity = new VoucherOrder();
        updateEntity.setId(voucherOrder.getId());
        updateEntity.setVersion(voucherOrder.getVersion());
        updateEntity.setStatus(ORDER_STATUS_CANCELED);
        boolean success = updateById(updateEntity);
        if (success) {
            log.info("订单超时取消成功, orderId={}", voucherOrder.getId());
        }
    }
}
