package com.mentis.hrms;

import com.mentis.hrms.service.LoginThrottleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class LoginThrottleServiceTest extends BaseIntegrationTest {

    @Autowired LoginThrottleService throttle;

    @Test
    void locks_after_five_failures_and_clears_on_success() {
        String key = "10.0.0.99|test.user@example.test";
        for (int i = 0; i < 4; i++) {
            throttle.recordFailure(key);
            assertFalse(throttle.isLocked(key), "must not lock before 5 failures");
        }
        throttle.recordFailure(key);
        assertTrue(throttle.isLocked(key), "must lock at 5 failures");
        assertTrue(throttle.retryAfterSeconds(key) > 0);

        throttle.recordSuccess(key);
        assertFalse(throttle.isLocked(key), "success clears the window");
    }
}
