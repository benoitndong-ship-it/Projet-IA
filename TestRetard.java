package robot;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import lejos.hardware.Button;
import lejos.hardware.lcd.LCD;
import lejos.utility.Delay;

/**
 * Calibration : mesure le retard de lecture du capteur ultrasons.
 * Se lance depuis le menu de Main (entree "Mesurer retard").
 *
 * Principe : on balaye le meme palet une fois vers la gauche, une fois vers
 * la droite. A cause du retard, il est vu trop loin dans le sens de rotation,
 * donc decale d'un cote a l'aller et de l'autre au retour. La moitie de
 * l'ecart entre les deux angles = le decalage du au retard.
 *     retard (ms) = (angle aller - angle retour) / 2 / VITESSE_SCAN * 1000
 *
 * Mise en place :
 *  - un seul palet devant le robot, a ~50 cm (dans [DISTANCE_MIN_PALET ; DISTANCE_MAX_PALET]),
 *    rien d'autre a moins de 70 cm dans les +/- 45 degres ;
 *  - Config.VITESSE_SCAN reglee a la vitesse utilisee en match.
 *
 * Si Config.RETARD_ULTRASONS_MS n'est pas a 0, le test mesure le RESTE a corriger et
 * affiche directement la nouvelle valeur totale a mettre.
 *
 * Le resultat final reste sur les lignes 0 a 3 de l'ecran : Main ecrit
 * ensuite la pose (lignes 4-5) et "Entree: retour" (ligne 7).
 */
public class TestRetard {

    static final int ESSAIS = 4;
    /** Pas fin : a 10 degres, l'angle n'est connu qu'a +/- 5, trop pour voir 3 degres. */
    static final double PAS_FIN_DEG = 2.0;
    static final double AMPLITUDE = 45.0;

    public static void lancer(Attrapeur att) {
        LCD.clear();
        LCD.drawString("Mesure du retard", 0, 0);
        LCD.drawString("1 palet a ~50cm", 0, 2);
        LCD.drawString("devant le robot", 0, 3);
        LCD.drawString("Entree: depart", 0, 5);
        LCD.drawString("Echap: annuler", 0, 6);
        int b;
        do { b = Button.waitForAnyPress(); } while (b != Button.ID_ENTER && b != Button.ID_ESCAPE);
        if (b == Button.ID_ESCAPE) {
            LCD.clear();
            LCD.drawString("Annule", 0, 0);
            return;
        }

        double somme = 0.0;
        int valides = 0;
        PrintWriter out = ouvrirFichier();

        for (int k = 1; k <= ESSAIS; k++) {
            LCD.clear();
            LCD.drawString("Essai " + k + "/" + ESSAIS, 0, 0);

            Attrapeur.Candidat aller = att.plusProche(att.balayer(-AMPLITUDE, AMPLITUDE, PAS_FIN_DEG));
            Attrapeur.Candidat retour = att.plusProche(att.balayer(AMPLITUDE, -AMPLITUDE, PAS_FIN_DEG));

            if (aller == null || retour == null) {
                LCD.drawString("Palet non vu", 0, 2);
                if (out != null) out.println(k + ";non vu");
                Delay.msDelay(1500);
                continue;
            }

            double demiEcart = (aller.angle - retour.angle) / 2.0;
            double retardMs = demiEcart / Config.VITESSE_SCAN * 1000.0;
            somme += retardMs;
            valides++;

            LCD.drawString("aller  " + arrondi(aller.angle), 0, 2);
            LCD.drawString("retour " + arrondi(retour.angle), 0, 3);
            LCD.drawString("retard " + arrondi(retardMs) + " ms", 0, 5);
            if (out != null) {
                out.println(k + ";" + aller.angle + ";" + retour.angle + ";" + retardMs);
            }
            Delay.msDelay(1500);
        }
        if (out != null && valides > 0) {
            double moyenne = somme / valides;
            out.println("moyenne;" + moyenne + ";total a mettre;"
                    + (Config.RETARD_ULTRASONS_MS + moyenne));
        }
        if (out != null) out.close();

        // Resultat sur les lignes 0 a 3 seulement (voir en-tete)
        LCD.clear();
        if (valides == 0) {
            LCD.drawString("Aucun essai valide", 0, 0);
            LCD.drawString("Palet bien place ?", 0, 1);
        } else {
            double moyenne = somme / valides;
            double total = Config.RETARD_ULTRASONS_MS + moyenne;
            LCD.drawString("Ok " + valides + "/" + ESSAIS + " : " + arrondi(moyenne) + "ms", 0, 0);
            LCD.drawString("A mettre (Config)", 0, 1);
            LCD.drawString("RETARD_ULTRASONS", 0, 2);
            LCD.drawString("_MS = " + arrondi(total), 0, 3);
        }
    }

    private static String arrondi(double v) {
        return String.valueOf(Math.round(v * 10.0) / 10.0);
    }

    /** retard.csv a cote du programme ; null si impossible (le test continue). */
    private static PrintWriter ouvrirFichier() {
        try {
            PrintWriter out = new PrintWriter(new FileWriter("retard.csv", true));
            out.println("essai;angle aller;angle retour;retard ms");
            return out;
        } catch (IOException e) {
            return null;
        }
    }
}