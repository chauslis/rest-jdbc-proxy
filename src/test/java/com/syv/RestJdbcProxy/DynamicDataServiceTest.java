package com.syv.RestJdbcProxy;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.syv.RestJdbcProxy.config.SpringJdbcConfig;
import com.syv.RestJdbcProxy.controler.DynamicDataController;
import com.syv.RestJdbcProxy.init.AliasConfig;
import com.syv.RestJdbcProxy.service.DynamicDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/////////
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
////////


//@ExtendWith(MockitoExtension.class)
@SpringBootTest
@ContextConfiguration(classes = {SpringJdbcConfig.class})
public class DynamicDataServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DataSource dataSource;

    @Mock
    private SimpleJdbcCall simpleJdbcCall;
    @InjectMocks
    private DynamicDataService dynamicDataService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
//        dynamicDataService.setDS(dataSource);
//        mockMvc = MockMvcBuilders.standaloneSetup(dynamicDataController).build();/////
        //  DynamicDataSourceContextHolder.setDataSourceKey("DB1"); // or DB2 or DB3 or DB4 or DB5 or DB6 or DB7 or DB8 or DB9 or DB10 or DB11 or DB12 or DB13 or DB14 or DB15 or DB16 or DB17 or DB18 or DB19 or DB20 or DB21 or DB22 or DB23 or DB24 or DB25 or DB26 or DB27 or DB28 or DB29 or DB30 or DB31 or DB32 or DB33 or DB34 or DB35 or DB36 or DB37 or DB38 or DB39 or DB40 or DB41 or DB42 or DB43 or DB44 or DB45 or DB46 or DB47 or DB48 or DB49 or DB50 or DB51 or DB52 or DB53 or DB54 or DB55 or DB56 or DB57 or DB58 or DB59 or DB60 or DB61 or DB62 or DB63 or DB64 or DB65 or DB66 or DB67 or DB68 or DB69 or DB70 or DB71 or DB72 or DB73 or DB74 or DB75 or DB76 or DB77 or DB78 or DB79 or DB80 or DB81 or DB82 or DB83 or DB84 or DB85 or DB86 or DB87 or DB88 or DB89 or DB90 or DB91 or DB92 or DB93 or DB94 or DB95 or DB96 or DB97 or DB98 or DB99 or DB100 or DB101 or DB102 or DB103 or DB104 or DB105 or DB106 or DB107 or DB108 or DB109 or DB110 or DB111 or DB112 or DB113 or DB114 or DB115 or DB116 or DB117 or DB118 or DB119 or DB120 or DB121 or DB122 or DB123 or DB124 or DB125 or DB126 or DB127 or DB128 or DB129 or DB130 or DB131 or DB132 or DB133 or DB134 or DB135 or DB136 or DB137 or DB138 or DB139 or DB140 or DB141 or DB142 or DB143 or DB144 or DB145 or DB146 or DB147 or DB148 or DB149 or DB150 or DB151 or DB152 or DB153 or DB154 or DB155 or DB156 or DB157 or DB158 or DB159 or DB160 or DB161 or DB162 or DB163 or DB164 or DB165 or DB166
    }

    ////////
    @InjectMocks
    DynamicDataController dynamicDataController;
    //    @Mock
//    private DynamicDataSourceContextHolder dynamicDataSourceContextHolder; // Mock this if it's a bean
    @Mock
    private Map<String, AliasConfig> aliasConfigMap;
    private String jdbcCallDescriptor = "{\n" +
            "  \"alias\": {\n" +
            "    \"prepared-statements\": {\n" +
            "      \"sql-statement-to-prepare\": \"select * from customer where id <= ?\",\n" +
            "      \"in-param\": {\n" +
            "        \"param\": [\n" +
            "          {\n" +
            "            \"jdbc-param-name\": \"AN\",\n" +
            "            \"jdbc-param-type\": \"BIGINT\",\n" +
            "            \"jdbc-param-index\": 800,\n" +
            "            \"jdbc-param-default\": 500\n" +
            "          }\n" +
            "        ]\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";

//    @Test
//    public void testExecuteAliasP() throws Exception {
//        // Arrange
//        String aliasName = "prepared_statement";
//        Map<String, Object> parameters = new HashMap<>();
//        parameters.put("connection", "DB2");
//        parameters.put("ID", 7);
//
//        ObjectMapper objectMapper = new ObjectMapper();
//        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
//        AliasConfig aliasConfig = objectMapper.readValue(jdbcCallDescriptor, AliasConfig.class);
//
//        when(aliasConfigMap.get(anyString())).thenReturn(aliasConfig);
//        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(dynamicDataController).build();
//        // Act & Assert
//        mockMvc.perform(post("/dynpst/{aliasName}", aliasName)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(asJsonString(parameters)))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$").isNotEmpty()); // Customize as per expected response
//
//        //  verifications as needed
//    }

    // Helper method to convert objects to JSON string
    private static String asJsonString(final Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
///////


    @Test
    void testExecuteDynamicQuery() {
        String sqlQuery = "SELECT id FROM customer";
        List<Map<String, Object>> expectedResults = List.of(
                Map.of("column1", "value1", "column2", "value2")
        );
        when(jdbcTemplate.queryForList(sqlQuery)).thenReturn(expectedResults);

        List<Map<String, Object>> results = dynamicDataService.executeDynamicQuery(sqlQuery);

        assertEquals(expectedResults, results);
        verify(jdbcTemplate).queryForList(sqlQuery);
    }

    @Test
    void testExecuteDynamicQueryWithParams() {
        String sqlQuery = "SELECT id FROM customer WHERE id = ?";
        Map<String, Object> inParams = Map.of("id", 1);
        List<Map<String, Object>> expectedResults = List.of(
                Map.of("column1", "value1", "column2", "value2")
        );
        when(jdbcTemplate.queryForList(sqlQuery, inParams.get("id"))).thenReturn(expectedResults);

        List<Map<String, Object>> results = dynamicDataService.executeDynamicQuery(sqlQuery, inParams);

        assertEquals(expectedResults, results);
        verify(jdbcTemplate).queryForList(sqlQuery, inParams.get("id"));
    }


}