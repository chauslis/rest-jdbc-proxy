package com.syv.RestJdbcProxy.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

    @Test
    void dataSources1BuildsDriverManagerDataSourcesWithoutOpeningConnections() {
        DynamicDataSourceConfig config = new DynamicDataSourceConfig();
        ReflectionTestUtils.setField(config, "connections", Map.of(
                "DB1", "user/password@localhost:1521/XEPDB1",
                "DB2", "other/secret@localhost:1522/XEPDB1"
        ));

        Map<String, DataSource> dataSources = config.dataSources1();

        assertEquals(2, dataSources.size());
        assertInstanceOf(DriverManagerDataSource.class, dataSources.get("DB1"));
        DriverManagerDataSource db1 = (DriverManagerDataSource) dataSources.get("DB1");
        assertEquals("user/password@localhost:1521/XEPDB1", db1.getUrl());
    }

    @Test
    void routingDataSourceUsesConfiguredTargets() {
        DynamicDataSourceConfig config = new DynamicDataSourceConfig();
        Map<String, DataSource> dataSources = Map.of(
                "DB1", new DriverManagerDataSource("jdbc:h2:mem:db1"),
                "DB2", new DriverManagerDataSource("jdbc:h2:mem:db2")
        );

        DataSource routingDataSource = config.dataSource(dataSources);

        assertNotNull(routingDataSource);
        assertEquals("DB1", DynamicDataSourceContextHolder.getDataSourceKey());
        DynamicDataSourceContextHolder.clearDataSourceKey();
    }

    @Test
    void executorServiceUsesResolvedThreadCount() throws Exception {
        DynamicDataSourceConfig config = new DynamicDataSourceConfig();
        ReflectionTestUtils.setField(config, "connections", Map.of("DB1", "user/password@localhost:1521/XEPDB1"));
        ReflectionTestUtils.setField(config, "maxThreadsPerDb", 1);
        ReflectionTestUtils.setField(config, "maxThreadsTotal", 1);

        ExecutorService executorService = config.executorService();

        assertNotNull(executorService.submit(() -> "ok").get(1, TimeUnit.SECONDS));
        executorService.shutdownNow();
    }
}
