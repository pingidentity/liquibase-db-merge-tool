package com.pingcentral.custom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pingcentral.custom.model.ColumnType;
import com.pingcentral.custom.model.LiquibaseChangesAsJson;
import com.pingcentral.custom.model.TableColumnTypeSelect;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Logger;

public class LiquibaseChangelogProcessor {

    private static final Logger LOGGER = Logger.getLogger(LiquibaseChangelogProcessor.class.getName());

    private static final Map<String, Map<String, String>> DBMS_DATA_TYPES = new HashMap<>();

    private Map<String, List<TableColumnTypeSelect>> sourceTables;
    private Map<String, List<TableColumnTypeSelect>> targetTables;

    /**
     * Needed to manage the order of tables (and with that the required order of delete statements)
     */
    private final Map<String, Integer> tableIndexes;

    private final DocumentBuilder db;
    private final File changelogMaster;
    private final String sourceDbms;
    private final String targetDbms;

    /**
     * {@link #LiquibaseChangelogProcessor(String, String, String, String)} )
     */
    public LiquibaseChangelogProcessor(String changelogMaster, String sourceDbms, String targetDbms) {
        this(changelogMaster, sourceDbms, targetDbms, null);
    }

    /**
     * @param changelogMaster    The path to the configuration file
     * @param sourceDbms         The source dbms (i.e.: mysql, postgresql)
     * @param targetDbms         The target dbms (i.e.: mysql, postgresql)
     * @param fromResourcePrefix If the configuration file is loaded from a jar file found in the resources directory
     */
    public LiquibaseChangelogProcessor(String changelogMaster, String sourceDbms, String targetDbms, String fromResourcePrefix) {
        this.changelogMaster = new File(fromResourcePrefix == null ? changelogMaster : String.format("%s/%s", fromResourcePrefix, changelogMaster));
        this.sourceDbms = sourceDbms;
        this.targetDbms = targetDbms;
        this.tableIndexes = new HashMap<>();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            // optional, but recommended
            // process XML securely, avoid attacks like XML External Entities (XXE)
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            db = dbf.newDocumentBuilder();
        } catch (Exception e) {
            // this should never happen
            LOGGER.warning(e.getMessage());
            throw new RuntimeException(String.format("This is a 'this should never happen error: %s'", e.getMessage()));
        }
    }

    /**
     * Converts the processed Liquibase changelog files into a JSON file with an internal format. This can be used as input instead of the Liquibase changelog files
     *
     * @return A string representing the json based changelog files
     * @throws JsonProcessingException
     */
    public String getChangelogAsJson() throws JsonProcessingException {

        Map<String, Map<String, List<TableColumnTypeSelect>>> dbmsList = new HashMap<>();
        dbmsList.put(sourceDbms, sourceTables);
        dbmsList.put(targetDbms, targetTables);

        LiquibaseChangesAsJson output = new LiquibaseChangesAsJson();
        output.setDatabases(dbmsList);
        output.setTableIndexes(tableIndexes);

        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(output);
    }

    /**
     * Processes the Liquibase changelog files of the json file that uses the tools internal format
     * {@link #getChangelogAsJson()}
     */
    public void processLiquibaseFiles() {
        // dbms, table, columns
        Map<String, Map<String, List<TableColumnTypeSelect>>> dbmsList = new HashMap<>();
        Comparator<String> ascending = (o1, o2) -> tableIndexes.get(o1).compareTo(tableIndexes.get(o2));
        dbmsList.put(sourceDbms, new TreeMap<>(ascending));
        dbmsList.put(targetDbms, new TreeMap<>(ascending));
        if (changelogMaster.getAbsolutePath().endsWith("json")) {
            ObjectMapper mapper = new ObjectMapper();
            LiquibaseChangesAsJson dbmsTables = null;
            try {
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                try (InputStream is = loader.getResourceAsStream(changelogMaster.getPath())) {
                    dbmsTables = mapper.readValue(is, LiquibaseChangesAsJson.class);
                } catch (Exception e) {
                    dbmsTables = mapper.readValue(new FileReader(changelogMaster), LiquibaseChangesAsJson.class);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            tableIndexes.putAll(dbmsTables.getTableIndexes());
            dbmsList.get(sourceDbms).putAll(dbmsTables.getDatabases().get(sourceDbms));
            dbmsList.get(targetDbms).putAll(dbmsTables.getDatabases().get(targetDbms));
        } else {
            processLiquibaseConfigFiles(processLiquibaseChangelogMaster(), dbmsList);
        }
        sourceTables = dbmsList.get(sourceDbms);
        targetTables = dbmsList.get(targetDbms);
    }

    public Map<String, List<TableColumnTypeSelect>> getSourceTables() {
        return sourceTables;
    }

    public Map<String, List<TableColumnTypeSelect>> getTargetTables() {
        return targetTables;
    }

    /**
     * Some Liquibase changelof master files may use properties to translate data types to database specific ones. This method helps in processing those
     *
     * @param dbms   The dtabase system (i.e.: mysql, postgresql)
     * @param typeId The id of the type, found like ${type.clos} in changelog-master.xml
     * @return The string to use
     */
    private String getDbDataType(String dbms, String typeId) {
        try {
            if (typeId.startsWith("$")) {
                String type = typeId.replaceAll("[${}]", "");
                return DBMS_DATA_TYPES.get(dbms).get(type);
            } else {
                return typeId;
            }
        } catch (NullPointerException e) {
            LOGGER.warning(e.getMessage());
            return null;
        }
    }

    private List<String> processLiquibaseChangelogMaster() {
        List<String> fileNames = new ArrayList<>();
        try {
            Document doc = db.parse(changelogMaster);

            // optional, but recommended
            // http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
            doc.getDocumentElement().normalize();

            NodeList propertyList = doc.getElementsByTagName("property");
            for (int temp = 0; temp < propertyList.getLength(); temp++) {
                Node node = propertyList.item(temp);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    String typeId = element.getAttribute("name");
                    String typeValue = element.getAttribute("value");
                    String[] dbms = element.getAttribute("dbms").split(",");
                    for (String next : dbms) {
                        DBMS_DATA_TYPES.computeIfAbsent(next, k -> new HashMap<>());
                        DBMS_DATA_TYPES.get(next).put(typeId, typeValue);
                    }
                }
            }

            NodeList fileList = doc.getElementsByTagName("include");
            for (int temp = 0; temp < fileList.getLength(); temp++) {
                Node node = fileList.item(temp);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    fileNames.add(element.getAttribute("file"));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        return fileNames;
    }

    /**
     * Processes all changelog files. It creates the order of tables, select, insert and delete statements that used later
     *
     * @param fileNames A list to all changelog files
     * @param dbmsList  A structure to have good accessibility to the type of database, tables and columns with data types
     */
    private void processLiquibaseConfigFiles(List<String> fileNames, Map<String, Map<String, List<TableColumnTypeSelect>>> dbmsList) {

        Set<String> unhandled = new HashSet<>();
        int tableIndex = 1;

        for (String nextFile : fileNames) {
            try {

                Document doc = db.parse(new File(String.format("%s/%s", changelogMaster.getParent(), nextFile)));

                // optional, but recommended
                // http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
                doc.getDocumentElement().normalize();

                NodeList changeSets = doc.getElementsByTagName("changeSet");
                for (int i = 0; i < changeSets.getLength(); i++) {
                    Node nodeChangeSet = changeSets.item(i);
                    if (nodeChangeSet.getNodeType() == Node.ELEMENT_NODE) {
                        NodeList allNodes = nodeChangeSet.getChildNodes();
                        for (int temp = 0; temp < allNodes.getLength(); temp++) {
                            Node node = allNodes.item(temp);
                            if (node.getNodeType() == Node.ELEMENT_NODE) {
                                Element element = (Element) node;
                                if ("createTable".equalsIgnoreCase(element.getTagName())) {
                                    String tableName = element.getAttribute("tableName");
                                    tableIndexes.put(tableName, tableIndex);
                                    tableIndex++;
                                    Map<String, TableColumnTypeSelect> response = processCreateTable(element);
                                    dbmsList.get(sourceDbms).computeIfAbsent(tableName, k -> new ArrayList<>());
                                    dbmsList.get(sourceDbms).get(tableName).add(response.get(sourceDbms));
                                    dbmsList.get(targetDbms).computeIfAbsent(tableName, k -> new ArrayList<>());
                                    dbmsList.get(targetDbms).get(tableName).add(response.get(targetDbms));
                                } else if ("dropColumn".equalsIgnoreCase(element.getTagName())) {
                                    String tableName = element.getAttribute("tableName");
                                    for (TableColumnTypeSelect tcts : dbmsList.get(sourceDbms).get(tableName)) {
                                        tcts.dropColumn(element.getAttribute("columnName"));
                                    }
                                    for (TableColumnTypeSelect tcts : dbmsList.get(targetDbms).get(tableName)) {
                                        tcts.dropColumn(element.getAttribute("columnName"));
                                    }
                                } else if ("renameColumn".equalsIgnoreCase(element.getTagName())) {
                                    String tableName = element.getAttribute("tableName");
                                    for (TableColumnTypeSelect tcts : dbmsList.get(sourceDbms).get(tableName)) {
                                        tcts.renameColumn(element.getAttribute("oldColumnName"), element.getAttribute("newColumnName"), getDbDataType(sourceDbms, element.getAttribute("columnDataType")));
                                    }
                                    for (TableColumnTypeSelect tcts : dbmsList.get(targetDbms).get(tableName)) {
                                        tcts.renameColumn(element.getAttribute("oldColumnName"), element.getAttribute("newColumnName"), getDbDataType(targetDbms, element.getAttribute("columnDataType")));
                                    }
                                } else if ("modifyDataType".equalsIgnoreCase(element.getTagName())) {
                                    String tableName = element.getAttribute("tableName");
                                    for (TableColumnTypeSelect tcts : dbmsList.get(sourceDbms).get(tableName)) {
                                        tcts.modifyDataType(element.getAttribute("columnName"), getDbDataType(sourceDbms, element.getAttribute("newDataType")));
                                    }
                                    for (TableColumnTypeSelect tcts : dbmsList.get(targetDbms).get(tableName)) {
                                        tcts.modifyDataType(element.getAttribute("columnName"), getDbDataType(targetDbms, element.getAttribute("newDataType")));
                                    }
                                } else if ("addColumn".equalsIgnoreCase(element.getTagName())) {
                                    String tableName = element.getAttribute("tableName");
                                    Map<String, TableColumnTypeSelect> response = processCreateTable(element);
                                    dbmsList.get(sourceDbms).computeIfAbsent(tableName, k -> new ArrayList<>());
                                    for (TableColumnTypeSelect next : dbmsList.get(sourceDbms).get(tableName)) {
                                        next.addColumnType(response.get(sourceDbms).getColumnTypes());
                                    }
                                    dbmsList.get(targetDbms).computeIfAbsent(tableName, k -> new ArrayList<>());
                                    for (TableColumnTypeSelect next : dbmsList.get(targetDbms).get(tableName)) {
                                        next.addColumnType(response.get(targetDbms).getColumnTypes());
                                    }
                                } else {
                                    unhandled.add(element.getTagName());
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        }
        // create select statement for each table
        for (String dbms : dbmsList.keySet()) {
            for (String tableName : dbmsList.get(dbms).keySet()) {
                for (TableColumnTypeSelect tableList : dbmsList.get(dbms).get(tableName)) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("SELECT ");
                    int columnCount = tableList.getColumnTypes().size();
                    int index = 1;
                    for (ColumnType ct : tableList.getColumnTypes()) {
                        sb.append(ct.getColumnName());
                        ct.setIndex(index);
                        if (index++ < columnCount) {
                            sb.append(",");
                        }
                    }
                    sb.append(" FROM ").append(tableName).append(";");
                    tableList.setSelectStmt(sb.toString());
                }
            }
        }
        // create delete statement for each table
        for (String dbms : dbmsList.keySet()) {
            for (String tableName : dbmsList.get(dbms).keySet()) {
                for (TableColumnTypeSelect tableList : dbmsList.get(dbms).get(tableName)) {
                    tableList.setDeleteStmt("DELETE FROM " + tableName + ";");
                }
            }
        }
        // create insert statement for each table as preparedStatement
        for (String dbms : dbmsList.keySet()) {
            for (String tableName : dbmsList.get(dbms).keySet()) {
                for (TableColumnTypeSelect tableList : dbmsList.get(dbms).get(tableName)) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("INSERT INTO ").append(tableName).append("(");
                    int columnCount = tableList.getColumnTypes().size();
                    int index = 1;
                    for (ColumnType ct : tableList.getColumnTypes()) {
                        sb.append(ct.getColumnName());
                        if (index++ < columnCount) {
                            sb.append(",");
                        }
                    }
                    sb.append(") VALUES (");
                    index = 1;
                    for (ColumnType ct : tableList.getColumnTypes()) {
                        sb.append("?");
                        if (index++ < columnCount) {
                            sb.append(",");
                        }
                    }
                    sb.append(")");
                    tableList.setInsertStmt(sb.toString());
                }
            }
        }
    }

    private Map<String, TableColumnTypeSelect> processCreateTable(Element createTableElement) {

        Map<String, TableColumnTypeSelect> result = new HashMap<>();

        TableColumnTypeSelect sourceTable = new TableColumnTypeSelect();
        TableColumnTypeSelect targetTable = new TableColumnTypeSelect();

        String tableName = createTableElement.getAttribute("tableName");
        sourceTable.setTableName(tableName);
        targetTable.setTableName(tableName);

        NodeList listColumns = createTableElement.getElementsByTagName("column");
        int noOfColumns = listColumns.getLength();
        for (int colCount = 0; colCount < noOfColumns; colCount++) {
            Node nodeColumn = listColumns.item(colCount);
            if (nodeColumn.getNodeType() == Node.ELEMENT_NODE) {
                Element elementColumn = (Element) nodeColumn;
                sourceTable.addColumnType(new ColumnType(elementColumn.getAttribute("name"), getDbDataType(sourceDbms, elementColumn.getAttribute("type"))));
                targetTable.addColumnType(new ColumnType(elementColumn.getAttribute("name"), getDbDataType(targetDbms, elementColumn.getAttribute("type"))));
            }
        }
        result.put(sourceDbms, sourceTable);
        result.put(targetDbms, targetTable);

        return result;
    }
}
