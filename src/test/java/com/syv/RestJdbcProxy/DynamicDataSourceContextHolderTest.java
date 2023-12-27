package com.syv.RestJdbcProxy;

import com.syv.RestJdbcProxy.config.DynamicDataSourceContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DynamicDataSourceContextHolderTest {

    @BeforeEach
    void setUp() {
        DynamicDataSourceContextHolder.clearDataSourceKey();
    }

    @AfterEach
    void tearDown() {
        DynamicDataSourceContextHolder.clearDataSourceKey();
    }

    @Test
    void testSetAndGetDataSourceKey() {
        String key = "myDataSourceKey";
        DynamicDataSourceContextHolder.setDataSourceKey(key);
        assertEquals(key, DynamicDataSourceContextHolder.getDataSourceKey(), "The retrieved key should match the set key");
    }

    @Test
    void testClearDataSourceKey() {
        String key = "myDataSourceKey";
        DynamicDataSourceContextHolder.setDataSourceKey(key);
        DynamicDataSourceContextHolder.clearDataSourceKey();
        assertNull(DynamicDataSourceContextHolder.getDataSourceKey(), "The data source key should be null after clear");
    }

    // Optional: Test for thread isolation if needed
    @Test
    void testThreadIsolation() throws InterruptedException {
        String keyMain = "mainKey";
        String keyThread = "threadKey";

        // Set key in main thread
        DynamicDataSourceContextHolder.setDataSourceKey(keyMain);

        Thread thread = new Thread(() -> {
            DynamicDataSourceContextHolder.setDataSourceKey(keyThread);
            assertEquals(keyThread, DynamicDataSourceContextHolder.getDataSourceKey());
        });
        thread.start();
        thread.join();

        assertEquals(keyMain, DynamicDataSourceContextHolder.getDataSourceKey(), "Key in main thread should not be affected by other threads");
    }
}