package com.boulangerie.dao;

import com.boulangerie.model.AutorisationDeblocage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;

public class AutorisationDeblocageDAO {
    private static final Logger log = LoggerFactory.getLogger(AutorisationDeblocageDAO.class);
    private final DatabaseConnection db = DatabaseConnection.getInstance();

    public void save(AutorisationDeblocage a) {
        String sql = """
            INSERT INTO autorisation_deblocage
            (id, client_id, manager_id, date_autorisation, motif, engagement_client,
             montant_autorise, duree_validite)
            VALUES (UUID(),?,?,NOW(),?,?,?,?)
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, a.getClientId());
            ps.setString(2, a.getManagerId());
            ps.setString(3, a.getMotif());
            ps.setString(4, a.getEngagementClient());
            ps.setBigDecimal(5, a.getMontantAutorise());
            ps.setDate(6, a.getDureeValidite() != null ? java.sql.Date.valueOf(a.getDureeValidite()) : null);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("save AutorisationDeblocage", e);
            throw new RuntimeException(e);
        }
    }

    public List<AutorisationDeblocage> findByClient(String clientId) {
        List<AutorisationDeblocage> list = new ArrayList<>();
        String sql = "SELECT * FROM autorisation_deblocage WHERE client_id=? ORDER BY date_autorisation DESC";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                AutorisationDeblocage a = new AutorisationDeblocage();
                a.setId(rs.getString("id"));
                a.setClientId(rs.getString("client_id"));
                a.setManagerId(rs.getString("manager_id"));
                a.setMotif(rs.getString("motif"));
                a.setEngagementClient(rs.getString("engagement_client"));
                a.setMontantAutorise(rs.getBigDecimal("montant_autorise"));
                Date dv = rs.getDate("duree_validite");
                if (dv != null) a.setDureeValidite(dv.toLocalDate());
                Timestamp da = rs.getTimestamp("date_autorisation");
                if (da != null) a.setDateAutorisation(da.toLocalDateTime());
                list.add(a);
            }
        } catch (SQLException e) {
            log.error("findByClient {}", clientId, e);
        }
        return list;
    }
}
