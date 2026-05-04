package com.syv.RestJdbcProxy;

import com.syv.RestJdbcProxy.config.DynamicDataSourceContextHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.OracleContainer;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class DynamicDataControllerTestIntegration {

    @Autowired
    private MockMvc mockMvc;



    static ArrayList<OracleContainer> oracleArrayList = new  ArrayList<>(Arrays.asList(
            new  OracleContainer()));
    private static final Logger log = LoggerFactory.getLogger(DynamicDataControllerTestIntegration.class);

    @BeforeAll
    public static void setUp() {
        System.out.println("Run BeforeAll");
        oracleArrayList
                .stream()
                .parallel()
                .forEach(oracleContainer -> {
            oracleContainer.start();
        });

        DynamicDataSourceContextHolder.clearDataSourceKey();
        oracleArrayList.stream().forEach(oracleContainer -> {
            log.info("Set DataSourceKey: {}", oracleContainer.getJdbcUrl());
            createTestUser(oracleContainer.getJdbcUrl(), oracleContainer.getUsername(), oracleContainer.getPassword());
            creteUserObjects(oracleContainer.getJdbcUrl(), oracleContainer.getUsername(), oracleContainer.getPassword());
        });

    }


    private static String createConnectionFromUrlLoginPassword(String jdbcUrl, String username, String password){
        String[] parts = jdbcUrl.split("@");
        return "\"" + parts[0]+username+"/"+password +  "@" + parts[1] + "\"";
    }
    private static void createTestUser(String jdbcUrl, String username, String password) {
        final String DEFAULT_SYS_USER = "sys as sysdba";
        try (Connection conn = DriverManager.getConnection(jdbcUrl, DEFAULT_SYS_USER, password);
             Statement stmt = conn.createStatement()) {


            log.info (String.format("Execute SQL commands to create a new user: {%s}", "CREATE USER gt IDENTIFIED BY gt"));
            stmt.execute("CREATE USER GT IDENTIFIED BY GT");

            stmt.execute("GRANT CONNECT, RESOURCE, DBA TO GT");
            log.info (String.format("Executed SQL commands : {%s}", "GRANT CONNECT, RESOURCE, DBA TO gt"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String connections = "{"
                + IntStream.range(0, oracleArrayList.size())
                .boxed()
                .map(i -> "\"DB" + ((Integer) (i + 1)).toString() + "\":" + createConnectionFromUrlLoginPassword(oracleArrayList.get(i).getJdbcUrl(), oracleArrayList.get(i).getUsername(), oracleArrayList.get(i).getPassword()))
                .collect(Collectors.joining(","))
                + "}";

        Supplier<Object> suplier = () ->
                "{"
                        + IntStream.range(0, oracleArrayList.size())
                        .boxed()
                        .map(i -> "\"DB" + ((Integer) (i + 1)).toString() + "\":" + createConnectionFromUrlLoginPassword(oracleArrayList.get(i).getJdbcUrl(), oracleArrayList.get(i).getUsername(), oracleArrayList.get(i).getPassword()))
                        .collect(Collectors.joining(","))
                        + "}";
        registry.add("Db.connections", suplier);

    }
    private static void creteUserObjects(String jdbcUrl, String userName, String password){
        SingleConnectionDataSource ds = new SingleConnectionDataSource(jdbcUrl, userName, password, false);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
        try {

            EncodedResource encodedResource = new EncodedResource(new ClassPathResource("schema.sql"), StandardCharsets.UTF_8);
            ScriptUtils.executeSqlScript(ds.getConnection(),
                    encodedResource,
                    false,
                    false,
                    "--",
                    "/",
                    "/*",
                    "*/");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ds.destroy();
        }
    }

    @Test
    public void testExecuteDynamicQuery() throws Exception {
        String connection = "DB1";
        String sqlQuery = "SELECT * FROM customer";

        mockMvc.perform(get("/query")
                        .param("connection", connection)
                        .param("sqlQuery", sqlQuery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].ID").value(2));
    }

    @Test
    public void testExecuteDynPst() throws Exception {
        String jsonParametrs = "{\n" +
                "  \"_rjp\": {\"connectionName\": \"DB1\"},\n" +
                "  \"params\": {\"aN\": \"123\"}\n" +
                "}\n";
        mockMvc.perform(post("/dynpst/test_pkh.tst_function")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonParametrs))
                .andExpect(status().isOk());

        String expectedJson = "[{\"result\":\"1\"}]";
        mockMvc.perform(post("/dynpst/test_pkh.tst_function")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonParametrs))
                        .andExpect(status().isOk())
                        .andExpect(content().json(expectedJson));
    }

    @Test
    public void testExecutePreparedStatementAlias() throws Exception {
        String jsonParameters = "{\n" +
                "  \"_rjp\": {\"connectionName\": \"DB1\"},\n" +
                "  \"params\": {\"AN\": 7}\n" +
                "}\n";

        mockMvc.perform(post("/dynpst/prepared_statement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonParameters))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0]._rjp_connectionName").value("DB1"));
    }

    @Test
    public void testPreparedStatementAllowsBusinessConnectionParameter() throws Exception {
        String jsonParameters = "{\n" +
                "  \"_rjp\": {\"connectionName\": \"DB1\"},\n" +
                "  \"params\": {\"connection\": \"business-value\", \"AN\": 7}\n" +
                "}\n";

        mockMvc.perform(post("/dynpst/prepared_statement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonParameters))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0]._rjp_connectionName").value("DB1"));
    }

    @Test
    public void testInvalidEnvelopeReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/dynpst/prepared_statement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"params\":{\"AN\":7}}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/dynpst/prepared_statement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"_rjp\":{},\"params\":{\"AN\":7}}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/dynpst/prepared_statement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"_rjp\":{\"connectionName\":\"DB1\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testMissingAliasReturnsNotFound() throws Exception {
        String jsonParameters = "{\n" +
                "  \"_rjp\": {\"connectionName\": \"DB1\"},\n" +
                "  \"params\": {\"AN\": 7}\n" +
                "}\n";

        mockMvc.perform(post("/dynpst/missing_alias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonParameters))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testExecuteStoredProcedureWithOutParams() throws Exception {
        String jsonParameters = "{\n" +
                "  \"_rjp\": {\"connectionName\": \"DB1\"},\n" +
                "  \"params\": {\n" +
                "    \"ID\": 123,\n" +
                "    \"NAME\": \"test\",\n" +
                "    \"P\": \"INPUT p parameter value\"\n" +
                "  }\n" +
                "}\n";

        mockMvc.perform(post("/dynpst/test_pkh.proc_with_OutParam")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonParameters))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].OUT1").value("out1"))
                .andExpect(jsonPath("$[0].OUT2").value("test"))
                .andExpect(jsonPath("$[0].OUT3").value("INPUT p parameter value"));
    }

    @Test
    public void testExecuteBatchStoredFunction() throws Exception {
        String jsonParameters = "[\n" +
                "  {\"_rjp\": {\"connectionName\": \"DB1\"}, \"params\": {\"aN\": \"123\"}},\n" +
                "  {\"_rjp\": {\"connectionName\": \"DB1\"}, \"params\": {\"aN\": \"23\"}},\n" +
                "  {\"_rjp\": {\"connectionName\": \"DB1\"}, \"params\": {\"aN\": \"3\"}},\n" +
                "  {\"_rjp\": {\"connectionName\": \"DB1\"}, \"params\": {\"aN\": \"321\"}}\n" +
                "]\n";

        mockMvc.perform(post("/batch/test_pkh.tst_function")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonParameters))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].result").value("1"))
                .andExpect(jsonPath("$[1].result").value("1"))
                .andExpect(jsonPath("$[2].result").value("1"))
                .andExpect(jsonPath("$[3].result").value("1"));
    }

    @Test
    public void testExecuteBatchPreparedStatement() throws Exception {
        String jsonParameters = "[\n" +
                "  {\"_rjp\": {\"connectionName\": \"DB1\"}, \"params\": {\"AN\": 3}},\n" +
                "  {\"_rjp\": {\"connectionName\": \"DB1\"}, \"params\": {\"AN\": 7}}\n" +
                "]\n";

        mockMvc.perform(post("/batch/prepared_statement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonParameters))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8));
    }

    @Test
    public void testAsyncBatchTaskLifecycle() throws Exception {
        String jsonParameters = "[\n" +
                "  {\"_rjp\": {\"connectionName\": \"DB1\"}, \"params\": {\"aN\": \"123\"}},\n" +
                "  {\"_rjp\": {\"connectionName\": \"DB1\"}, \"params\": {\"aN\": \"23\"}},\n" +
                "  {\"_rjp\": {\"connectionName\": \"DB1\"}, \"params\": {\"aN\": \"3\"}},\n" +
                "  {\"_rjp\": {\"connectionName\": \"DB1\"}, \"params\": {\"aN\": \"321\"}}\n" +
                "]\n";

        MvcResult startResult = mockMvc.perform(post("/startAsyncTask/test_pkh.tst_function")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonParameters))
                .andExpect(status().isOk())
                .andReturn();
        String taskId = startResult.getResponse().getContentAsString();

        mockMvc.perform(get("/tasksStatus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['" + taskId + "']").exists());

        waitForTaskCompletion(taskId);

        mockMvc.perform(get("/taskResult/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].result").value("1"));

        mockMvc.perform(get("/taskRemove/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(content().string("Task is completed"));

        mockMvc.perform(get("/taskStatus/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(content().string("Task not found"));
    }

    private void waitForTaskCompletion(String taskId) throws Exception {
        for (int i = 0; i < 20; i++) {
            MvcResult statusResult = mockMvc.perform(get("/taskStatus/{taskId}", taskId))
                    .andExpect(status().isOk())
                    .andReturn();
            if ("Task is completed".equals(statusResult.getResponse().getContentAsString())) {
                return;
            }
            Thread.sleep(250);
        }
        mockMvc.perform(get("/taskStatus/{taskId}", taskId))
                .andExpect(content().string("Task is completed"));
    }

}
