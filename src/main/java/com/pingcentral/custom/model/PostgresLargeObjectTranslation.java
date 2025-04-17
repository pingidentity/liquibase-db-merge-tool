package com.pingcentral.custom.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashSet;
import java.util.Set;

public class PostgresLargeObjectTranslation {

    @JsonIgnore(false)
    @JsonProperty("table")
    private String table;
    @JsonProperty("translate_all")
    private boolean translateAll;
    @JsonProperty("columns")
    private Set<String> columns;

    public PostgresLargeObjectTranslation() {
        this.translateAll = false;
        this.columns = new HashSet<>();
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public boolean isTranslateAll() {
        return translateAll;
    }

    public void setTranslateAll(boolean translateAll) {
        this.translateAll = translateAll;
    }

    public Set<String> getColumns() {
        return columns;
    }

    public void setColumns(Set<String> columns) {
        this.columns = columns;
    }
}
