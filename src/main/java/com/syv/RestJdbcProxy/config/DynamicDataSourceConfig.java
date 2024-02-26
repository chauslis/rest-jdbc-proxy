package com.syv.RestJdbcProxy.config;

import com.syv.RestJdbcProxy.service.DynamicDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.scheduling.annotation.EnableAsync;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class DynamicDataSourceConfig {
    private static final Logger log = LoggerFactory.getLogger(DynamicDataService.class);

    @Autowired
    private Environment env;
    @Value("#{${Db.connections}}")
    private Map<String,String> connections;

    @Value("${app.thread.number}")
    public  int ThreadNumber;

    private String[] parseConnString(String connectionString) {
        String[] result = new String[3];
        String[] parts = connectionString.split("@");
        String[] loginAndPassword = parts[0].split("/");
        result[0] = loginAndPassword[0];
        result[1] = loginAndPassword[1];
        result[2] = parts[1];
        return result;
    }

    public DataSource creteDataSource(String url, String username, String password, String driverClassName) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        return new HikariDataSource(config);
    }

    @Bean
    public Map<String, DataSource> dataSources() {
        Map<String, DataSource> dataSources = new HashMap<>();
        for (Map.Entry<String, String> entry : connections.entrySet()) {
            String[] conn = parseConnString(entry.getValue());
            log.info (String.format("key: {%s}, value: {%s}", entry.getKey(), conn[0]+"/"+conn[1] + "://" + conn[2]));
            HikariConfig config = new HikariConfig();
            //todo: add driverClassName into parameters
            // config.setDriverClassName("org.postgresql.Driver");
            config.setJdbcUrl(entry.getValue());
            //todo: add min/max pool size paramets
             config.setMaximumPoolSize(20);
            // config.setDriverClassName("org.postgresql.Driver");
            dataSources.put(entry.getKey(), new HikariDataSource(config));
        }
        return dataSources;
    }
  //  @Bean
    public Map<String, DataSource> dataSources1() {
        Map<String, DataSource> dataSources = new HashMap<>();
        for (Map.Entry<String, String> entry : connections.entrySet()) {
            String[] conn = parseConnString(entry.getValue());
            log.info (String.format("key: {%s}, value: {%s}", entry.getKey(), conn[0]+"/"+conn[1] + "://" + conn[2]));
            DriverManagerDataSource dataSource = new DriverManagerDataSource();

            dataSource.setUrl(entry.getValue());
            dataSources.put(entry.getKey(), dataSource);
        }
      //  datasources = dataSources;
        return dataSources;
    }

    @Bean
    public DataSource dataSource(Map<String, DataSource> datasources) {
        Map<Object, Object> targetDataSources = new HashMap<>(datasources);
        AbstractRoutingDataSource routingDataSource = new AbstractRoutingDataSource() {
            @Override
            protected String determineCurrentLookupKey() {
                return detemineDynamicDataSourceKey();
            }

        };
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.afterPropertiesSet();
        DynamicDataSourceContextHolder.setDataSourceKey("DB1");//todo: remove hardcode: for initial datasource
        return routingDataSource;
    }


        private String detemineDynamicDataSourceKey() {
            return  DynamicDataSourceContextHolder.getDataSourceKey();
        }

    @Bean
    public ExecutorService executorService() {

        return Executors.newFixedThreadPool(ThreadNumber); // Customize as needed
    }
    }

