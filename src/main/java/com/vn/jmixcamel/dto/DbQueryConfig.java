package com.vn.jmixcamel.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.List;
import java.util.Map;

public class DbQueryConfig {
    private String entity;

    @JacksonXmlElementWrapper(localName = "filters")
    @JacksonXmlProperty(localName = "filter")
    private List<QueryFilter> filters;
    private String orderBy;
    private String orderDir;
    private Integer limit;

    /** Raw SQL (SELECT-only). When present, takes precedence over entity/filters. */
    private String sql;

    /** Optional: alias → column. Projects each row into a flat map of selected fields. */
    private Map<String, String> outputFields;

    /** Datasource name for raw SQL: "main" (Jmix DB) | "test" (external Test DB). Default "main". */
    private String datasource;

    public String getEntity() { return entity; }
    public void setEntity(String entity) { this.entity = entity; }

    public List<QueryFilter> getFilters() { return filters; }
    public void setFilters(List<QueryFilter> filters) { this.filters = filters; }

    public String getOrderBy() { return orderBy; }
    public void setOrderBy(String orderBy) { this.orderBy = orderBy; }

    public String getOrderDir() { return orderDir; }
    public void setOrderDir(String orderDir) { this.orderDir = orderDir; }

    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }

    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }

    public Map<String, String> getOutputFields() { return outputFields; }
    public void setOutputFields(Map<String, String> outputFields) { this.outputFields = outputFields; }

    public String getDatasource() { return datasource; }
    public void setDatasource(String datasource) { this.datasource = datasource; }
}
