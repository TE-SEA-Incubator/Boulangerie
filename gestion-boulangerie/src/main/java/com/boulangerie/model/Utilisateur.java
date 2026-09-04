package com.boulangerie.model;

import java.time.LocalDateTime;

public class Utilisateur {
    private String id;
    private String login;
    private String motDePasse;   // BCrypt hash (jamais exposé en clair)
    private String nomComplet;
    private String telephone;
    private String email;
    private Role role;
    private boolean actif;
    private LocalDateTime dateCreation;
    private LocalDateTime derniereConnexion;

    public Utilisateur() {}

    // ── Getters / Setters ──────────────────────────────────────
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getMotDePasse() { return motDePasse; }
    public void setMotDePasse(String motDePasse) { this.motDePasse = motDePasse; }

    public String getNomComplet() { return nomComplet; }
    public void setNomComplet(String nomComplet) { this.nomComplet = nomComplet; }

    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    public LocalDateTime getDerniereConnexion() { return derniereConnexion; }
    public void setDerniereConnexion(LocalDateTime derniereConnexion) { this.derniereConnexion = derniereConnexion; }

    public boolean hasPermission(String code) {
        return role != null && role.hasPermission(code);
    }

    public boolean isAdmin() {
        return role != null && "ADMIN".equals(role.getNom());
    }

    @Override public String toString() { return nomComplet + " (" + login + ")"; }
}
