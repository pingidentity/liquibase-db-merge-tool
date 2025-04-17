package com.pingcentral.custom.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class LiquibaseChangesAsJson {
    @JsonProperty("databases")
    Map<String, Map<String, List<TableColumnTypeSelect>>> databases;
    @JsonProperty("tables_indexes")
    Map<String, Integer> tableIndexes;

    public Map<String, Map<String, List<TableColumnTypeSelect>>> getDatabases() {
        return databases;
    }

    public void setDatabases(Map<String, Map<String, List<TableColumnTypeSelect>>> databases) {
        this.databases = databases;
    }

    public Map<String, Integer> getTableIndexes() {
        return tableIndexes;
    }

    public void setTableIndexes(Map<String, Integer> tableIndexes) {
        this.tableIndexes = tableIndexes;
    }
}
