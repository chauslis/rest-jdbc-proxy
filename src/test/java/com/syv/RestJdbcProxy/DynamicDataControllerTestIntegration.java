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
            //new  OracleContainer(),
            //new  OracleContainer(),
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
            //  log.info("Started Oracle");
        });

        DynamicDataSourceContextHolder.clearDataSourceKey();
        oracleArrayList.stream().forEach(oracleContainer -> {
            log.info("Set DataSourceKey: {}", oracleContainer.getJdbcUrl());
            createTestUser(oracleContainer.getJdbcUrl(), oracleContainer.getUsername(), oracleContainer.getPassword());
            //creteUserObjects(oracleContainer.getJdbcUrl(), "GT", "GT");
            creteUserObjects(oracleContainer.getJdbcUrl(), oracleContainer.getUsername(), oracleContainer.getPassword());
        });

    }


    private static String createConnectionFromUrlLoginPassword(String jdbcUrl, String username, String password){
        String[] parts = jdbcUrl.split("@");
        return "\"" + parts[0]+username+"/"+password +  "@" + parts[1] + "\"";
    }
    private static void createTestUser(String jdbcUrl, String username, String password) {
        final String DEFAULT_SYS_USER = "sys as sysdba";
        try (//Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             Connection conn = DriverManager.getConnection(jdbcUrl, DEFAULT_SYS_USER, password);
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
                .map(i -> "\"DB" + ((Integer) (i+1)).toString() + "\":" + createConnectionFromUrlLoginPassword(oracleArrayList.get(i).getJdbcUrl(), oracleArrayList.get(i).getUsername(), oracleArrayList.get(i).getPassword()))
                .collect(Collectors.joining(","))
                + "}";

        Supplier<Object> suplier = () ->
                "{"
                        + IntStream.range(0, oracleArrayList.size())
                        .boxed()
                        .map(i -> "\"DB" + ((Integer) (i+1)).toString() + "\":" + createConnectionFromUrlLoginPassword(oracleArrayList.get(i).getJdbcUrl(), oracleArrayList.get(i).getUsername(), oracleArrayList.get(i).getPassword()))
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
                .andExpect(status().isOk());
        // Additional assertions can be added here
    }

    @Test
    public void testExecuteDynPst() throws Exception {
        String jsonParametrs = "{\n" +
                "  \"connection\": \"DB1\",\n" +
                "  \"aN\": \"123\"\n" +
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
//                        .andExpect(jsonPath("result").value("1"));
    }

}
