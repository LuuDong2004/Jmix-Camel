package com.vn.jmixcamel.dto;

public class QueryFilter {
    private String field;
    private String op;
    private Object value;

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }

    public String getOp() { return op; }
    public void setOp(String op) { this.op = op; }

    public Object getValue() { return value; }
    public void setValue(Object value) { this.value = value; }
}
