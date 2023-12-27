package com.syv.RestJdbcProxy;

import com.syv.RestJdbcProxy.config.SpringJdbcConfig;
import com.syv.RestJdbcProxy.service.DynamicDataService;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mockito.InjectMocks;
import org.springframework.test.context.ContextConfiguration;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(classes = {SpringJdbcConfig.class})

class DynamicDataControllerTest {
    @Mock
    private JdbcTemplate jdbcTemplate;
    @InjectMocks
    private DynamicDataService dynamicDataService;
    @Test
    void executeDynamicQuery() {
        // Arrange
        String sql = "SELECT * FROM your_table WHERE condition = ?";
        Map<String, Object> inParams = new HashMap<>();
        inParams.put("param1", "value1");

        List<Map<String, Object>> expected = new ArrayList<>();
        expected.add(Map.of("column1", "value1", "column2", "value2"));

        when(jdbcTemplate.queryForList(sql)).thenReturn(expected);

        when(jdbcTemplate.queryForList(eq(sql), any(Object[].class))).thenReturn(expected);

        // Act
        List<Map<String, Object>> result = dynamicDataService.executeDynamicQuery(sql, inParams);

        // Assert
        assertEquals(expected, result);
        verify(jdbcTemplate).queryForList(eq(sql), any(Object[].class));


    }

    @Test
    void executeAliasP() {
    }
}