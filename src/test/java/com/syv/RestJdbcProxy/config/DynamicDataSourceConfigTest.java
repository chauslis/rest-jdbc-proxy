package com.syv.RestJdbcProxy.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamicDataSourceConfigTest {

    @Test
    void totalThreadCountUsesDatabaseCountAndConfiguredMaximum() {
        DynamicDataSourceConfig config = new DynamicDataSourceConfig();
        ReflectionTestUtils.setField(config, "connections", Map.of(
                "DB1", "jdbc:oracle:thin:user/password@localhost:1521/XEPDB1",
                "DB2", "jdbc:oracle:thin:user/password@localhost:1522/XEPDB1",
                "DB3", "jdbc:oracle:thin:user/password@localhost:1523/XEPDB1"
        ));
        ReflectionTestUtils.setField(config, "maxThreadsPerDb", 10);
        ReflectionTestUtils.setField(config, "maxThreadsTotal", 20);

        assertEquals(20, config.totalThreadCount());
    }

    @Test
    void totalThreadCountIsAtLeastOne() {
        DynamicDataSourceConfig config = new DynamicDataSourceConfig();
        ReflectionTestUtils.setField(config, "connections", Map.of());
        ReflectionTestUtils.setField(config, "maxThreadsPerDb", 0);
        ReflectionTestUtils.setField(config, "maxThreadsTotal", 0);

        assertEquals(1, config.totalThreadCount());
    }
}
