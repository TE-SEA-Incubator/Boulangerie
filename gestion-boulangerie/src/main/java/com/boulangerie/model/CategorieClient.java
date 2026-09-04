package com.boulangerie.model;

public class CategorieClient {
    private String id;
    private String nom;

    public CategorieClient() {}
    public CategorieClient(String id, String nom) { this.id = id; this.nom = nom; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    @Override public String toString() { return nom; }
}
