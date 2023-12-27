package com.syv.RestJdbcProxy.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

@Configuration
//@ComponentScan("com.baeldung.jdbc")
public class SpringJdbcConfig {
//    @Bean//(name = "primaryDataSource")
//    @Primary
//    public DataSource OracleDataSource() {
//        DriverManagerDataSource dataSource = new DriverManagerDataSource();
//        dataSource.setDriverClassName("oracle.jdbc.OracleDriver");
//        dataSource.setUrl("jdbc:oracle:thin:@//localhost:11521/XEPDB1");
//        dataSource.setUsername("gt");
//        dataSource.setPassword("gt");
//
//        return dataSource;
//    }
}