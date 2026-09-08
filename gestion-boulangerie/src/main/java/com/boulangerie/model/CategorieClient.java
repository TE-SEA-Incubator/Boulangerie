package com.boulangerie.model;

import java.math.BigDecimal;

public class CategorieClient {
    private String id;
    private String nom;
    private BigDecimal pourcentageRemise;

    public CategorieClient() {
        this.pourcentageRemise = BigDecimal.ZERO;
    }
    public CategorieClient(String id, String nom) {
        this.id = id;
        this.nom = nom;
        this.pourcentageRemise = BigDecimal.ZERO;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public BigDecimal getPourcentageRemise() { return pourcentageRemise; }
    public void setPourcentageRemise(BigDecimal pourcentageRemise) { this.pourcentageRemise = pourcentageRemise; }

    @Override public String toString() { return nom; }
}
