package com.boulangerie.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SoldeClient {
    private String id;
    private String clientId;
    private LocalDate dateSolde;
    private BigDecimal soldeOuverture  = BigDecimal.ZERO;
    private BigDecimal sortiesDuJour   = BigDecimal.ZERO;
    private BigDecimal versementsDuJour = BigDecimal.ZERO;
    private BigDecimal soldeCloture    = BigDecimal.ZERO;

    public SoldeClient() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public LocalDate getDateSolde() { return dateSolde; }
    public void setDateSolde(LocalDate dateSolde) { this.dateSolde = dateSolde; }
    public BigDecimal getSoldeOuverture() { return soldeOuverture; }
    public void setSoldeOuverture(BigDecimal soldeOuverture) { this.soldeOuverture = soldeOuverture; }
    public BigDecimal getSortiesDuJour() { return sortiesDuJour; }
    public void setSortiesDuJour(BigDecimal sortiesDuJour) { this.sortiesDuJour = sortiesDuJour; }
    public BigDecimal getVersementsDuJour() { return versementsDuJour; }
    public void setVersementsDuJour(BigDecimal versementsDuJour) { this.versementsDuJour = versementsDuJour; }
    public BigDecimal getSoldeCloture() { return soldeCloture; }
    public void setSoldeCloture(BigDecimal soldeCloture) { this.soldeCloture = soldeCloture; }

    /** soldeCloture = soldeOuverture + sortiesDuJour − versementsDuJour */
    public void calculer() {
        soldeCloture = soldeOuverture.add(sortiesDuJour).subtract(versementsDuJour);
    }
}
