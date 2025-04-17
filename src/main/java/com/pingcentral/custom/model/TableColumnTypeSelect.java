package com.pingcentral.custom.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class TableColumnTypeSelect {

    @JsonProperty("column_types")
    private List<ColumnType> columnTypes;
    @JsonProperty("table_name")
    private String tableName;
    @JsonProperty("select_stmt")
    private String selectStmt;
    @JsonProperty("insert_stmt")
    private String insertStmt;
    @JsonProperty("delete_stmt")
    private String deleteStmt;

    public TableColumnTypeSelect() {
        this.columnTypes = new ArrayList<>();
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getSelectStmt() {
        return selectStmt;
    }

    public void setSelectStmt(String selectStmt) {
        this.selectStmt = selectStmt;
    }

    public List<ColumnType> getColumnTypes() {
        return columnTypes;
    }

    public void addColumnType(ColumnType columnType) {
        this.columnTypes.add(columnType);
    }
    public void addColumnType(List<ColumnType> columnType) {
        this.columnTypes.addAll(columnType);
    }

    public String getInsertStmt() {
        return insertStmt;
    }

    public void setInsertStmt(String insertStmt) {
        this.insertStmt = insertStmt;
    }

    public String getDeleteStmt() {
        return deleteStmt;
    }

    public void setDeleteStmt(String deleteStmt) {
        this.deleteStmt = deleteStmt;
    }

    public void dropColumn(String columnName) {
        for(ColumnType columnType : columnTypes) {
            if(columnType.getColumnName().equalsIgnoreCase(columnName)) {
                columnTypes.remove(columnType);
                break;
            }
        }
    }

    public void setColumnTypes(List<ColumnType> columnTypes) {
        this.columnTypes = columnTypes;
    }

    public void renameColumn(String oldColumnName, String newColumnName, String dataType) {
        for(ColumnType columnType : columnTypes) {
            if(columnType.getColumnName().equalsIgnoreCase(oldColumnName)) {
                columnType.setColumnName(newColumnName);
                columnType.setColumnType(dataType);
            }
        }
    }

    public void modifyDataType(String columnName, String dataType) {
        for(ColumnType columnType : columnTypes) {
            if(columnType.getColumnName().equalsIgnoreCase(columnName)) {
                columnType.setColumnType(dataType);
            }
        }
    }

}
