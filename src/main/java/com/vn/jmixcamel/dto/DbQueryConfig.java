package com.vn.jmixcamel.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.List;

public class DbQueryConfig {
    private String entity;

    @JacksonXmlElementWrapper(localName = "filters")
    @JacksonXmlProperty(localName = "filter")
    private List<QueryFilter> filters;
    private String orderBy;
    private String orderDir;
    private Integer limit;

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
}
