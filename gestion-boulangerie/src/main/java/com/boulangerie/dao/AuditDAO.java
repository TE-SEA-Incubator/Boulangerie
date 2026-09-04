package com.boulangerie.dao;

import com.boulangerie.model.JournalAudit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;

public class AuditDAO {
    private static final Logger log = LoggerFactory.getLogger(AuditDAO.class);
    private final DatabaseConnection db = DatabaseConnection.getInstance();

    public void log(JournalAudit entry) {
        String sql = """
            INSERT INTO journal_audit (id,entite,entite_id,action,utilisateur_id,
            login_utilisateur,details,ip_address,date_action)
            VALUES (UUID(),?,?,?,?,?,?,?,NOW())
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, entry.getEntite());
            ps.setString(2, entry.getEntiteId());
            ps.setString(3, entry.getAction());
            ps.setString(4, entry.getUtilisateurId());
            ps.setString(5, entry.getLoginUtilisateur());
            ps.setString(6, entry.getDetails());
            ps.setString(7, entry.getIpAddress());
            ps.executeUpdate();
        } catch (SQLException e) {
            // Le journal d'audit ne doit jamais faire échouer l'opération principale
            log.error("Erreur journalisation audit", e);
        }
    }

    public List<JournalAudit> search(String entite, String action, String utilisateurId,
                                     LocalDate du, LocalDate au, int limit, int offset) {
        List<JournalAudit> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM journal_audit WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (entite != null && !entite.isBlank()) {
            sql.append(" AND entite=?"); params.add(entite);
        }
        if (action != null && !action.isBlank()) {
            sql.append(" AND action=?"); params.add(action);
        }
        if (utilisateurId != null && !utilisateurId.isBlank()) {
            sql.append(" AND utilisateur_id=?"); params.add(utilisateurId);
        }
        if (du != null) { sql.append(" AND DATE(date_action) >= ?"); params.add(java.sql.Date.valueOf(du)); }
        if (au != null) { sql.append(" AND DATE(date_action) <= ?"); params.add(java.sql.Date.valueOf(au)); }
        sql.append(" ORDER BY date_action DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            log.error("search audit", e);
        }
        return list;
    }

    public int count(String entite, String action, String utilisateurId, LocalDate du, LocalDate au) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM journal_audit WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (entite != null && !entite.isBlank()) { sql.append(" AND entite=?"); params.add(entite); }
        if (action != null && !action.isBlank()) { sql.append(" AND action=?"); params.add(action); }
        if (utilisateurId != null && !utilisateurId.isBlank()) { sql.append(" AND utilisateur_id=?"); params.add(utilisateurId); }
        if (du != null) { sql.append(" AND DATE(date_action) >= ?"); params.add(java.sql.Date.valueOf(du)); }
        if (au != null) { sql.append(" AND DATE(date_action) <= ?"); params.add(java.sql.Date.valueOf(au)); }
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            log.error("count audit", e);
            return 0;
        }
    }

    private JournalAudit mapRow(ResultSet rs) throws SQLException {
        JournalAudit a = new JournalAudit();
        a.setId(rs.getString("id"));
        a.setEntite(rs.getString("entite"));
        a.setEntiteId(rs.getString("entite_id"));
        a.setAction(rs.getString("action"));
        a.setUtilisateurId(rs.getString("utilisateur_id"));
        a.setLoginUtilisateur(rs.getString("login_utilisateur"));
        a.setDetails(rs.getString("details"));
        a.setIpAddress(rs.getString("ip_address"));
        Timestamp da = rs.getTimestamp("date_action");
        if (da != null) a.setDateAction(da.toLocalDateTime());
        return a;
    }
}
