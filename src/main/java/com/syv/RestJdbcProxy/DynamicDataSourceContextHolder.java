package com.syv.RestJdbcProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


//@Slf4j
public class DynamicDataSourceContextHolder {
    private static final Logger log = LoggerFactory.getLogger(DynamicDataSourceContextHolder.class);
    private static final ThreadLocal<String> contextHolder = new ThreadLocal<>();

    public static void setDataSourceKey(String dataSourceKey) {
        log.info("setDataSourceKey: {}", dataSourceKey);
        contextHolder.set(dataSourceKey);
    }

    public static String getDataSourceKey() {
        log.info("getDataSourceKey: {}", contextHolder.get());
        return contextHolder.get();
    }

    public static void clearDataSourceKey() {
        contextHolder.remove();
    }
}
