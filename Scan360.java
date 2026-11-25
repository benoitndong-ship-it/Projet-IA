package robot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import lejos.hardware.lcd.LCD;

/**
 * Scan 360 de test, lance depuis le menu de Main (entree "Scan 360").
 *
 * Utilise EXACTEMENT le meme scan que le match (Attrapeur.balayerTout), donc
 * tous les reglages viennent de Config :
 *  - rotation continue a Config.VITESSE_SCAN,
 *  - une mesure tous les Config.PAS_MESURE_SCAN degres,
 *  - angles corriges du retard Config.RETARD_ULTRASONS_MS,
 *  - palets reconnus avec les intervalles DISTANCE_MIN/MAX_PALET, LARGEUR_MIN/MAX_PALET.
 * Le profil complet est ajoute a scan.csv si Config.DEBUG_SCAN = true.
 *
 * Ce que l'on voit ici est donc ce que le robot verra en match.
 *
 * Affichage sur les lignes 0 a 3 et 6 : Main ecrit ensuite la pose
 * (lignes 4-5) et "Entree: retour" (ligne 7).
 */
public class Scan360 {

    public static void lancer(Attrapeur att) {
        LCD.clear();
        LCD.drawString("Scan 360...", 0, 0);

        long debut = System.currentTimeMillis();
        ArrayList<Attrapeur.Candidat> palets = att.balayerTout();
        double duree = (System.currentTimeMillis() - debut) / 1000.0;

        // Du plus proche au plus loin
        Collections.sort(palets, new Comparator<Attrapeur.Candidat>() {
            public int compare(Attrapeur.Candidat a, Attrapeur.Candidat b) {
                return Double.compare(a.distance, b.distance);
            }
        });

        LCD.clear();
        LCD.drawString(palets.size() + " palet(s) " + arrondi(duree) + "s", 0, 0);
        if (palets.isEmpty()) {
            LCD.drawString("Aucun palet vu", 0, 1);
            return;
        }
        // 3 lignes disponibles pour les palets
        for (int i = 0; i < palets.size() && i < 3; i++) {
            Attrapeur.Candidat c = palets.get(i);
            LCD.drawString((i + 1) + ") " + Math.round(c.angle) + "deg "
                    + Math.round(c.distance) + "cm", 0, i + 1);
        }
        if (palets.size() > 3) {
            LCD.drawString("+" + (palets.size() - 3) + " autre(s)", 0, 6);
        }
    }

    private static String arrondi(double v) {
        return String.valueOf(Math.round(v * 10.0) / 10.0);
    }
}