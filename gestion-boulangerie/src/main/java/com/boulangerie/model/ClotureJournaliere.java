package com.boulangerie.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ClotureJournaliere {
    private String id;
    private LocalDate dateCloture;
    private BigDecimal montantAttendu;
    private BigDecimal montantRemis;
    private BigDecimal montantEnregistre;
    private BigDecimal ecartTotal;
    private String motifEcart;
    private BigDecimal tauxRecouvrement;
    private BigDecimal soldeCloture;
    private String valideParId;
    private LocalDateTime dateValidation;

    public ClotureJournaliere() {
        this.montantAttendu    = BigDecimal.ZERO;
        this.montantRemis      = BigDecimal.ZERO;
        this.montantEnregistre = BigDecimal.ZERO;
        this.ecartTotal        = BigDecimal.ZERO;
        this.tauxRecouvrement  = BigDecimal.ZERO;
        this.soldeCloture      = BigDecimal.ZERO;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public LocalDate getDateCloture() { return dateCloture; }
    public void setDateCloture(LocalDate dateCloture) { this.dateCloture = dateCloture; }
    public BigDecimal getMontantAttendu() { return montantAttendu; }
    public void setMontantAttendu(BigDecimal montantAttendu) { this.montantAttendu = montantAttendu; }
    public BigDecimal getMontantRemis() { return montantRemis; }
    public void setMontantRemis(BigDecimal montantRemis) { this.montantRemis = montantRemis; }
    public BigDecimal getMontantEnregistre() { return montantEnregistre; }
    public void setMontantEnregistre(BigDecimal montantEnregistre) { this.montantEnregistre = montantEnregistre; }
    public BigDecimal getEcartTotal() { return ecartTotal; }
    public void setEcartTotal(BigDecimal ecartTotal) { this.ecartTotal = ecartTotal; }
    public String getMotifEcart() { return motifEcart; }
    public void setMotifEcart(String motifEcart) { this.motifEcart = motifEcart; }
    public BigDecimal getTauxRecouvrement() { return tauxRecouvrement; }
    public void setTauxRecouvrement(BigDecimal tauxRecouvrement) { this.tauxRecouvrement = tauxRecouvrement; }
    public BigDecimal getSoldeCloture() { return soldeCloture; }
    public void setSoldeCloture(BigDecimal soldeCloture) { this.soldeCloture = soldeCloture; }
    public String getValideParId() { return valideParId; }
    public void setValideParId(String valideParId) { this.valideParId = valideParId; }
    public LocalDateTime getDateValidation() { return dateValidation; }
    public void setDateValidation(LocalDateTime dateValidation) { this.dateValidation = dateValidation; }

    public void calculerTaux() {
        if (montantAttendu != null && montantAttendu.compareTo(BigDecimal.ZERO) > 0) {
            tauxRecouvrement = montantEnregistre
                .divide(montantAttendu, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        }
        ecartTotal = montantRemis.subtract(montantEnregistre);
    }
}
