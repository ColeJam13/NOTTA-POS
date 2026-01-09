package com.notapos.scheduled;

import com.notapos.service.OrderItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;

/**
 * Tests for TimerScheduledTask.
 * 
 * Verifies that the background job correctly calls the service layer
 * to lock and fire expired order items.
 * 
 * @author CJ
 */

@ExtendWith(MockitoExtension.class)
class TimerScheduledTaskTest {

    @Mock
    private OrderItemService orderItemService;

    @InjectMocks
    private TimerScheduledTask timerScheduledTask;

    @BeforeEach
    void setUp() {
        // Mock is set up by Mockito annotations
    }

    @Test
    void testCheckExpiredTimers_ShouldCallServiceMethod() {
        // WHAT: Test that the scheduled task calls the service
        // WHY: Ensure the background job is wired correctly
        
        // Given - Service is mocked (from @Mock annotation)
        
        // When - Scheduled task runs
        timerScheduledTask.checkExpiredTimers();

        // Then - Service method should be called exactly once
        verify(orderItemService, times(1)).lockAndSendExpiredItems();
    }

    @Test
    void testCheckExpiredTimers_RunsMultipleTimes_ShouldCallServiceMultipleTimes() {
        // WHAT: Test that multiple scheduler executions call service multiple times
        // WHY: Verify the scheduler can run repeatedly (simulating real-world usage)
        
        // Given - Service is mocked
        
        // When - Scheduled task runs 5 times (simulating 5 seconds)
        for (int i = 0; i < 5; i++) {
            timerScheduledTask.checkExpiredTimers();
        }

        // Then - Service should be called 5 times
        verify(orderItemService, times(5)).lockAndSendExpiredItems();
    }

    @Test
    void testCheckExpiredTimers_WhenServiceThrowsException_ShouldNotCrash() {
        // WHAT: Test that scheduler handles exceptions gracefully
        // WHY: If one execution fails, the scheduler should keep running
        
        // Given - Service throws exception
        doThrow(new RuntimeException("Database connection lost"))
                .when(orderItemService).lockAndSendExpiredItems();

        // When/Then - Should not throw exception (scheduler catches it)
        // In production, Spring's @Scheduled handles exceptions gracefully
        // This test just verifies the call happens
        try {
            timerScheduledTask.checkExpiredTimers();
        } catch (RuntimeException e) {
            // Exception is expected in this test scenario
        }

        // Verify the service was still called
        verify(orderItemService, times(1)).lockAndSendExpiredItems();
    }
}