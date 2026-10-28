package robot;

import lejos.hardware.Button;
import lejos.hardware.lcd.LCD;
import lejos.utility.Delay;

/**
 * Programme de TEST de toutes les fonctions de base, dans un seul menu.
 *
 * Commandes sur la brique :
 *   HAUT / BAS  : choisir un test
 *   ENTRÉE      : lancer le test
 *   ÉCHAP       : quitter un test en direct / quitter le programme
 *
 * Départ : robot posé face à la cage adverse, pinces FERMÉES.
 */
public class Main {

    private static final String[] TESTS = {
        "Avancer 30cm",
        "Reculer 30cm",
        "Tourner +90",
        "Tourner 360",
        "Orienter cap 0",
        "Ouvrir pince",
        "Fermer pince",
    };

    private static final int LIGNES_VISIBLES = 7; // 8 lignes sur l'écran, la 1re = titre

    private static Deplacement dep;
    private static Pince pince;

    public static void main(String[] args) {
        afficher("Initialisation...");
        dep = new Deplacement();
        pince = new Pince();

        int choix = 0;
        while (true) {
            dessinerMenu(choix);
            int bouton = Button.waitForAnyPress();
            if (bouton == Button.ID_UP)        choix = (choix - 1 + TESTS.length) % TESTS.length;
            else if (bouton == Button.ID_DOWN) choix = (choix + 1) % TESTS.length;
            else if (bouton == Button.ID_ENTER) lancer(choix);
            else if (bouton == Button.ID_ESCAPE) break;
        }

    }

    // =====================================================================
    // Lancement des tests
    // =====================================================================

    private static void lancer(int choix) {
        LCD.clear();
        LCD.drawString(TESTS[choix], 0, 0);
        try {
            switch (choix) {
                case 0:  dep.avancer(30); break;
                case 1:  dep.reculer(30); break;
                case 2:  dep.tourner(90); break;
                case 3:  dep.tourner(360); break;  // doit revenir pile à la même position
                case 4:  dep.orienterVers(Config.CAP_CAGE_ADVERSE); break;
                case 5:  pince.ouvrir(); break;
                case 6:  pince.fermer(); break;
            }
        } catch (Exception e) {
            dep.arreter();
            ligne(5, "ERREUR:");
            ligne(6, e.getClass().getSimpleName());
        }
        // Pour les tests de pince, l'etat de la pince est plus parlant que la pose
        if (choix == 5 || choix == 6) {
            afficherEtatPince();
        } else {
            afficherPose();
        }
        ligne(7, "Entree: retour");
        attendreEntreeOuEchap();
    }

    // =====================================================================
    // Utilitaires d'écran
    // =====================================================================

    private static void dessinerMenu(int choix) {
        LCD.clear();
        LCD.drawString("TESTS " + (choix + 1) + "/" + TESTS.length, 0, 0);
        int debut = Math.max(0, Math.min(choix - LIGNES_VISIBLES / 2, TESTS.length - LIGNES_VISIBLES));
        for (int i = 0; i < LIGNES_VISIBLES && debut + i < TESTS.length; i++) {
            int idx = debut + i;
            LCD.drawString((idx == choix ? ">" : " ") + TESTS[idx], 0, i + 1);
        }
    }

    private static void afficherPose() {
        ligne(4, String.format("x%.0f y%.0f", dep.getPose().getX(), dep.getPose().getY()));
        ligne(5, String.format("cap %.0f", dep.getCap()));
    }

    private static void afficherEtatPince() {
        ligne(4, "Pince : " + (pince.estOuverte() ? "OUVERTE" : "FERMEE"));
        ligne(5, "");
    }

    private static void ligne(int y, String texte) {
        LCD.clear(y);
        LCD.drawString(texte, 0, y);
    }

    private static void afficher(String texte) {
        LCD.clear();
        LCD.drawString(texte, 0, 0);
    }

    private static void attendreEntreeOuEchap() {
        int b;
        do { b = Button.waitForAnyPress();
        } 
        while (b != Button.ID_ENTER && b != Button.ID_ESCAPE);
    }

    private static void attendreRelachement() {
        while (Button.ESCAPE.isDown()) Delay.msDelay(10);
    }
}