package com.boulangerie.model;

public class Permission {
    private String id;
    private String code;
    private String description;

    public Permission() {}
    public Permission(String id, String code, String description) {
        this.id = id; this.code = code; this.description = description;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override public String toString() { return code; }
}
