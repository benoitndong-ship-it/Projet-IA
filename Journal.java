package robot;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Journal d'un match : une ligne par etape dans journal.csv (sur la brique),
 * pour savoir apres coup ou le chemin a marche et ou il a deraille.
 * Colonnes : temps (s) ; etape ; resultat ; x ; y ; cap.
 * Recuperation : scp root@<adresse de la brique>:/home/lejos/programs/journal.csv .
 */
public class Journal {

    private static long debut = System.currentTimeMillis();

    /** A appeler au debut d'un match : remet le chrono a zero et ecrit un separateur. */
    public static void nouveauMatch(String nom) {
        debut = System.currentTimeMillis();
        ecrire("---", nom, 0, 0, 0);
    }

    public static void ecrire(String etape, String resultat, double x, double y, double cap) {
        if (!Config.JOURNAL) return;
        PrintWriter out = null;
        try {
            out = new PrintWriter(new FileWriter("journal.csv", true));
            out.println(((System.currentTimeMillis() - debut) / 100) / 10.0 + ";" + etape + ";"
                    + resultat + ";" + Math.round(x) + ";" + Math.round(y) + ";" + Math.round(cap));
        } catch (IOException e) {
            // pas de journal : le match continue
        } finally {
            if (out != null) out.close();
        }
    }
}