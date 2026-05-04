package com.syv.RestJdbcProxy.init;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliasFilesTest {

    @TempDir
    Path tempDir;

    @Test
    void readJsonFilesLoadsJsonFilesByBaseName() throws Exception {
        Files.writeString(tempDir.resolve("prepared_statement.json"), """
                {
                  "operationDescriptor": {
                    "type": "PREPARED_STATEMENT",
                    "sql": "select * from customer where id = ?",
                    "inputParameters": [
                      {
                        "name": "ID",
                        "jdbcType": "BIGINT",
                        "position": 1,
                        "defaultValue": "1"
                      }
                    ]
                  }
                }
                """);
        Files.writeString(tempDir.resolve("ignore.txt"), "not json");
        AliasFiles aliasFiles = new AliasFiles();
        ReflectionTestUtils.setField(aliasFiles, "folderPath", tempDir.toString());

        Map<String, OperationConfig> result = aliasFiles.readJsonFiles();

        assertEquals(1, result.size());
        assertTrue(result.containsKey("prepared_statement"));
        assertEquals(
                "select * from customer where id = ?",
                result.get("prepared_statement").getOperationDescriptor().getSql()
        );
    }

    @Test
    void readJsonFilesRejectsDescriptorWithoutOperationDescriptor() throws Exception {
        Files.writeString(tempDir.resolve("invalid.json"), "{}");
        AliasFiles aliasFiles = new AliasFiles();
        ReflectionTestUtils.setField(aliasFiles, "folderPath", tempDir.toString());

        assertThrows(IllegalArgumentException.class, aliasFiles::readJsonFiles);
    }

    @Test
    void readJsonFilesReturnsEmptyMapForMissingFolder() {
        AliasFiles aliasFiles = new AliasFiles();
        ReflectionTestUtils.setField(aliasFiles, "folderPath", tempDir.resolve("missing").toString());

        assertEquals(Map.of(), aliasFiles.readJsonFiles());
    }
}
