package com.boulangerie.dao;

import com.boulangerie.model.SoldeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;

public class SoldeClientDAO {
    private static final Logger log = LoggerFactory.getLogger(SoldeClientDAO.class);
    private final DatabaseConnection db = DatabaseConnection.getInstance();

    /**
     * Enregistre ou met à jour le solde d'un client pour une date donnée.
     * Garantit : solde clôture J = solde ouverture J+1.
     */
    public void sauvegarder(SoldeClient s) {
        String sql = """
            INSERT INTO solde_client
              (id, client_id, date_solde, solde_ouverture, sorties_du_jour, versements_du_jour, solde_cloture)
            VALUES (UUID(),?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE
              solde_ouverture=VALUES(solde_ouverture),
              sorties_du_jour=VALUES(sorties_du_jour),
              versements_du_jour=VALUES(versements_du_jour),
              solde_cloture=VALUES(solde_cloture)
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, s.getClientId());
            ps.setDate(2, java.sql.Date.valueOf(s.getDateSolde()));
            ps.setBigDecimal(3, s.getSoldeOuverture());
            ps.setBigDecimal(4, s.getSortiesDuJour());
            ps.setBigDecimal(5, s.getVersementsDuJour());
            ps.setBigDecimal(6, s.getSoldeCloture());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("sauvegarder SoldeClient", e);
            throw new RuntimeException(e);
        }
    }

    /** Récupère le solde de clôture du jour J (= ouverture de J+1). */
    public Optional<BigDecimal> getSoldeCloture(String clientId, LocalDate date) {
        String sql = "SELECT solde_cloture FROM solde_client WHERE client_id=? AND date_solde=?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ps.setDate(2, java.sql.Date.valueOf(date));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(rs.getBigDecimal("solde_cloture"));
        } catch (SQLException e) {
            log.error("getSoldeCloture {}", clientId, e);
        }
        return Optional.empty();
    }

    /** Historique de soldes pour un client. */
    public List<SoldeClient> findByClient(String clientId, int limit) {
        List<SoldeClient> list = new ArrayList<>();
        String sql = "SELECT * FROM solde_client WHERE client_id=? ORDER BY date_solde DESC LIMIT ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                SoldeClient s = new SoldeClient();
                s.setId(rs.getString("id"));
                s.setClientId(rs.getString("client_id"));
                Date d = rs.getDate("date_solde");
                if (d != null) s.setDateSolde(d.toLocalDate());
                s.setSoldeOuverture(rs.getBigDecimal("solde_ouverture"));
                s.setSortiesDuJour(rs.getBigDecimal("sorties_du_jour"));
                s.setVersementsDuJour(rs.getBigDecimal("versements_du_jour"));
                s.setSoldeCloture(rs.getBigDecimal("solde_cloture"));
                list.add(s);
            }
        } catch (SQLException e) {
            log.error("findByClient {}", clientId, e);
        }
        return list;
    }
}
