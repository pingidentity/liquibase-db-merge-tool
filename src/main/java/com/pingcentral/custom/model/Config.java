package com.pingcentral.custom.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Config {

    @JsonProperty("description")
    private String description;
    @JsonIgnore(false)
    @JsonProperty("change_log_file")
    private String changeLogFile;
    @JsonProperty("delete_target_data")
    private boolean deleteTargetData;
    @JsonProperty("compare_data")
    private boolean compareData;
    @JsonIgnore(false)
    @JsonProperty("source_database")
    private ConfigDatabase sourceDatabase;
    @JsonIgnore(false)
    @JsonProperty("target_databases")
    private List<ConfigDatabase> targetDatabases;

    public Config() {
        deleteTargetData = true;
        compareData = false;
    }

    public String getChangeLogFile() {
        return changeLogFile;
    }

    public void setChangeLogFile(String changeLogFile) {
        this.changeLogFile = changeLogFile;
    }

    public boolean isDeleteTargetData() {
        return deleteTargetData;
    }

    public void setDeleteTargetData(boolean deleteTargetData) {
        this.deleteTargetData = deleteTargetData;
    }

    public boolean isCompareData() {
        return compareData;
    }

    public void setCompareData(boolean compareData) {
        this.compareData = compareData;
    }

    public ConfigDatabase getSourceDatabase() {
        return sourceDatabase;
    }

    public void setSourceDatabase(ConfigDatabase sourceDatabase) {
        this.sourceDatabase = sourceDatabase;
    }

    public List<ConfigDatabase> getTargetDatabases() {
        return targetDatabases;
    }

    public void setTargetDatabases(List<ConfigDatabase> targetDatabases) {
        this.targetDatabases = targetDatabases;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
