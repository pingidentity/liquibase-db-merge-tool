package com.pingcentral.custom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pingcentral.custom.model.Config;
import com.pingcentral.custom.model.ConfigDatabase;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class DatabaseManagerTest {

    private DatabaseManager dm;

    @Test
    public void testConfigCorrect01() {
        try {
            dm = new DatabaseManager("src/test/resources/config-correct-01.json");
            Config config = dm.getConfig();
            assertTrue(config.isDeleteTargetData());
            assertFalse(config.isCompareData());
            assertEquals("test mysql database", config.getSourceDatabase().getName());
            assertEquals("mysql", config.getSourceDatabase().getDbmsType());
            assertEquals("jdbc:mariadb://dbmerger.mysql.local:3306/dbmerger", config.getSourceDatabase().getJdbcUrl());
            assertEquals("root", config.getSourceDatabase().getUsername());
            assertEquals("password", config.getSourceDatabase().getPassword());
            ConfigDatabase targetDb = config.getTargetDatabases().get(0);
            assertEquals("test postgresql database", targetDb.getName());
            assertEquals("postgresql", targetDb.getDbmsType());
            assertEquals("jdbc:postgresql://dbmerger.postgres.local:5432/postgres", targetDb.getJdbcUrl());
            assertEquals("postgres", targetDb.getUsername());
            assertEquals("password", targetDb.getPassword());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testConfigCorrect02() {
        try {
            dm = new DatabaseManager("src/test/resources/config-correct-02.json");
            Config config = dm.getConfig();
            assertFalse(config.isDeleteTargetData());
            assertTrue(config.isCompareData());
            ConfigDatabase targetDb = config.getTargetDatabases().get(0);
            assertEquals(2, targetDb.getTranslateToPostgresClobObjects().size());
            assertEquals(2, targetDb.getTranslateToPostgresClobObjects().get(0).getColumns().size());
            assertEquals("table_01", targetDb.getTranslateToPostgresClobObjects().get(0).getTable());
            assertFalse(targetDb.getTranslateToPostgresClobObjects().get(0).isTranslateAll());
            assertEquals("table_02", targetDb.getTranslateToPostgresClobObjects().get(1).getTable());
            assertTrue(targetDb.getTranslateToPostgresClobObjects().get(1).isTranslateAll());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testConfigCorrect03() {
        try {
            dm = new DatabaseManager("src/test/resources/config-correct-03.json");
            Config config = dm.getConfig();
            ConfigDatabase targetDb = config.getTargetDatabases().get(0);
            assertEquals(2, targetDb.getTranslateToPostgresClobObjects().size());
            assertEquals(2, targetDb.getTranslateToPostgresClobObjects().get(0).getColumns().size());
            assertEquals("table_01", targetDb.getTranslateToPostgresClobObjects().get(0).getTable());
            assertFalse(targetDb.getTranslateToPostgresClobObjects().get(0).isTranslateAll());
            assertEquals("table_02", targetDb.getTranslateToPostgresClobObjects().get(1).getTable());
            assertTrue(targetDb.getTranslateToPostgresClobObjects().get(1).isTranslateAll());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testConfigCorrect04() {
        try {
            dm = new DatabaseManager("src/test/resources/config-correct-04.json");
            Config config = dm.getConfig();
            ConfigDatabase sourceDb = config.getSourceDatabase();
            assertTrue(sourceDb.isPostgresType());
            ConfigDatabase targetDb = config.getTargetDatabases().get(0);
            assertEquals(2, targetDb.getTranslateFromPostgresClobObjects().size());
            assertEquals(2, targetDb.getTranslateFromPostgresClobObjects().get(0).getColumns().size());
            assertEquals("table_01", targetDb.getTranslateFromPostgresClobObjects().get(0).getTable());
            assertFalse(targetDb.getTranslateFromPostgresClobObjects().get(0).isTranslateAll());
            assertEquals("table_02", targetDb.getTranslateFromPostgresClobObjects().get(1).getTable());
            assertTrue(targetDb.getTranslateFromPostgresClobObjects().get(1).isTranslateAll());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testConfigInvalid01() {
        try {
            dm = new DatabaseManager("src/test/resources/config-invalid-01.json");
            fail("Config is invalid");
        } catch (Exception e) {
            assertEquals("A target database can either include {translate_to_postgres_large_clob_object} or {translate_from_postgres_large_clob_object} but not both", e.getMessage());
        }
    }

    @Test
    public void testConfigInvalid02() {
        try {
            new DatabaseManager("src/test/resources/config-invalid-02.json");
            fail("Config is invalid");
        } catch (Exception e) {
            assertEquals("If {translate_all=true} is configured a list of columns is not supported. Check the config of target database 'test postgresql database'", e.getMessage());
        }
    }

    @Test
    public void testConfigInvalid03() {
        try {
            new DatabaseManager("src/test/resources/config-invalid-03.json");
            fail("Config is invalid");
        } catch (Exception e) {
            assertEquals("If {translate_all=false} a list of columns must be configured. Check the config of target database 'test postgresql database'", e.getMessage());
        }
    }

    @Test
    public void testConfigInvalid04() {
        try {
            new DatabaseManager("src/test/resources/config-invalid-04.json");
            fail("Config is invalid");
        } catch (Exception e) {
            assertEquals("The configuration needs to contain exactly one target database", e.getMessage());
        }
    }

    @Test
    public void testConfigInvalid05() {
        try {
            new DatabaseManager("src/test/resources/config-invalid-05.json");
            fail("Config is invalid");
        } catch (Exception e) {
            assertEquals("Database Names have to be unique. Duplicates were found", e.getMessage());
        }
    }

    @Test
    public void testConfigInvalid06() {
        try {
            new DatabaseManager("src/test/resources/config-invalid-06.json");
            fail("Config is invalid");
        } catch (Exception e) {
            assertEquals("The source (test mysql database) and target (test postgres database) database cannot be of the same dbms_type", e.getMessage());
        }
    }

    @Test
    public void testConfigInvalid07() {
        StringBuilder loggingMessage = new StringBuilder();
        Logger logger = Logger.getLogger(DatabaseManager.class.getName());
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                loggingMessage.append(record.getMessage());
            }

            @Override
            public void flush() {
                // do nothing
            }

            @Override
            public void close() throws SecurityException {
                // do nothing
            }
        });
        new DatabaseManager("src/test/resources/config-invalid-07.json");
        assertNotNull(loggingMessage);
        assertEquals(
                "It appears that the source database is of type 'postgresql' but the flag 'postgres_type=true' has not been set. Please set the flag if it is the case.",
                loggingMessage.toString()
        );
    }

    @Test
    public void testConfigInvalid08() {
        StringBuilder loggingMessage = new StringBuilder();
        Logger logger = Logger.getLogger(DatabaseManager.class.getName());
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                loggingMessage.append(record.getMessage());
            }

            @Override
            public void flush() {
                // do nothing
            }

            @Override
            public void close() throws SecurityException {
                // do nothing
            }
        });
        new DatabaseManager("src/test/resources/config-invalid-08.json");
        assertNotNull(loggingMessage);
        assertEquals(
                "It appears that the source database is not of type 'postgresql' but the flag 'postgres_type=true' has been set. Please unset the flag if it is the case.",
                loggingMessage.toString()
        );
    }
}
