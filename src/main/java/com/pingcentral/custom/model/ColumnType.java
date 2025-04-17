package com.pingcentral.custom.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class ColumnType {

    @JsonProperty("index")
    private int index;
    @JsonProperty("column_name")
    private String columnName;
    @JsonProperty("column_type")
    private String columnType;

    public ColumnType() {
        // needed for Jackson
    }

    public ColumnType(String columnName, String columnType) {
        this.columnName = columnName;
        this.columnType = columnType;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getColumnType() {
        return columnType;
    }

    public void setColumnType(String columnType) {
        this.columnType = columnType;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ColumnType that = (ColumnType) o;
        return Objects.equals(columnName, that.columnName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(columnName);
    }
}
