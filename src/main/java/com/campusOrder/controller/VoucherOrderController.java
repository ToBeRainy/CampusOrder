package com.campusOrder.controller;


import com.campusOrder.dto.Result;
import com.campusOrder.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  鍓嶇鎺у埗鍣?
 * </p>
 *
 * @author 铏庡摜
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    @PostMapping("pay/{id}")
    public Result payVoucherOrder(@PathVariable("id") Long orderId) {
        return voucherOrderService.payVoucherOrder(orderId);
    }
}

