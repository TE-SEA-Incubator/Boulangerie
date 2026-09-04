package com.boulangerie.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Avoir {
    private String id;
    private String numero;
    private String factureId;
    private LocalDate dateAvoir;
    private BigDecimal montant;
    private String motif;
    private String creePar;
    private LocalDateTime dateCreation;

    public Avoir() { this.montant = BigDecimal.ZERO; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getFactureId() { return factureId; }
    public void setFactureId(String factureId) { this.factureId = factureId; }
    public LocalDate getDateAvoir() { return dateAvoir; }
    public void setDateAvoir(LocalDate dateAvoir) { this.dateAvoir = dateAvoir; }
    public BigDecimal getMontant() { return montant; }
    public void setMontant(BigDecimal montant) { this.montant = montant; }
    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }
}
