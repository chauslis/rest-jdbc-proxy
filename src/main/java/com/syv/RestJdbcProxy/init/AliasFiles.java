package com.syv.RestJdbcProxy.init;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.syv.RestJdbcProxy.service.DynamicDataService.convertStringToJdbcType;

@Configuration
public class AliasFiles {

    @Value("${json.folder.path}")
    private String folderPath;

    @Bean
    public Map<String, OperationConfig> readJsonFiles() {
        Map<String, OperationConfig> resultMap = new HashMap<>();
        File folder = new File(folderPath);
        ObjectMapper objectMapper = new ObjectMapper();

        if (folder.isDirectory()) {
            File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));

            if (files != null) {
                for (File file : files) {
                    try {
                        OperationConfig operationConfig = objectMapper.readValue(file, OperationConfig.class);
                        validate(operationConfig, file.getName());
                        String fileName = file.getName();
                        int dotIndex = fileName.lastIndexOf('.');
                        if (dotIndex > 0) {
                            fileName = fileName.substring(0, dotIndex);
                        }
                        resultMap.put(fileName, operationConfig);
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to read descriptor file: " + file.getName(), e);
                    }
                }
            }
        }

        return resultMap;
    }

    private void validate(OperationConfig operationConfig, String fileName) {
        if (operationConfig == null || operationConfig.getOperationDescriptor() == null) {
            throw new IllegalArgumentException("Missing operationDescriptor in " + fileName);
        }
        OperationConfig.OperationDescriptor descriptor = operationConfig.getOperationDescriptor();
        if (descriptor.getType() == null) {
            throw new IllegalArgumentException("Missing operationDescriptor.type in " + fileName);
        }
        if (descriptor.getType() == OperationConfig.OperationType.PREPARED_STATEMENT && isBlank(descriptor.getSql())) {
            throw new IllegalArgumentException("Prepared statement descriptor requires sql in " + fileName);
        }
        if (descriptor.getType() == OperationConfig.OperationType.CALLABLE_STATEMENT && isBlank(descriptor.getDatabaseObjectName())) {
            throw new IllegalArgumentException("Callable statement descriptor requires databaseObjectName in " + fileName);
        }
        descriptor.getInputParameters().forEach(parameter -> validateParameter(parameter, fileName));
        descriptor.getOutputParameters().forEach(parameter -> validateParameter(parameter, fileName));
    }

    private void validateParameter(OperationConfig.ParameterDescriptor parameter, String fileName) {
        if (parameter.getName() == null || parameter.getName().isBlank()) {
            throw new IllegalArgumentException("Parameter name is required in " + fileName);
        }
        if (parameter.getJdbcType() == null || parameter.getJdbcType().isBlank()) {
            throw new IllegalArgumentException("Parameter jdbcType is required in " + fileName);
        }
        if (parameter.getPosition() != null && parameter.getPosition() < 0) {
            throw new IllegalArgumentException("Parameter position must not be negative in " + fileName);
        }
        convertStringToJdbcType(parameter.getJdbcType());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
