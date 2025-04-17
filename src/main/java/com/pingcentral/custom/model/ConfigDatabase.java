package com.pingcentral.custom.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

public class ConfigDatabase {
    @JsonIgnore(false)
    @JsonProperty("name")
    private String name;
    @JsonIgnore(false)
    @JsonProperty("dbms_type")
    private String dbmsType;
    @JsonProperty("postgres_type")
    private boolean postgresType;
    @JsonIgnore(false)
    @JsonProperty("jdbc_url")
    private String jdbcUrl;
    @JsonIgnore(false)
    @JsonProperty("username")
    private String username;
    @JsonIgnore(false)
    @JsonProperty("password")
    private String password;
    @JsonProperty("translate_to_postgres_large_clob_object")
    private List<PostgresLargeObjectTranslation> translateToPostgresClobObjects;
    @JsonProperty("translate_from_postgres_large_clob_object")
    private List<PostgresLargeObjectTranslation> translateFromPostgresClobObjects;

    public ConfigDatabase() {
        this.translateToPostgresClobObjects = new ArrayList<>();
        this.translateFromPostgresClobObjects = new ArrayList<>();
        this.postgresType = false;
    }

    public String getDbmsType() {
        return dbmsType;
    }

    public void setDbmsType(String dbmsType) {
        this.dbmsType = dbmsType;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isPostgresType() {
        return postgresType;
    }

    public void setPostgresType(boolean postgresType) {
        this.postgresType = postgresType;
    }

    public List<PostgresLargeObjectTranslation> getTranslateToPostgresClobObjects() {
        return translateToPostgresClobObjects;
    }

    public Map<String, Set<String>> getToPostgresLargeObjectColumnsAsMap() {
        Map<String, Set<String>> result = new HashMap<>();
        for(PostgresLargeObjectTranslation translation : translateToPostgresClobObjects) {
            result.put(translation.getTable(), translation.getColumns());
        }
        return result;
    }

    public Map<String, Set<String>> getFromPostgresLargeObjectColumnsAsMap() {
        Map<String, Set<String>> result = new HashMap<>();
        for(PostgresLargeObjectTranslation translation : translateFromPostgresClobObjects) {
            result.put(translation.getTable(), translation.getColumns());
        }
        return result;
    }

    public void setTranslateToPostgresClobObjects(List<PostgresLargeObjectTranslation> translateToPostgresClobObjects) {
        this.translateToPostgresClobObjects = translateToPostgresClobObjects;
    }

    public List<PostgresLargeObjectTranslation> getTranslateFromPostgresClobObjects() {
        return translateFromPostgresClobObjects;
    }

    public void setTranslateFromPostgresClobObjects(List<PostgresLargeObjectTranslation> translateFromPostgresClobObjects) {
        this.translateFromPostgresClobObjects = translateFromPostgresClobObjects;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
