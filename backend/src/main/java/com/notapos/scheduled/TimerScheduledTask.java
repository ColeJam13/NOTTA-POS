package com.notapos.scheduled;

import com.notapos.service.OrderItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background job that automatically locks expired order items.
 * 
 * This is the heart of the 15-second delay timer feature.
 * Runs every second to check for items where the delay timer has expired,
 * then locks them and fires them to the kitchen/bar.
 * 
 * Flow:
 * 1. Server adds items to order (status = "draft")
 * 2. Server clicks "Send" (status = "pending", timer starts)
 * 3. This job checks every second for expired timers
 * 4. When timer expires: locks item, fires to kitchen (status = "fired")
 * 
 * @author CJ
 */

@Component
public class TimerScheduledTask {
    
    private final OrderItemService orderItemService;

    @Autowired
    public TimerScheduledTask(OrderItemService orderItemService) {
        this.orderItemService = orderItemService;
    }

    @Scheduled(fixedRate = 1000, initialDelay = 5000)
    public void checkExpiredTimers() {
        orderItemService.lockAndSendExpiredItems();
    }
}
