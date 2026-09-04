package com.boulangerie.dao;

import com.boulangerie.model.RemiseExceptionnelle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;

public class RemiseExceptionnelleDAO {
    private static final Logger log = LoggerFactory.getLogger(RemiseExceptionnelleDAO.class);
    private final DatabaseConnection db = DatabaseConnection.getInstance();

    /** Trouve la remise active applicable pour un produit à une date et quantité. */
    public Optional<RemiseExceptionnelle> findApplicable(String produitId, LocalDate date, int quantite) {
        String sql = """
            SELECT * FROM remise_exceptionnelle
            WHERE produit_id=? AND actif=1
              AND date_debut <= ? AND date_fin >= ?
              AND quantite_min <= ?
            ORDER BY prix_accordé ASC
            LIMIT 1
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, produitId);
            ps.setDate(2, java.sql.Date.valueOf(date));
            ps.setDate(3, java.sql.Date.valueOf(date));
            ps.setInt(4, quantite);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
        } catch (SQLException e) {
            log.error("findApplicable remise produit={}", produitId, e);
        }
        return Optional.empty();
    }

    public List<RemiseExceptionnelle> findByProduit(String produitId) {
        List<RemiseExceptionnelle> list = new ArrayList<>();
        String sql = "SELECT * FROM remise_exceptionnelle WHERE produit_id=? ORDER BY date_debut DESC";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, produitId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            log.error("findByProduit {}", produitId, e);
        }
        return list;
    }

    public void save(RemiseExceptionnelle r) {
        String sql = """
            INSERT INTO remise_exceptionnelle
            (id, produit_id, motif, prix_normal, prix_accorde, quantite_min,
             date_debut, date_fin, actif, cree_par)
            VALUES (UUID(),?,?,?,?,?,?,?,?,?)
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, r.getProduitId());
            ps.setString(2, r.getMotif());
            ps.setBigDecimal(3, r.getPrixNormal());
            ps.setBigDecimal(4, r.getPrixAccorde());
            ps.setInt(5, r.getQuantiteMin());
            ps.setDate(6, java.sql.Date.valueOf(r.getDateDebut()));
            ps.setDate(7, java.sql.Date.valueOf(r.getDateFin()));
            ps.setBoolean(8, r.isActif());
            ps.setString(9, r.getCreePar());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("save RemiseExceptionnelle", e);
            throw new RuntimeException(e);
        }
    }

    public void deactivate(String id) {
        String sql = "UPDATE remise_exceptionnelle SET actif=0 WHERE id=?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("deactivate remise {}", id, e);
        }
    }

    private RemiseExceptionnelle mapRow(ResultSet rs) throws SQLException {
        RemiseExceptionnelle r = new RemiseExceptionnelle();
        r.setId(rs.getString("id"));
        r.setProduitId(rs.getString("produit_id"));
        r.setMotif(rs.getString("motif"));
        r.setPrixNormal(rs.getBigDecimal("prix_normal"));
        r.setPrixAccorde(rs.getBigDecimal("prix_accorde"));
        r.setQuantiteMin(rs.getInt("quantite_min"));
        Date dd = rs.getDate("date_debut");
        if (dd != null) r.setDateDebut(dd.toLocalDate());
        Date df = rs.getDate("date_fin");
        if (df != null) r.setDateFin(df.toLocalDate());
        r.setActif(rs.getBoolean("actif"));
        r.setCreePar(rs.getString("cree_par"));
        Timestamp dc = rs.getTimestamp("date_creation");
        if (dc != null) r.setDateCreation(dc.toLocalDateTime());
        return r;
    }
}
