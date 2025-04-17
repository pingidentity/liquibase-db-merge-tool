package com.pingcentral.custom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pingcentral.custom.model.*;
import org.postgresql.PGConnection;
import org.postgresql.largeobject.LargeObject;
import org.postgresql.largeobject.LargeObjectManager;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

public class DatabaseManager {

    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LiquibaseChangelogProcessor liquibaseChangelogProcessor;
    private Config config;
    private Connection sourceConnection, targetConnection;

    /**
     * Initializes the tool
     *
     * @param filePath Location of the configuration file. Either an absolute path or a location within the tools jar file
     */
    public DatabaseManager(String filePath) {
        String fromResourcePrefix = null;
        try {
            Config config;
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            try (InputStream is = loader.getResourceAsStream(filePath)) {
                config = MAPPER.readValue(is, Config.class);
                fromResourcePrefix = filePath.substring(0, filePath.lastIndexOf("/"));
            } catch (Exception e) {
                config = MAPPER.readValue(new FileReader(filePath), Config.class);
            }
            validateConfig(config);
        } catch (IOException e) {
            throw new RuntimeException(String.format("The configuration file could not be found: %s", e.getMessage()));
        }
        liquibaseChangelogProcessor = new LiquibaseChangelogProcessor(
                config.getChangeLogFile(),
                config.getSourceDatabase().getDbmsType(),
                config.getTargetDatabases().get(0).getDbmsType(),
                fromResourcePrefix
        );
    }

    /**
     * A facade to {@link LiquibaseChangelogProcessor#getChangelogAsJson()}
     *
     * @return
     * @throws JsonProcessingException
     */
    public String getChangelogAsJson() throws JsonProcessingException {
        return liquibaseChangelogProcessor.getChangelogAsJson();
    }

    /**
     * A facade to {@link LiquibaseChangelogProcessor#processLiquibaseFiles()}
     *
     * @return
     * @throws JsonProcessingException
     */
    public void processLiquibaseFiles() {
        liquibaseChangelogProcessor.processLiquibaseFiles();
    }

    /**
     * Initializes connections to the source and target databases
     *
     * @param sourceDb true to connect to the source database
     * @param targetDb true to connect to the target database
     * @throws Exception
     */
    public void initDb(boolean sourceDb, boolean targetDb) throws Exception {
        if (sourceDb) {
            sourceConnection = DriverManager.getConnection(
                    config.getSourceDatabase().getJdbcUrl(),
                    config.getSourceDatabase().getUsername(),
                    config.getSourceDatabase().getPassword()
            );
            LOGGER.info(String.format("Connection to source database %s created", config.getSourceDatabase().getName()));
        }
        if (targetDb) {
            // remember, only one target database is supported at this point in time
            ConfigDatabase targetDbms = config.getTargetDatabases().get(0);
            targetConnection = DriverManager.getConnection(
                    targetDbms.getJdbcUrl(),
                    targetDbms.getUsername(),
                    targetDbms.getPassword()
            );
            LOGGER.info(String.format("Connection to target database %s created", config.getTargetDatabases().get(0).getName()));
        }
    }

    /**
     * Closes the connection to the source and target dbms
     *
     * @param sourceDb true to close the source database connection
     * @param targetDb true to close the target database connection
     * @throws Exception
     */
    public void closeDb(boolean sourceDb, boolean targetDb) throws Exception {
        if (sourceDb) {
            sourceConnection.close();
            LOGGER.info(String.format("Connection to source database %s closed", config.getSourceDatabase().getName()));
        }
        if (targetDb) {
            targetConnection.close();
            LOGGER.info(String.format("Connection to target database %s closed", config.getTargetDatabases().get(0).getName()));
        }
    }

    /**
     * Deletes all data from the target databases. This is useful to avoid any data conflicts when data is copied from the source to the target databases
     */
    public void deleteDataFromTargetDbms() {
        if (config.isDeleteTargetData()) {
            LOGGER.info(String.format("Deleting data from target database %s", config.getTargetDatabases().get(0).getName()));
            Map<String, List<TableColumnTypeSelect>> tt = ((TreeMap) liquibaseChangelogProcessor.getTargetTables()).descendingMap();
            for (List<TableColumnTypeSelect> table : tt.values()) {
                for (TableColumnTypeSelect next : table) {
                    try {
                        Statement stmt = targetConnection.createStatement();
                        stmt.executeUpdate(next.getDeleteStmt());
                        stmt.close();
                    } catch (Exception e) {
                        LOGGER.warning(e.getMessage());
                    }
                }
            }
            LOGGER.info("DONE - Deleting target database data completed");
        }
    }

    /**
     * Copies all data from the source database to the target databases
     */
    public void transferFromDbToDb() {

        /*
          This code has a complex data structure to keep the order of tables as they were created
         */

        LOGGER.info(String.format("Transferring data from %s to %s", config.getSourceDatabase().getName(), config.getTargetDatabases().get(0).getName()));
        for (List<TableColumnTypeSelect> nextSourceTables : liquibaseChangelogProcessor.getSourceTables().values()) {
            for (TableColumnTypeSelect nextSourceTable : nextSourceTables) {
                for (List<TableColumnTypeSelect> nextTargetTables : liquibaseChangelogProcessor.getTargetTables().values()) {
                    for (TableColumnTypeSelect nextTargetTable : nextTargetTables) {
                        if (nextTargetTable.getTableName().equalsIgnoreCase(nextSourceTable.getTableName())) {
                            try {
                                Statement sourceStmt = sourceConnection.createStatement();
                                PreparedStatement targetStmt = targetConnection.prepareStatement(nextTargetTable.getInsertStmt());
                                ResultSet sourceResultSet = sourceStmt.executeQuery(nextSourceTable.getSelectStmt());
                                int rowcount = 0;
                                while (sourceResultSet.next()) {
                                    try {
                                        sourceConnection.setAutoCommit(false);
                                        targetConnection.setAutoCommit(false);
                                        for (ColumnType next : nextSourceTable.getColumnTypes()) {
                                            String columnName = next.getColumnName();
                                            String columnType = next.getColumnType().toLowerCase();
                                            if (columnType.startsWith("int")) {
                                                targetStmt.setInt(next.getIndex(), sourceResultSet.getInt(columnName));
                                            } else if (columnType.startsWith("tinyint")) {
                                                targetStmt.setShort(next.getIndex(), sourceResultSet.getShort(columnName));
                                            } else if (columnType.startsWith("number")) {
                                                targetStmt.setBigDecimal(next.getIndex(), sourceResultSet.getBigDecimal(columnName));
                                            } else if (columnType.startsWith("bigint")) {
                                                targetStmt.setLong(next.getIndex(), sourceResultSet.getLong(columnName));
                                            } else if (columnType.startsWith("boolean")) {
                                                targetStmt.setBoolean(next.getIndex(), sourceResultSet.getBoolean(columnName));
                                            } else if (columnType.startsWith("timestamp")) {
                                                targetStmt.setTimestamp(next.getIndex(), sourceResultSet.getTimestamp(columnName));
                                            } else if (columnType.startsWith("date")) {
                                                targetStmt.setDate(next.getIndex(), sourceResultSet.getDate(columnName));
                                            } else if (columnType.startsWith("clob") || columnType.startsWith("varchar") || columnType.startsWith("char") || columnType.startsWith("text") || columnType.startsWith("uuid")) {
                                                String clobValue = null;
                                                Set<String> fromColumns = config.getTargetDatabases().get(0).getFromPostgresLargeObjectColumnsAsMap().get(nextTargetTable.getTableName());
                                                if (fromColumns != null && fromColumns.contains(columnName)) {
                                                    // this is an oid from PostgreSQL
                                                    long oid = sourceResultSet.getLong(columnName);
                                                    if (oid > 0) { // indicates that the large object does not exist
                                                        LargeObjectManager lobj = sourceConnection.unwrap(org.postgresql.PGConnection.class).getLargeObjectAPI();
                                                        LargeObject obj = lobj.open(oid, LargeObjectManager.READ);
                                                        byte[] buf = new byte[obj.size()];
                                                        obj.read(buf, 0, obj.size());
                                                        clobValue = new String(buf);
                                                        obj.close();
                                                    }
                                                } else if (config.getSourceDatabase().isPostgresType()) {
                                                    clobValue = sourceResultSet.getString(columnName);
                                                } else {
                                                    Clob clob = sourceResultSet.getClob(columnName);
                                                    if (clob != null) {
                                                        clobValue = processClob(clob.getCharacterStream());
                                                        Set<String> toColumns = config.getTargetDatabases().get(0).getToPostgresLargeObjectColumnsAsMap().get(nextTargetTable.getTableName());
                                                        if (toColumns != null && toColumns.contains(columnName)) {
                                                            PGConnection unwrap = targetConnection.unwrap(PGConnection.class);
                                                            LargeObjectManager lobj = unwrap.getLargeObjectAPI();
                                                            long oid = lobj.createLO(LargeObjectManager.READ | LargeObjectManager.WRITE);
                                                            LargeObject obj = lobj.open(oid, LargeObjectManager.WRITE);
                                                            obj.write(clobValue.getBytes(), 0, clobValue.length());
                                                            obj.close();
                                                            clobValue = String.valueOf(oid);
                                                        }
                                                    }
                                                }
                                                targetStmt.setString(next.getIndex(), clobValue);
                                            } else if (columnType.startsWith("blob") || columnType.startsWith("byte")) {
                                                byte[] byteArray = sourceResultSet.getBytes(columnName);
                                                targetStmt.setBytes(next.getIndex(), byteArray);
                                            }
                                            targetConnection.commit();
                                            sourceConnection.commit();
                                        }
                                        targetStmt.execute();
                                        targetConnection.commit();
                                        sourceConnection.commit();
                                        targetConnection.setAutoCommit(true);
                                        sourceConnection.setAutoCommit(true);
                                        rowcount++;
                                    } catch (Exception e) {
                                        if (e.getMessage().toLowerCase().contains("duplicate")) {
                                            LOGGER.info(String.format("Duplicate entry found in table: %s", nextTargetTable.getTableName()));
                                        } else {
                                            LOGGER.warning(String.format("Table: %s, error: %s", nextTargetTable.getTableName(), e.getMessage()));
                                        }
                                        try {
                                            targetConnection.rollback();
                                        } catch (SQLException ex) {
                                            LOGGER.warning(String.format("Should not have happened ... :%s", ex.getMessage()));
                                        }
                                    }
                                }
                                sourceResultSet.close();
                                sourceStmt.close();
                                targetStmt.close();
                                if (rowcount > 0) {
                                    LOGGER.info(String.format("Target table %s updated, row count: %d", nextTargetTable.getTableName(), rowcount));
                                }
                            } catch (Exception e) {
                                LOGGER.warning(e.getMessage());
                            }
                        }
                    }
                }
            }
        }
        LOGGER.info("DONE - Transferring data completed");
    }

    /**
     * Compares all rows of all tables between the source and target database
     */
    public void compareFromDbToDb() {
        if (config.isCompareData()) {
            LOGGER.info(String.format("Comparing data between %s and %s", config.getSourceDatabase().getName(), config.getTargetDatabases().get(0).getName()));
            for (List<TableColumnTypeSelect> from : liquibaseChangelogProcessor.getSourceTables().values()) {
                for (TableColumnTypeSelect nextSourceTable : from) {
                    for (List<TableColumnTypeSelect> table : liquibaseChangelogProcessor.getTargetTables().values()) {
                        for (TableColumnTypeSelect nextTargetTable : table) {
                            if (nextTargetTable.getTableName().equalsIgnoreCase(nextSourceTable.getTableName())) {
                                try {
                                    Statement sourceStmt = sourceConnection.createStatement();
                                    ResultSet sourceResultSet = sourceStmt.executeQuery(nextSourceTable.getSelectStmt());
                                    Statement targetStmt = targetConnection.createStatement();
                                    ResultSet targetResultSet = targetStmt.executeQuery(nextTargetTable.getSelectStmt());
                                    while (sourceResultSet.next()) {
                                        targetResultSet.next();
                                        try {
                                            for (ColumnType next : nextSourceTable.getColumnTypes()) {
                                                String columnName = next.getColumnName();
                                                String columnType = next.getColumnType().toLowerCase();
                                                try {
                                                    if (columnType.startsWith("varchar") || columnType.startsWith("char") || columnType.startsWith("text")) {
                                                        String fromResult = sourceResultSet.getString(columnName);
                                                        String toResult = targetResultSet.getString(columnName);
                                                        if (fromResult != null && toResult != null && !fromResult.equals(toResult)) {
                                                            LOGGER.warning(String.format("table: %s, column: %s, type: %s, fromResult: %s, toResult: %s", nextSourceTable.getTableName(), columnName, next.getColumnType(), fromResult, toResult));
                                                        }
                                                    } else if (columnType.startsWith("int")) {
                                                        int fromResult = sourceResultSet.getInt(columnName);
                                                        int toResult = targetResultSet.getInt(columnName);
                                                        if (fromResult != toResult) {
                                                            LOGGER.warning(String.format("table: %s, column: %s, type: %s, fromResult: %s, toResult: %s", nextSourceTable.getTableName(), columnName, next.getColumnType(), fromResult, toResult));
                                                        }
                                                    } else if (columnType.startsWith("tinyint")) {
                                                        short fromResult = sourceResultSet.getShort(columnName);
                                                        short toResult = targetResultSet.getShort(columnName);
                                                        if (fromResult != toResult) {
                                                            LOGGER.warning(String.format("table: %s, column: %s, type: %s, fromResult: %s, toResult: %s", nextSourceTable.getTableName(), columnName, next.getColumnType(), fromResult, toResult));
                                                        }
                                                    } else if (columnType.startsWith("number")) {
                                                        BigDecimal fromResult = sourceResultSet.getBigDecimal(columnName);
                                                        BigDecimal toResult = targetResultSet.getBigDecimal(columnName);
                                                        if (!fromResult.equals(toResult)) {
                                                            LOGGER.warning(String.format("table: %s, column: %s, type: %s, fromResult: %s, toResult: %s", nextSourceTable.getTableName(), columnName, next.getColumnType(), fromResult, toResult));
                                                        }
                                                    } else if (columnType.startsWith("bigint")) {
                                                        long fromResult = sourceResultSet.getLong(columnName);
                                                        long toResult = targetResultSet.getLong(columnName);
                                                        if (fromResult != toResult) {
                                                            LOGGER.warning(String.format("table: %s, column: %s, type: %s, fromResult: %s, toResult: %s", nextSourceTable.getTableName(), columnName, next.getColumnType(), fromResult, toResult));
                                                        }
                                                    } else if (columnType.startsWith("boolean")) {
                                                        boolean fromResult = sourceResultSet.getBoolean(columnName);
                                                        boolean toResult = targetResultSet.getBoolean(columnName);
                                                        if (fromResult != toResult) {
                                                            LOGGER.warning(String.format("table: %s, column: %s, type: %s, fromResult: %s, toResult: %s", nextSourceTable.getTableName(), columnName, next.getColumnType(), fromResult, toResult));
                                                        }
                                                    } else if (columnType.startsWith("clob")) {
                                                        String fromResult = sourceResultSet.getString(columnName);
                                                        String toResult = targetResultSet.getString(columnName);
                                                        if (fromResult != null && !fromResult.equals(toResult)) {
                                                            LOGGER.warning(String.format("table: %s, column: %s, type: %s, fromResult: %s, toResult: %s", nextSourceTable.getTableName(), columnName, next.getColumnType(), fromResult, toResult));
                                                        }
                                                    } else if (columnType.startsWith("blob") || columnType.startsWith("byte")) {
                                                        byte[] fromBlob = sourceResultSet.getBytes(columnName);
                                                        String fromResult = Base64.getEncoder().encodeToString(fromBlob);
                                                        byte[] toBlob = targetResultSet.getBytes(columnName);
                                                        String toResult = Base64.getEncoder().encodeToString(toBlob);
                                                        if (!fromResult.equals(toResult)) {
                                                            LOGGER.warning(String.format("table: %s, column: %s, type: %s, fromResult: %s, toResult: %s", nextSourceTable.getTableName(), columnName, next.getColumnType(), fromResult, toResult));
                                                        }
                                                    }
                                                } catch (Exception e) {
                                                    LOGGER.warning(String.format("Table: %s, column: %s, columnType: %s, error: %s", nextSourceTable.getTableName(), columnName, columnType, e.getMessage()));
                                                    throw e;
                                                }
                                            }
                                        } catch (Exception e) {
                                            LOGGER.warning(e.getMessage());
                                        }
                                    }
                                    sourceResultSet.close();
                                    sourceStmt.close();
                                    targetResultSet.close();
                                    targetStmt.close();
                                } catch (Exception e) {
                                    LOGGER.warning(e.getMessage());
                                }
                            }
                        }
                    }
                }
            }
            LOGGER.info("DONE - Comparing data complete");
        } else {
            LOGGER.info("Skipping data comparison");
        }
    }

    public Config getConfig() {
        return config;
    }

    private String processClob(Reader clobStreamReader) throws IOException {
        int intch;
        StringBuilder sb = new StringBuilder();
        while ((intch = clobStreamReader.read()) != -1) {
            char ch = (char) intch;
            sb.append(ch);
        }
        return sb.toString();
    }

    private void validateConfig(Config config) {
        this.config = config;
        // validations:
        // - exactly one valid target database has to be configured at this time
        // - check if the flag 'postgres_type=true' has been set if the source dbms_type starts with 'post'. Will trigger a warning log message
        // - target database cannot include {translate_to_postgres_large_clob_object} and {translate_from_postgres_large_clob_object}
        // - database names have to be unique
        // - source and target cannot be the same database type
        // - either configure translate_all=true && do not provide a list of columns
        // - or configure translate_all=false && provide a list of columns
        //
        if (config.getTargetDatabases().size() != 1) {
            throw new RuntimeException("The configuration needs to contain exactly one target database");
        }
        if (config.getSourceDatabase().getDbmsType().startsWith("post") && !config.getSourceDatabase().isPostgresType()) {
            LOGGER.warning("It appears that the source database is of type 'postgresql' but the flag 'postgres_type=true' has not been set. Please set the flag if it is the case.");
        }
        if (!config.getSourceDatabase().getDbmsType().startsWith("post") && config.getSourceDatabase().isPostgresType()) {
            LOGGER.warning("It appears that the source database is not of type 'postgresql' but the flag 'postgres_type=true' has been set. Please unset the flag if it is the case.");
        }
        Set<String> databaseNames = new HashSet<>();
        Set<String> databaseTypes = new HashSet<>();
        ConfigDatabase source = config.getSourceDatabase();
        databaseNames.add(source.getName().toLowerCase());
        databaseTypes.add(source.getDbmsType().toLowerCase());
        boolean includesTranslateToPostgresLargeClobObject = false;
        for (ConfigDatabase next : config.getTargetDatabases()) {
            if (!databaseNames.add(next.getName().toLowerCase())) {
                throw new RuntimeException("Database Names have to be unique. Duplicates were found");
            }
            if (!databaseTypes.add(next.getDbmsType().toLowerCase())) {
                throw new RuntimeException(String.format("The source (%s) and target (%s) database cannot be of the same dbms_type", source.getName(), next.getName()));
            }
            for (PostgresLargeObjectTranslation nextPLOT : next.getTranslateToPostgresClobObjects()) {
                if (nextPLOT.isTranslateAll() && !nextPLOT.getColumns().isEmpty()) {
                    throw new RuntimeException(String.format("If {translate_all=true} is configured a list of columns is not supported. Check the config of target database '%s'", next.getName()));
                }
                if (!nextPLOT.isTranslateAll() && nextPLOT.getColumns().isEmpty()) {
                    throw new RuntimeException(String.format("If {translate_all=false} a list of columns must be configured. Check the config of target database '%s'", next.getName()));
                }
                includesTranslateToPostgresLargeClobObject = true;
            }
            for (PostgresLargeObjectTranslation nextPLOT : next.getTranslateFromPostgresClobObjects()) {
                if (includesTranslateToPostgresLargeClobObject) {
                    throw new RuntimeException("A target database can either include {translate_to_postgres_large_clob_object} or {translate_from_postgres_large_clob_object} but not both");
                }
                if (nextPLOT.isTranslateAll() && !nextPLOT.getColumns().isEmpty()) {
                    throw new RuntimeException(String.format("If {translate_all=true} is configured a list of columns is not supported. Check the config to target database %s", next.getName()));
                }
                if (!nextPLOT.isTranslateAll() && nextPLOT.getColumns().isEmpty()) {
                    throw new RuntimeException(String.format("If {translate_all=false} a list of columns must be configured. Check the config to target database %s", next.getName()));
                }
            }
        }
    }
}