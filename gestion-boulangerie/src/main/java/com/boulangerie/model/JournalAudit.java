package com.boulangerie.model;

import java.time.LocalDateTime;

public class JournalAudit {
    /** Actions métier journalisées */
    public static final String CREATE   = "CREATE";
    public static final String UPDATE   = "UPDATE";
    public static final String DELETE   = "DELETE";
    public static final String LOGIN    = "LOGIN";
    public static final String LOGOUT   = "LOGOUT";
    public static final String BLOCK    = "BLOCK";
    public static final String UNBLOCK  = "UNBLOCK";
    public static final String CLOTURE  = "CLOTURE";
    public static final String AVOIR    = "AVOIR";
    public static final String ECART    = "ECART";

    private String id;
    private String entite;
    private String entiteId;
    private String action;
    private String utilisateurId;
    private String loginUtilisateur;
    private String details;
    private String ipAddress;
    private LocalDateTime dateAction;

    public JournalAudit() {}

    public JournalAudit(String entite, String entiteId, String action,
                        String utilisateurId, String loginUtilisateur, String details) {
        this.entite           = entite;
        this.entiteId         = entiteId;
        this.action           = action;
        this.utilisateurId    = utilisateurId;
        this.loginUtilisateur = loginUtilisateur;
        this.details          = details;
        this.dateAction       = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEntite() { return entite; }
    public void setEntite(String entite) { this.entite = entite; }
    public String getEntiteId() { return entiteId; }
    public void setEntiteId(String entiteId) { this.entiteId = entiteId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getUtilisateurId() { return utilisateurId; }
    public void setUtilisateurId(String utilisateurId) { this.utilisateurId = utilisateurId; }
    public String getLoginUtilisateur() { return loginUtilisateur; }
    public void setLoginUtilisateur(String loginUtilisateur) { this.loginUtilisateur = loginUtilisateur; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public LocalDateTime getDateAction() { return dateAction; }
    public void setDateAction(LocalDateTime dateAction) { this.dateAction = dateAction; }
}
