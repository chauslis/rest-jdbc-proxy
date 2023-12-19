package com.syv.RestJdbcProxy.init;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class AliasFiles {

    @Value("${json.folder.path}")
    private String folderPath;

    @Bean
    public Map<String, AliasConfig> readJsonFiles() {
        Map<String, AliasConfig> resultMap = new HashMap<>();
        File folder = new File(folderPath);

        if (folder.isDirectory()) {
            File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));

            if (files != null) {
                for (File file : files) {
                    try {
                        System.out.println(file.getName());
                        ObjectMapper objectMapper = new ObjectMapper();
                        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
                        AliasConfig aliasConfig = objectMapper.readValue(file, AliasConfig.class);
                        String fileName = file.getName();
                        int dotIndex = fileName.lastIndexOf('.');
                        if (dotIndex > 0) {
                            fileName = fileName.substring(0, dotIndex);
                        }
                        resultMap.put(fileName, aliasConfig);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return resultMap;
    }
}
