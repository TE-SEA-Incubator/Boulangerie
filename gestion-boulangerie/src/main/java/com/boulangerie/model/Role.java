package com.boulangerie.model;

import java.util.ArrayList;
import java.util.List;

public class Role {
    private String id;
    private String nom;
    private String description;
    private List<Permission> permissions = new ArrayList<>();

    public Role() {}
    public Role(String id, String nom) { this.id = id; this.nom = nom; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<Permission> getPermissions() { return permissions; }
    public void setPermissions(List<Permission> permissions) { this.permissions = permissions; }

    public boolean hasPermission(String code) {
        return permissions.stream().anyMatch(p -> p.getCode().equals(code));
    }

    @Override public String toString() { return nom; }
}
