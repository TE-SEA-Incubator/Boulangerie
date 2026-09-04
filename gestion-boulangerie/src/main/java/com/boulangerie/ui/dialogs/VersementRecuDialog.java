package com.boulangerie.ui.dialogs;

import com.boulangerie.dao.FactureDAO;
import com.boulangerie.dao.UtilisateurDAO;
import com.boulangerie.model.*;
import com.boulangerie.service.CaisseService;
import com.boulangerie.service.PdfService;
import com.boulangerie.service.SessionService;
import com.boulangerie.ui.components.RoundedButton;
import com.boulangerie.util.FormatUtil;
import com.boulangerie.util.UIConstants;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dialogue de saisie d'un versement et génération du reçu électronique.
 * Reproduit l'écran n°11 de la Planche 2.
 *
 * Workflow CDC §5.4 :
 *   Pointeur remet fonds → Caissier saisit montant
 *   → Rapprochement attendu/remis → Écart nécessite motif
 *   → Enregistrement → Génération reçu → Mise à jour solde
 */
public class VersementRecuDialog extends JDialog {

    private final CaisseService   caisseService = new CaisseService();
    private final FactureDAO      factureDAO    = new FactureDAO();
    private final UtilisateurDAO  userDAO       = new UtilisateurDAO();
    private final SessionService  session       = SessionService.getInstance();

    private boolean saved = false;
    private Versement versementSauvegarde;

    // Formulaire
    private JComboBox<Facture>     cboFacture;
    private JComboBox<Utilisateur> cboLivreur;
    private JLabel    lblAttendu;
    private JTextField txtRemis;
    private JComboBox<String> cboMode;
    private JTextField txtDate, txtNotes;

    // Aperçu reçu
    private JTextArea txtApercu;

    public VersementRecuDialog(Frame parent, Facture facturePreselectionnee) {
        super(parent, "Caisse — Enregistrer un versement", true);
        setSize(900, 580);
        setLocationRelativeTo(parent);
        buildUI(facturePreselectionnee);
    }

    public VersementRecuDialog(Frame parent) {
        this(parent, null);
    }

    private void buildUI(Facture facturePreselectionnee) {
        setLayout(new BorderLayout());
        getContentPane().setBackground(UIConstants.GRIS_FOND);
        JPanel root = new JPanel(new BorderLayout(16, 0));
        root.setBackground(UIConstants.GRIS_FOND);
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // ── Gauche : formulaire ───────────────────────────────────
        JPanel left = buildFormulairePanel(facturePreselectionnee);

        // ── Droite : aperçu reçu ──────────────────────────────────
        JPanel right = buildApercuPanel();

        root.add(left,  BorderLayout.CENTER);
        root.add(right, BorderLayout.EAST);

        // ── Footer ────────────────────────────────────────────────
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setBackground(UIConstants.GRIS_FOND);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIConstants.GRIS_BORDURE));

        RoundedButton btnEnreg  = new RoundedButton("✓ Générer reçu",  RoundedButton.Style.SUCCESS);
        RoundedButton btnCancel = new RoundedButton("Fermer",            RoundedButton.Style.SECONDARY);
        RoundedButton btnPDF    = new RoundedButton("Export PDF reçu",   RoundedButton.Style.OUTLINE);

        btnEnreg.addActionListener(e  -> enregistrer());
        btnCancel.addActionListener(e -> dispose());
        btnPDF.addActionListener(e    -> exporterRecuPDF());

        footer.add(btnPDF); footer.add(btnCancel); footer.add(btnEnreg);
        add(root,   BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
    }

    private JPanel buildFormulairePanel(Facture facturePre) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Enregistrer un versement"),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        p.setPreferredSize(new Dimension(480, 0));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 8, 6, 8);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill   = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;

        // Facture
        gc.gridx = 0; gc.gridy = 0; gc.weightx = 0;
        p.add(new JLabel("Facture"), gc);
        gc.gridx = 1; gc.weightx = 1;
        cboFacture = new JComboBox<>();
        factureDAO.findByFilters(null, null, null, "EnAttente").forEach(cboFacture::addItem);
        factureDAO.findByFilters(null, null, null, "Partielle").forEach(cboFacture::addItem);
        if (facturePre != null) cboFacture.setSelectedItem(facturePre);
        p.add(cboFacture, gc);
        cboFacture.addActionListener(e -> majAttenduEtApercu());

        // Livreur
        gc.gridx = 0; gc.gridy = 1; gc.weightx = 0;
        p.add(new JLabel("Livreur"), gc);
        gc.gridx = 1; gc.weightx = 1;
        cboLivreur = new JComboBox<>();
        cboLivreur.addItem(null);
        userDAO.findLivreurs().forEach(cboLivreur::addItem);
        p.add(cboLivreur, gc);

        // Montant attendu (automatique)
        gc.gridx = 0; gc.gridy = 2; gc.weightx = 0;
        p.add(new JLabel("Montant attendu (TTC)"), gc);
        gc.gridx = 1;
        lblAttendu = new JLabel("—");
        lblAttendu.setFont(UIConstants.FONT_BOLD);
        lblAttendu.setForeground(UIConstants.GRIS_TEXTE);
        p.add(lblAttendu, gc);

        // Montant reçu
        gc.gridx = 0; gc.gridy = 3; gc.weightx = 0;
        p.add(new JLabel("Montant reçu *"), gc);
        gc.gridx = 1; gc.weightx = 1;
        txtRemis = new JTextField("0,00");
        p.add(txtRemis, gc);
        txtRemis.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { majApercu(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { majApercu(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { majApercu(); }
        });

        // Mode paiement
        gc.gridx = 0; gc.gridy = 4; gc.weightx = 0;
        p.add(new JLabel("Mode de paiement"), gc);
        gc.gridx = 1; gc.weightx = 1;
        cboMode = new JComboBox<>(new String[]{"Espèces","Chèque","Virement","Mobile Money","Autre"});
        p.add(cboMode, gc);

        // Date
        gc.gridx = 0; gc.gridy = 5; gc.weightx = 0;
        p.add(new JLabel("Date de versement"), gc);
        gc.gridx = 1;
        txtDate = new JTextField(FormatUtil.date(LocalDate.now()));
        p.add(txtDate, gc);

        // Notes
        gc.gridx = 0; gc.gridy = 6; gc.weightx = 0;
        p.add(new JLabel("Notes"), gc);
        gc.gridx = 1; gc.weightx = 1;
        txtNotes = new JTextField();
        p.add(txtNotes, gc);

        // Déclencher le calcul initial
        majAttenduEtApercu();
        return p;
    }

    private JPanel buildApercuPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Aperçu du reçu électronique"),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        p.setPreferredSize(new Dimension(340, 0));

        txtApercu = new JTextArea();
        txtApercu.setEditable(false);
        txtApercu.setFont(new Font("Courier New", Font.PLAIN, 12));
        txtApercu.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        txtApercu.setBackground(new Color(0xFFFDE7));
        p.add(new JScrollPane(txtApercu), BorderLayout.CENTER);
        return p;
    }

    // ── Logique ───────────────────────────────────────────────────
    private void majAttenduEtApercu() {
        Facture f = (Facture) cboFacture.getSelectedItem();
        if (f != null) {
            lblAttendu.setText(FormatUtil.montant(f.getMontantTtc()) + " FCFA");
        } else {
            lblAttendu.setText("—");
        }
        majApercu();
    }

    private void majApercu() {
        Facture f     = (Facture) cboFacture.getSelectedItem();
        Utilisateur l = (Utilisateur) cboLivreur.getSelectedItem();
        String mode   = (String) cboMode.getSelectedItem();
        String remisStr = txtRemis.getText().replace(" ","").replace(",",".");

        StringBuilder sb = new StringBuilder();
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("    🥖 BOULANGERIE\n");
        sb.append("  Reçu de versement\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("Date     : ").append(txtDate.getText()).append("\n");
        if (f != null) {
            sb.append("Facture  : ").append(f.getNumero()).append("\n");
            sb.append("Client   : ").append(f.getClient() != null ? f.getClient().getNom() : "—").append("\n");
        }
        if (l != null) sb.append("Livreur  : ").append(l.getNomComplet()).append("\n");
        sb.append("Montant reçu : ").append(txtRemis.getText()).append(" FCFA\n");
        sb.append("Mode     : ").append(mode != null ? mode : "—").append("\n");

        // Écart
        try {
            BigDecimal attendu = f != null ? f.getMontantTtc() : BigDecimal.ZERO;
            BigDecimal remis   = new BigDecimal(remisStr);
            BigDecimal ecart   = remis.subtract(attendu);
            if (ecart.compareTo(BigDecimal.ZERO) != 0) {
                sb.append("⚠ Écart   : ").append(FormatUtil.montant(ecart)).append(" FCFA\n");
            }
        } catch (Exception ignore) {}

        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("Merci pour votre confiance !\n\n");
        sb.append("Signature: _______________");
        txtApercu.setText(sb.toString());
    }

    private void enregistrer() {
        Facture f = (Facture) cboFacture.getSelectedItem();
        if (f == null) {
            JOptionPane.showMessageDialog(this, "Sélectionnez une facture.", "Info", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            BigDecimal attendu = f.getMontantTtc();
            BigDecimal remis   = new BigDecimal(txtRemis.getText().trim().replace(",", "."));
            BigDecimal ecart   = remis.subtract(attendu);

            String motifEcart = null;
            if (ecart.compareTo(BigDecimal.ZERO) != 0) {
                motifEcart = JOptionPane.showInputDialog(this,
                    String.format("Écart détecté : %s FCFA\nMotif obligatoire :", FormatUtil.montant(ecart)));
                if (motifEcart == null || motifEcart.isBlank()) {
                    JOptionPane.showMessageDialog(this,
                        "Le motif de l'écart est obligatoire.", "Validation", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }

            Utilisateur livreur = (Utilisateur) cboLivreur.getSelectedItem();
            LocalDate dateV = FormatUtil.parseDate(txtDate.getText());
            if (dateV == null) dateV = LocalDate.now();

            Versement v = new Versement();
            v.setNumero(new com.boulangerie.dao.VersementDAO().genererNumero());
            v.setFacture(f);
            v.setClient(f.getClient());
            v.setLivreur(livreur);
            v.setMontantAttendu(attendu);
            v.setMontantRemis(remis);
            v.setMontantEnregistre(remis);
            v.setModePaiement((String) cboMode.getSelectedItem());
            v.setMotifEcart(motifEcart);
            v.setDateVersement(dateV);
            v.setCaissier(session.getUtilisateur());

            versementSauvegarde = caisseService.enregistrerVersement(v);
            saved = true;

            JOptionPane.showMessageDialog(this,
                "Versement " + versementSauvegarde.getNumero() + " enregistré avec succès.\nReçu généré.",
                "Succès", JOptionPane.INFORMATION_MESSAGE);
            dispose();

        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Validation", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erreur : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exporterRecuPDF() {
        if (versementSauvegarde == null) {
            JOptionPane.showMessageDialog(this,
                "Enregistrez d'abord le versement.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("Recu_" + versementSauvegarde.getNumero() + ".pdf"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            PdfService.exporterRecu(versementSauvegarde, fc.getSelectedFile().getAbsolutePath());
            JOptionPane.showMessageDialog(this,
                "Reçu PDF exporté : " + fc.getSelectedFile().getName(), "Export PDF", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erreur export PDF : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isSaved()          { return saved; }
    public Versement getVersement()   { return versementSauvegarde; }
}
