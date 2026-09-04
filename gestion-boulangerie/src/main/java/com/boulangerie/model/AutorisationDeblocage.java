package com.boulangerie.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class AutorisationDeblocage {
    private String id;
    private String clientId;
    private String managerId;
    private LocalDateTime dateAutorisation;
    private String motif;
    private String engagementClient;
    private BigDecimal montantAutorise;
    private LocalDate dureeValidite;

    public AutorisationDeblocage() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; }
    public LocalDateTime getDateAutorisation() { return dateAutorisation; }
    public void setDateAutorisation(LocalDateTime dateAutorisation) { this.dateAutorisation = dateAutorisation; }
    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
    public String getEngagementClient() { return engagementClient; }
    public void setEngagementClient(String engagementClient) { this.engagementClient = engagementClient; }
    public BigDecimal getMontantAutorise() { return montantAutorise; }
    public void setMontantAutorise(BigDecimal montantAutorise) { this.montantAutorise = montantAutorise; }
    public LocalDate getDureeValidite() { return dureeValidite; }
    public void setDureeValidite(LocalDate dureeValidite) { this.dureeValidite = dureeValidite; }
}
