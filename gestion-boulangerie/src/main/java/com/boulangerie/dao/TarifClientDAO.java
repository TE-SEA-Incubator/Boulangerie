package com.boulangerie.dao;

import com.boulangerie.model.Tarif;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;

/**
 * Accès aux tarifs spécifiques par client (table tarif_client).
 * Priorité (1) dans la règle de résolution du tarif.
 */
public class TarifClientDAO {
    private static final Logger log = LoggerFactory.getLogger(TarifClientDAO.class);
    private final DatabaseConnection db = DatabaseConnection.getInstance();

    /** Trouve le tarif spécifique valide pour un client et un produit à une date donnée. */
    public Optional<java.math.BigDecimal> findPrixSpecifique(String clientId, String produitId, LocalDate date) {
        String sql = """
            SELECT prix FROM tarif_client
            WHERE client_id=? AND produit_id=? AND actif=1
              AND date_debut <= ?
              AND (date_fin IS NULL OR date_fin >= ?)
            ORDER BY date_debut DESC
            LIMIT 1
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ps.setString(2, produitId);
            ps.setDate(3, java.sql.Date.valueOf(date));
            ps.setDate(4, java.sql.Date.valueOf(date));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(rs.getBigDecimal("prix"));
        } catch (SQLException e) {
            log.error("findPrixSpecifique client={} produit={}", clientId, produitId, e);
        }
        return Optional.empty();
    }

    public List<Map<String, Object>> findByClient(String clientId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = """
            SELECT tc.id, tc.produit_id, p.libelle AS produit_libelle,
                   tc.prix, tc.date_debut, tc.date_fin, tc.actif
            FROM tarif_client tc
            JOIN produit p ON tc.produit_id = p.id
            WHERE tc.client_id=?
            ORDER BY p.libelle
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id",               rs.getString("id"));
                row.put("produit_id",        rs.getString("produit_id"));
                row.put("produit_libelle",   rs.getString("produit_libelle"));
                row.put("prix",              rs.getBigDecimal("prix"));
                Date dd = rs.getDate("date_debut");
                row.put("date_debut", dd != null ? dd.toLocalDate() : null);
                Date df = rs.getDate("date_fin");
                row.put("date_fin", df != null ? df.toLocalDate() : null);
                row.put("actif", rs.getBoolean("actif"));
                list.add(row);
            }
        } catch (SQLException e) {
            log.error("findByClient {}", clientId, e);
        }
        return list;
    }

    public void save(String clientId, String produitId, java.math.BigDecimal prix,
                     LocalDate dateDebut, LocalDate dateFin) {
        String sql = """
            INSERT INTO tarif_client (id, client_id, produit_id, prix, date_debut, date_fin, actif)
            VALUES (UUID(), ?, ?, ?, ?, ?, 1)
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ps.setString(2, produitId);
            ps.setBigDecimal(3, prix);
            ps.setDate(4, java.sql.Date.valueOf(dateDebut));
            ps.setDate(5, dateFin != null ? java.sql.Date.valueOf(dateFin) : null);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("save TarifClient", e);
            throw new RuntimeException(e);
        }
    }

    public void deactivate(String tarifClientId) {
        String sql = "UPDATE tarif_client SET actif=0 WHERE id=?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tarifClientId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("deactivate TarifClient {}", tarifClientId, e);
        }
    }
}
