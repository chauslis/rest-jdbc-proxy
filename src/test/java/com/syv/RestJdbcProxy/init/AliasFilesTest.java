package com.syv.RestJdbcProxy.init;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliasFilesTest {

    @TempDir
    Path tempDir;

    @Test
    void readJsonFilesLoadsJsonFilesByBaseName() throws Exception {
        Files.writeString(tempDir.resolve("prepared_statement.json"), """
                {
                  "alias": {
                    "prepared-statements": {
                      "sql-statement-to-prepare": "select * from customer where id = ?",
                      "in-param": {
                        "param": [
                          {
                            "jdbc-param-name": "ID",
                            "jdbc-param-type": "BIGINT",
                            "jdbc-param-index": 1,
                            "jdbc-param-default": "1"
                          }
                        ]
                      }
                    }
                  }
                }
                """);
        Files.writeString(tempDir.resolve("ignore.txt"), "not json");
        AliasFiles aliasFiles = new AliasFiles();
        ReflectionTestUtils.setField(aliasFiles, "folderPath", tempDir.toString());

        Map<String, AliasConfig> result = aliasFiles.readJsonFiles();

        assertEquals(1, result.size());
        assertTrue(result.containsKey("prepared_statement"));
        assertEquals(
                "select * from customer where id = ?",
                result.get("prepared_statement").getAlias().getPreparedStatementAlias().getSqlStatementToPrepare()
        );
    }

    @Test
    void readJsonFilesReturnsEmptyMapForMissingFolder() {
        AliasFiles aliasFiles = new AliasFiles();
        ReflectionTestUtils.setField(aliasFiles, "folderPath", tempDir.resolve("missing").toString());

        assertEquals(Map.of(), aliasFiles.readJsonFiles());
    }
}
