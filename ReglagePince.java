package robot;

import lejos.hardware.Button;
import lejos.hardware.lcd.LCD;

/**
 * Reglage manuel de la pince, un appui = un petit pas.
 * Se lance depuis le menu de Main (entree "Reglage pince").
 *
 * Pas d'appui continu : chaque appui fait tourner la pince de PAS degres.
 *   GAUCHE : ouvrir d'un pas
 *   DROITE : fermer d'un pas
 *   ENTREE : afficher la position (valeur pour ANGLE_PINCE_OUVERTE)
 *   ECHAP  : quitter
 *
 * Pour mesurer l'angle d'ouverture : quitter, lancer "Fermer pince" (pose le
 * zero), revenir ici, ouvrir pas a pas jusqu'a l'ouverture voulue, ENTREE.
 */
public class ReglagePince {

    /** Degres moteur par appui. */
    static final int PAS = 30;

    public static void lancer(Pince pince) {
        LCD.clear();
        LCD.drawString("Reglage pince", 0, 0);
        LCD.drawString("GAUCHE: ouvrir", 0, 1);
        LCD.drawString("DROITE: fermer", 0, 2);
        LCD.drawString("ENTREE: angle", 0, 3);
        LCD.drawString("ECHAP: quitter", 0, 7);

        while (true) {
            ligne(5, "Position " + pince.position());
            int b = Button.waitForAnyPress();
            if (b == Button.ID_LEFT) {
                pince.tourner(PAS);
            } else if (b == Button.ID_RIGHT) {
                pince.tourner(-PAS);
            } else if (b == Button.ID_ENTER) {
                ligne(6, "ANGLE = " + pince.position());
            } else if (b == Button.ID_ESCAPE) {
                break;
            }
        }
    }

    private static void ligne(int y, String texte) {
        LCD.clear(y);
        LCD.drawString(texte, 0, y);
    }
}