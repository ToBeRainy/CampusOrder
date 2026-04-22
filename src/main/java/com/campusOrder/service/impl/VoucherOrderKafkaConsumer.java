package com.campusOrder.service.impl;

import cn.hutool.json.JSONUtil;
import com.campusOrder.entity.VoucherOrder;
import com.campusOrder.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class VoucherOrderKafkaConsumer {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @KafkaListener(topics = "${campus-order.kafka.order-create-topic:campus-order-create}", groupId = "${spring.kafka.consumer.group-id:campus-order-group}")
    public void consumeVoucherOrderMessage(String message) {
        try {
            VoucherOrder voucherOrder = JSONUtil.toBean(message, VoucherOrder.class);
            voucherOrderService.createVoucherOrder(voucherOrder);
        } catch (Exception e) {
            log.error("消费下单消息失败, message={}", message, e);
            throw e;
        }
    }
}
