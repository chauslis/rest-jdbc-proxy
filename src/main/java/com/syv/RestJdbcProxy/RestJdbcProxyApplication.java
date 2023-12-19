package com.syv.RestJdbcProxy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.sql.DriverManager;
import java.sql.SQLException;


@SpringBootApplication
//@ComponentScan(basePackages = {"com.syv.RestJdbcProxy", "com.syv.RestJdbcProxy.init"})
public class RestJdbcProxyApplication implements ApplicationContextAware {
	static{
	try {
		DriverManager.registerDriver (new oracle.jdbc.OracleDriver());
	} catch (SQLException e) {
		throw new RuntimeException(e);
	}
}
	ApplicationContext applicationContext;
	private static final Logger log = LoggerFactory.getLogger(RestJdbcProxyApplication.class);
	public static void main(String[] args) {
		ConfigurableApplicationContext ctx =
				SpringApplication.run(RestJdbcProxyApplication.class, args);
		RestJdbcProxyApplication app = (RestJdbcProxyApplication) ctx.getBean(RestJdbcProxyApplication.class);

	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		EntityManagerFactory entityManagerFactory = applicationContext.getBean(EntityManagerFactory.class);
		this.applicationContext = applicationContext;
		log.info("entityManagerFactory: {}", entityManagerFactory);
	}
}
