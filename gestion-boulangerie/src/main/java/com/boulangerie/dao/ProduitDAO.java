package com.boulangerie.dao;

import com.boulangerie.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;

public class ProduitDAO {
    private static final Logger log = LoggerFactory.getLogger(ProduitDAO.class);
    private final DatabaseConnection db = DatabaseConnection.getInstance();

    // ── Lister tous les produits ─────────────────────────────────
    public List<Produit> findAll(boolean inclureInactifs) {
        List<Produit> list = new ArrayList<>();
        String sql = """
            SELECT p.id, p.code, p.libelle, p.unite, p.statut, p.seuil_alerte,
                   p.description, p.date_creation,
                   f.id AS fam_id, f.nom AS fam_nom
            FROM produit p LEFT JOIN famille f ON p.famille_id = f.id
            """ + (inclureInactifs ? "" : "WHERE p.statut = 'Actif' ") + "ORDER BY p.code";
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            log.error("findAll produits", e);
        }
        return list;
    }

    // ── Recherche textuelle ──────────────────────────────────────
    public List<Produit> search(String texte, String familleId, boolean inclureInactifs) {
        List<Produit> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT p.id, p.code, p.libelle, p.unite, p.statut, p.seuil_alerte,
                   p.description, p.date_creation,
                   f.id AS fam_id, f.nom AS fam_nom
            FROM produit p LEFT JOIN famille f ON p.famille_id = f.id WHERE 1=1
            """);
        List<Object> params = new ArrayList<>();
        if (texte != null && !texte.isBlank()) {
            sql.append(" AND (p.code LIKE ? OR p.libelle LIKE ?)");
            params.add("%" + texte + "%"); params.add("%" + texte + "%");
        }
        if (familleId != null && !familleId.isBlank()) {
            sql.append(" AND p.famille_id = ?"); params.add(familleId);
        }
        if (!inclureInactifs) sql.append(" AND p.statut = 'Actif'");
        sql.append(" ORDER BY p.code");
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            log.error("search produits", e);
        }
        return list;
    }

    public Optional<Produit> findById(String id) {
        String sql = """
            SELECT p.id, p.code, p.libelle, p.unite, p.statut, p.seuil_alerte,
                   p.description, p.date_creation,
                   f.id AS fam_id, f.nom AS fam_nom
            FROM produit p LEFT JOIN famille f ON p.famille_id = f.id WHERE p.id=?
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Produit p = mapRow(rs);
                p.setTarifs(findTarifs(id, c));
                return Optional.of(p);
            }
        } catch (SQLException e) {
            log.error("findById produit {}", id, e);
        }
        return Optional.empty();
    }

    // ── Sauvegarder / Mettre à jour ──────────────────────────────
    public String save(Produit p) {
        String sql = """
            INSERT INTO produit (id, code, libelle, famille_id, unite, statut, seuil_alerte, description)
            VALUES (UUID(), ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, p.getCode());
            ps.setString(2, p.getLibelle());
            ps.setString(3, p.getFamille() != null ? p.getFamille().getId() : null);
            ps.setString(4, p.getUnite());
            ps.setString(5, p.getStatut().name());
            ps.setInt(6, p.getSeuilAlerte());
            ps.setString(7, p.getDescription());
            ps.executeUpdate();
            // Récupérer l'UUID généré
            String idGenere = findIdByCode(p.getCode(), c);
            return idGenere;
        } catch (SQLException e) {
            log.error("save produit", e);
            throw new RuntimeException(e);
        }
    }

    public void update(Produit p) {
        String sql = """
            UPDATE produit SET code=?, libelle=?, famille_id=?, unite=?,
            statut=?, seuil_alerte=?, description=? WHERE id=?
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, p.getCode());
            ps.setString(2, p.getLibelle());
            ps.setString(3, p.getFamille() != null ? p.getFamille().getId() : null);
            ps.setString(4, p.getUnite());
            ps.setString(5, p.getStatut().name());
            ps.setInt(6, p.getSeuilAlerte());
            ps.setString(7, p.getDescription());
            ps.setString(8, p.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("update produit", e);
            throw new RuntimeException(e);
        }
    }

    // ── Tarifs ────────────────────────────────────────────────────
    public List<Tarif> findTarifs(String produitId) {
        try (Connection c = db.getConnection()) {
            return findTarifs(produitId, c);
        } catch (SQLException e) {
            log.error("findTarifs {}", produitId, e);
            return Collections.emptyList();
        }
    }

    private List<Tarif> findTarifs(String produitId, Connection c) throws SQLException {
        List<Tarif> list = new ArrayList<>();
        String sql = "SELECT * FROM tarif WHERE produit_id=? ORDER BY type_tarif";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, produitId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Tarif t = new Tarif();
                t.setId(rs.getString("id"));
                t.setProduitId(produitId);
                t.setTypeTarif(Tarif.TypeTarif.valueOf(rs.getString("type_tarif")));
                t.setMontant(rs.getBigDecimal("montant"));
                Date dd = rs.getDate("date_debut");
                if (dd != null) t.setDateDebut(dd.toLocalDate());
                Date df = rs.getDate("date_fin");
                if (df != null) t.setDateFin(df.toLocalDate());
                t.setStatut(Tarif.Statut.valueOf(rs.getString("statut")));
                list.add(t);
            }
        }
        return list;
    }

    public void saveTarif(Tarif t) {
        String sql = """
            INSERT INTO tarif (id, produit_id, type_tarif, montant, date_debut, date_fin, statut)
            VALUES (UUID(), ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, t.getProduitId());
            ps.setString(2, t.getTypeTarif().name());
            ps.setBigDecimal(3, t.getMontant());
            ps.setDate(4, java.sql.Date.valueOf(t.getDateDebut()));
            ps.setDate(5, t.getDateFin() != null ? java.sql.Date.valueOf(t.getDateFin()) : null);
            ps.setString(6, t.getStatut().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("saveTarif", e);
            throw new RuntimeException(e);
        }
    }

    public void updateTarif(Tarif t) {
        String sql = "UPDATE tarif SET montant=?, date_debut=?, date_fin=?, statut=? WHERE id=?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBigDecimal(1, t.getMontant());
            ps.setDate(2, java.sql.Date.valueOf(t.getDateDebut()));
            ps.setDate(3, t.getDateFin() != null ? java.sql.Date.valueOf(t.getDateFin()) : null);
            ps.setString(4, t.getStatut().name());
            ps.setString(5, t.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("updateTarif", e);
            throw new RuntimeException(e);
        }
    }

    // ── Familles ─────────────────────────────────────────────────
    public List<Famille> findAllFamilles() {
        List<Famille> list = new ArrayList<>();
        String sql = "SELECT id, nom FROM famille ORDER BY nom";
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(new Famille(rs.getString("id"), rs.getString("nom")));
        } catch (SQLException e) {
            log.error("findAllFamilles", e);
        }
        return list;
    }

    // ── Compter les produits ─────────────────────────────────────
    public int countActifs() {
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM produit WHERE statut='Actif'")) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            log.error("countActifs", e);
        }
        return 0;
    }

    // ── Helpers ──────────────────────────────────────────────────
    private String findIdByCode(String code, Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM produit WHERE code=?")) {
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("id") : null;
        }
    }

    private Produit mapRow(ResultSet rs) throws SQLException {
        Produit p = new Produit();
        p.setId(rs.getString("id"));
        p.setCode(rs.getString("code"));
        p.setLibelle(rs.getString("libelle"));
        p.setUnite(rs.getString("unite"));
        p.setStatut(Produit.Statut.valueOf(rs.getString("statut")));
        p.setSeuilAlerte(rs.getInt("seuil_alerte"));
        p.setDescription(rs.getString("description"));
        Timestamp dc = rs.getTimestamp("date_creation");
        if (dc != null) p.setDateCreation(dc.toLocalDateTime());
        String famId = rs.getString("fam_id");
        if (famId != null) p.setFamille(new Famille(famId, rs.getString("fam_nom")));
        return p;
    }
}
