package com.syv.RestJdbcProxy;

import com.syv.RestJdbcProxy.config.DynamicDataSourceContextHolder;
import com.syv.RestJdbcProxy.config.SpringJdbcConfig;
//import org.junit.BeforeClass;
import com.syv.RestJdbcProxy.controler.DynamicDataController;
import com.syv.RestJdbcProxy.service.DynamicDataService;
import org.apache.tomcat.util.file.ConfigurationSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;


import static java.lang.System.out;

@SpringBootTest
//@TestConfiguration
//@AutoConfigureWebMvc
//@TestPropertySource(properties = {"Db.connections=getConnections"})
//@Testcontainers
@ContextConfiguration(classes = {OracleTest.class})
@TestPropertySource(locations = "classpath:application.properties")
public class OracleTest {

//    @Value("#{${Db.connections1}}")
//    private Map<String,String> connections1;


//    @Autowired
//    TestRestTemplate restTemplate;
    private static final Logger log = LoggerFactory.getLogger(OracleTest.class);
    static{
        try {
            DriverManager.registerDriver (new oracle.jdbc.OracleDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    //private static final OracleContainer oracle = new OracleContainer().withUsername("gt").withPassword("gt").withInitScript("schema.sql");

    private static final ArrayList<OracleContainer> oracleArrayList = new  ArrayList<>(Arrays.asList(new  OracleContainer()
            , new  OracleContainer()
    ));

//    OracleContainer oracle1 = new OracleContainer("gvenzl/oracle-xe:18.4.0-slim")
//            .withUsername("system")
//            .withPassword("manager");


    @BeforeAll
    public static void setUp() {
        System.out.println("Run BeforeAll");
        oracleArrayList.stream()
                .parallel()
                .forEach(oracleContainer -> {
            oracleContainer.start();
            log.info("Started Oracle");
        });
        //oracle.start();
        log.info("Started Oracles");

        DynamicDataSourceContextHolder.clearDataSourceKey();
        oracleArrayList.stream().forEach(oracleContainer -> {
            log.info("Set DataSourceKey: {}", oracleContainer.getJdbcUrl());
            createTestUser(oracleContainer.getJdbcUrl(), oracleContainer.getUsername(), oracleContainer.getPassword());
            creteUserObjects(oracleContainer.getJdbcUrl(), "GT", "GT");
        });

    }

    private static String createConnectionFromUrlLoginPassword(String jdbcUrl, String username, String password){
        String[] parts = jdbcUrl.split("@");
        return parts[0]+username+"/"+password +  "@" + parts[1];
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
    public void testTest() throws IOException {
        out.println("test");

        oracleArrayList.stream().forEach(oc ->
                System.out.println(oc.getJdbcUrl())
        );
        oracleArrayList.stream().forEach(oracleContainer -> {
            try {
                Connection conn = DriverManager.getConnection(oracleContainer.getJdbcUrl(), "GT", "GT");// because CREATE USER gt IDENTIFIED BY gt means uppercase
                Statement stmt = conn.createStatement();
                out.println("User Creted");
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        });

        out.println("End test");
    }
}
