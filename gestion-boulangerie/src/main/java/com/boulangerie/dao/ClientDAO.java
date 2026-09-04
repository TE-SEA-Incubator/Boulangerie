package com.boulangerie.dao;

import com.boulangerie.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;

public class ClientDAO {
    private static final Logger log = LoggerFactory.getLogger(ClientDAO.class);
    private final DatabaseConnection db = DatabaseConnection.getInstance();

    public List<Client> findAll() {
        return search(null, null, null, false);
    }

    public List<Client> search(String texte, String categorieId, String statut, boolean anonymeSeulement) {
        List<Client> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT c.*, cat.id AS cat_id, cat.nom AS cat_nom,
                   u.id AS liv_id, u.nom_complet AS liv_nom,
                   df.derniere_facture_date
            FROM client c
            LEFT JOIN categorie_client cat ON c.categorie_id = cat.id
            LEFT JOIN utilisateur u ON c.livreur_rattache = u.id
            LEFT JOIN (
                SELECT client_id, MAX(date_emission) AS derniere_facture_date
                FROM facture
                WHERE statut IN ('En attente', 'Partielle')
                GROUP BY client_id
            ) df ON df.client_id = c.id
            WHERE 1=1
            """);
        List<Object> params = new ArrayList<>();
        if (texte != null && !texte.isBlank()) {
            sql.append(" AND (c.code LIKE ? OR c.nom LIKE ? OR c.telephone LIKE ?)");
            String p = "%" + texte + "%"; params.add(p); params.add(p); params.add(p);
        }
        if (categorieId != null && !categorieId.isBlank()) {
            sql.append(" AND c.categorie_id=?"); params.add(categorieId);
        }
        if (statut != null && !statut.isBlank()) {
            sql.append(" AND c.statut=?"); params.add(statut);
        }
        if (anonymeSeulement) sql.append(" AND c.est_anonyme=1");
        sql.append(" ORDER BY c.code");
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            log.error("search clients", e);
        }
        return list;
    }

    public Optional<Client> findById(String id) {
        String sql = """
            SELECT c.*, cat.id AS cat_id, cat.nom AS cat_nom,
                   u.id AS liv_id, u.nom_complet AS liv_nom,
                   df.derniere_facture_date
            FROM client c
            LEFT JOIN categorie_client cat ON c.categorie_id = cat.id
            LEFT JOIN utilisateur u ON c.livreur_rattache = u.id
            LEFT JOIN (
                SELECT client_id, MAX(date_emission) AS derniere_facture_date
                FROM facture
                WHERE statut IN ('En attente', 'Partielle')
                GROUP BY client_id
            ) df ON df.client_id = c.id
            WHERE c.id=?
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
        } catch (SQLException e) {
            log.error("findById client {}", id, e);
        }
        return Optional.empty();
    }

    public String save(Client client) {
        String sql = """
            INSERT INTO client (id, code, nom, quartier, ville, telephone, email,
            categorie_id, est_anonyme, type_client, livreur_rattache,
            delai_paiement, plafond_credit, solde_precedent, solde_actuel, statut, notes)
            VALUES (UUID(),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            setClientParams(ps, client);
            ps.executeUpdate();
            return findIdByCode(client.getCode(), c);
        } catch (SQLException e) {
            log.error("save client", e);
            throw new RuntimeException(e);
        }
    }

    public void update(Client client) {
        String sql = """
            UPDATE client SET code=?,nom=?,quartier=?,ville=?,telephone=?,email=?,
            categorie_id=?,est_anonyme=?,type_client=?,livreur_rattache=?,
            delai_paiement=?,plafond_credit=?,solde_precedent=?,solde_actuel=?,statut=?,notes=?
            WHERE id=?
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            setClientParams(ps, client);
            ps.setString(17, client.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("update client", e);
            throw new RuntimeException(e);
        }
    }

    public void updateSolde(String clientId, BigDecimal nouveauSolde) {
        String sql = "UPDATE client SET solde_actuel=? WHERE id=?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBigDecimal(1, nouveauSolde);
            ps.setString(2, clientId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("updateSolde {}", clientId, e);
            throw new RuntimeException(e);
        }
    }

    public void updateStatut(String clientId, Client.Statut statut) {
        String sql = "UPDATE client SET statut=? WHERE id=?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, statut.name());
            ps.setString(2, clientId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("updateStatut {}", clientId, e);
            throw new RuntimeException(e);
        }
    }

    public void updateStatut(String clientId, String statut) {
        try {
            updateStatut(clientId, Client.Statut.valueOf(statut));
        } catch (IllegalArgumentException iae) {
            throw new RuntimeException("Statut invalide: " + statut, iae);
        }
    }

    public List<CategorieClient> findAllCategories() {
        List<CategorieClient> list = new ArrayList<>();
        String sql = "SELECT id, nom FROM categorie_client ORDER BY nom";
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(new CategorieClient(rs.getString("id"), rs.getString("nom")));
        } catch (SQLException e) {
            log.error("findAllCategories", e);
        }
        return list;
    }

    public int countBloques() {
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM client WHERE statut='Bloqué'")) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            log.error("countBloques", e);
        }
        return 0;
    }

    // ── Blocages ─────────────────────────────────────────────────
    public void saveBlocage(Blocage b) {
        String sql = "INSERT INTO blocage (id,client_id,date_blocage,motif,montant_dette,statut) VALUES (UUID(),?,?,?,?,?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, b.getClientId());
            ps.setDate(2, java.sql.Date.valueOf(b.getDateBlocage()));
            ps.setString(3, b.getMotif());
            ps.setBigDecimal(4, b.getMontantDette());
            ps.setString(5, b.getStatut().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("saveBlocage", e);
            throw new RuntimeException(e);
        }
    }

    public void leverBlocage(String clientId, String leveParId) {
        String sql = "UPDATE blocage SET statut='Levé', leve_par=?, date_levee=NOW() WHERE client_id=? AND statut='Actif'";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, leveParId);
            ps.setString(2, clientId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("leverBlocage", e);
            throw new RuntimeException(e);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────
    private void setClientParams(PreparedStatement ps, Client client) throws SQLException {
        ps.setString(1, client.getCode());
        ps.setString(2, client.getNom());
        ps.setString(3, client.getQuartier());
        ps.setString(4, client.getVille());
        ps.setString(5, client.getTelephone());
        ps.setString(6, client.getEmail());
        ps.setString(7, client.getCategorie() != null ? client.getCategorie().getId() : null);
        ps.setBoolean(8, client.isEstAnonyme());
        ps.setString(9, client.getTypeClient().name());
        ps.setString(10, client.getLivreurRattache() != null ? client.getLivreurRattache().getId() : null);
        ps.setInt(11, client.getDelaiPaiement());
        ps.setBigDecimal(12, client.getPlafondCredit());
        ps.setBigDecimal(13, client.getSoldePrecedent());
        ps.setBigDecimal(14, client.getSoldeActuel());
        ps.setString(15, client.getStatut().name());
        ps.setString(16, client.getNotes());
    }

    private String findIdByCode(String code, Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM client WHERE code=?")) {
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("id") : null;
        }
    }

    private Client mapRow(ResultSet rs) throws SQLException {
        Client cl = new Client();
        cl.setId(rs.getString("id"));
        cl.setCode(rs.getString("code"));
        cl.setNom(rs.getString("nom"));
        cl.setQuartier(rs.getString("quartier"));
        cl.setVille(rs.getString("ville"));
        cl.setTelephone(rs.getString("telephone"));
        cl.setEmail(rs.getString("email"));
        cl.setEstAnonyme(rs.getBoolean("est_anonyme"));
        String tc = rs.getString("type_client");
        if (tc != null) cl.setTypeClient(Client.TypeClient.valueOf(tc));
        cl.setDelaiPaiement(rs.getInt("delai_paiement"));
        cl.setPlafondCredit(rs.getBigDecimal("plafond_credit"));
        cl.setSoldePrecedent(rs.getBigDecimal("solde_precedent"));
        cl.setSoldeActuel(rs.getBigDecimal("solde_actuel"));
        String st = rs.getString("statut");
        if (st != null) cl.setStatut(Client.Statut.valueOf(st.replace("é","é")));
        cl.setNotes(rs.getString("notes"));
        Timestamp dc = rs.getTimestamp("date_creation");
        if (dc != null) cl.setDateCreation(dc.toLocalDateTime());
        try {
            Date dfd = rs.getDate("derniere_facture_date");
            if (dfd != null) cl.setDerniereFactureDate(dfd.toLocalDate());
        } catch (SQLException ignore) { /* colonne absente si appel depuis un autre contexte */ }
        // Catégorie
        String catId = rs.getString("cat_id");
        if (catId != null) cl.setCategorie(new CategorieClient(catId, rs.getString("cat_nom")));
        // Livreur
        String livId = rs.getString("liv_id");
        if (livId != null) {
            Utilisateur liv = new Utilisateur();
            liv.setId(livId);
            liv.setNomComplet(rs.getString("liv_nom"));
            cl.setLivreurRattache(liv);
        }
        return cl;
    }
}
