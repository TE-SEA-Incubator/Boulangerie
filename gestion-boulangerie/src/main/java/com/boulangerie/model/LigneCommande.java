package com.boulangerie.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public class LigneCommande {
    private String id;
    private String ficheId;
    private Client client;
    private Produit produit;
    private int quantiteSortie;
    private int quantiteRetournee;
    private BigDecimal tarifApplicable;
    private String typeTarif;
    private BigDecimal remisePct;    // pourcentage de remise
    private BigDecimal montantHt;
    private String motifRetour;
    private String modifiePar;
    private LocalDate dateSortie; // Pour consultation simplifiée

    public LigneCommande() {
        this.quantiteSortie    = 0;
        this.quantiteRetournee = 0;
        this.remisePct         = BigDecimal.ZERO;
        this.montantHt         = BigDecimal.ZERO;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFicheId() { return ficheId; }
    public void setFicheId(String ficheId) { this.ficheId = ficheId; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
    public Produit getProduit() { return produit; }
    public void setProduit(Produit produit) { this.produit = produit; }
    public int getQuantiteSortie() { return quantiteSortie; }
    public void setQuantiteSortie(int quantiteSortie) { this.quantiteSortie = quantiteSortie; recalculer(); }
    public int getQuantiteRetournee() { return quantiteRetournee; }
    public void setQuantiteRetournee(int quantiteRetournee) { this.quantiteRetournee = quantiteRetournee; recalculer(); }
    public BigDecimal getTarifApplicable() { return tarifApplicable; }
    public void setTarifApplicable(BigDecimal tarifApplicable) { this.tarifApplicable = tarifApplicable; recalculer(); }
    public String getTypeTarif() { return typeTarif; }
    public void setTypeTarif(String typeTarif) { this.typeTarif = typeTarif; }
    public BigDecimal getRemisePct() { return remisePct; }
    public void setRemisePct(BigDecimal remisePct) { this.remisePct = remisePct; recalculer(); }
    public BigDecimal getMontantHt() { return montantHt; }
    public void setMontantHt(BigDecimal montantHt) { this.montantHt = montantHt; }
    public String getMotifRetour() { return motifRetour; }
    public void setMotifRetour(String motifRetour) { this.motifRetour = motifRetour; }
    public String getModifiePar() { return modifiePar; }
    public void setModifiePar(String modifiePar) { this.modifiePar = modifiePar; }
    public LocalDate getDateSortie() { return dateSortie; }
    public void setDateSortie(LocalDate dateSortie) { this.dateSortie = dateSortie; }
    public BigDecimal getPrixUnitaire() { return tarifApplicable != null ? tarifApplicable : (produit != null ? produit.getPrixUnitaire() : BigDecimal.ZERO); }

    public int getQuantiteNette() { return quantiteSortie - quantiteRetournee; }

    private void recalculer() {
        if (tarifApplicable == null) return;
        BigDecimal qteNette = BigDecimal.valueOf(getQuantiteNette());
        BigDecimal brut = tarifApplicable.multiply(qteNette);
        if (remisePct != null && remisePct.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal facteur = BigDecimal.ONE.subtract(remisePct.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            montantHt = brut.multiply(facteur).setScale(2, RoundingMode.HALF_UP);
        } else {
            montantHt = brut.setScale(2, RoundingMode.HALF_UP);
        }
    }
}
