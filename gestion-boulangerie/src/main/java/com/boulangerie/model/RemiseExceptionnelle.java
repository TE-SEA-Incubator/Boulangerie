package com.boulangerie.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class RemiseExceptionnelle {
    private String id;
    private String produitId;
    private String motif;
    private BigDecimal prixNormal;
    private BigDecimal prixAccorde;
    private int quantiteMin;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private boolean actif;
    private String creePar;
    private LocalDateTime dateCreation;

    public RemiseExceptionnelle() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProduitId() { return produitId; }
    public void setProduitId(String produitId) { this.produitId = produitId; }
    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
    public BigDecimal getPrixNormal() { return prixNormal; }
    public void setPrixNormal(BigDecimal prixNormal) { this.prixNormal = prixNormal; }
    public BigDecimal getPrixAccorde() { return prixAccorde; }
    public void setPrixAccorde(BigDecimal prixAccorde) { this.prixAccorde = prixAccorde; }
    public int getQuantiteMin() { return quantiteMin; }
    public void setQuantiteMin(int quantiteMin) { this.quantiteMin = quantiteMin; }
    public LocalDate getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }
    public LocalDate getDateFin() { return dateFin; }
    public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    public boolean isApplicable(LocalDate date, int quantite) {
        return actif
            && quantite >= quantiteMin
            && (dateDebut == null || !date.isBefore(dateDebut))
            && (dateFin   == null || !date.isAfter(dateFin));
    }

    public BigDecimal getMontantRemise() {
        if (prixNormal == null || prixAccorde == null) return BigDecimal.ZERO;
        return prixNormal.subtract(prixAccorde);
    }
}
