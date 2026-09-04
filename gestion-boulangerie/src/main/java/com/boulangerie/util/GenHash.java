package com.boulangerie.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Utilitaire de génération de hash BCrypt.
 * Usage : mvn exec:java -Dexec.mainClass="com.boulangerie.util.GenHash" -Dexec.args="MonMotDePasse"
 */
public class GenHash {
    public static void main(String[] args) {
        String[] passwords = args.length > 0 ? args : new String[]{"Admin@2025"};

        for (String pwd : passwords) {
            String hash = BCrypt.hashpw(pwd, BCrypt.gensalt(10));
            System.out.println("Mot de passe : " + pwd);
            System.out.println("Hash BCrypt  : " + hash);
            System.out.println("Vérification : " + BCrypt.checkpw(pwd, hash));

            // Tester aussi le hash existant dans le schéma
            String oldHash = "$2a$12$eLlMFzZn/nMbEJv5yQqJe.O1LhsniamM8vFKCBPCX.OT4tSM7JQIK";
            System.out.println("Hash schema.sql valide pour '" + pwd + "' : "
                + BCrypt.checkpw(pwd, oldHash));
            System.out.println("---");
        }
    }
}
