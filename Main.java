package robot;

import lejos.hardware.Button;
import lejos.hardware.lcd.LCD;
import lejos.utility.Delay;

/* Programme de TEST de toutes les fonctions de base, dans un seul menu.
  Commandes sur la brique :
   HAUT / BAS pour choisir un test
  ENTRÉE pour lancer le test, et aussi pour quitter parfois 
 ÉCHAP pour quitter un test en direct / quitter le programme

  ÉTAT INITIAL DE LA PINCE : FERMÉE.
   - Au lancement, le programme ne bouge pas la pince : il suppose qu'elle
     est fermée (estOuverte() = false) et prend sa position comme zéro.
   - Tout le reste du code part de là : les scans ultrasons se font pince
     fermée (bras ouverts = risque de gêner le capteur), la pince ne s'ouvre
     qu'au dernier moment, juste avant d'avancer sur le palet, puis se referme.
   - Donc : poser le robot PINCE FERMÉE avant de lancer le programme.
     Si elle est restée ouverte, lancer "Fermer pince" en premier.
  */

public class Main {

    private static final String[] TESTS = {
        "Avancer 30cm",
        "Reculer 30cm",
        "Tourner 180",
        "Tourner 360",
        "Orienter cap 0",
        "Ouvrir pince",
        "Fermer pince",
        "Capteurs direct",
        "Chercher palet",
        "Mesurer retard",
        "Scan 360",
        "Reglage pince",
    };

    private static final int LIGNES_VISIBLES = 7; // 8 lignes sur l'écran, la 1re = titre

    private static Deplacement dep;
    private static Pince pince;
    private static Capteurs capteurs;
    private static Attrapeur attrapeur;

    public static void main(String[] args) {
        afficher("Initialisation...");
        dep = new Deplacement();
        pince = new Pince();
        capteurs = new Capteurs();
        attrapeur = new Attrapeur(dep, pince, capteurs);

        int choix = 0;
        while (true) {
            dessinerMenu(choix);
            int bouton = Button.waitForAnyPress();
            if (bouton == Button.ID_UP)        choix = (choix - 1 + TESTS.length) % TESTS.length;
            else if (bouton == Button.ID_DOWN) choix = (choix + 1) % TESTS.length;
            else if (bouton == Button.ID_ENTER) lancer(choix);
            else if (bouton == Button.ID_ESCAPE) break;
        }

        // Libere les ports, sinon ils restent occupes d'une execution a l'autre
        capteurs.fermer();
    }

    
    // Lancement des tests
  private static void lancer(int choix) {
        LCD.clear();
        LCD.drawString(TESTS[choix], 0, 0);

        // Le test des capteurs gere son propre affichage et sa propre sortie
        if (choix == 7) {
            testerCapteurs();
            return;
        }
        // Reglage manuel de la pince aux boutons (gere son affichage et sa sortie)
        if (choix == 11) {
            ReglagePince.lancer(pince);
            return;
        }

        try {
            switch (choix) {
                case 0:  dep.avancer(30); break;
                case 1:  dep.reculer(30); break;
                case 2:  dep.tourner(180); break;
                case 3:  dep.tourner(360); break;  
                case 4:  dep.orienterVers(Config.CAP_CAGE_ADVERSE); break;
                case 5:  pince.ouvrir(); break;
                case 6:  pince.fermer(); break;
                case 8:  attrapeur.chercherEtAttraper(); break;
                case 9:  TestRetard.lancer(attrapeur); break;
                case 10: Scan360.lancer(attrapeur); break;
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

    /**
     * Affiche les valeurs des trois capteurs en continu jusqu'a ECHAP.
     * Sert a relever les valeurs reelles du terrain pour regler les seuils,
     * en particulier Config.SEUIL_BLANC : promener le robot sur le gris du
     * plateau, sur une ligne blanche puis sur une noire, et noter les RGB.
     */
    private static void testerCapteurs() {
        LCD.clear();
        LCD.drawString("Capteurs - ECHAP", 0, 0);
        while (!Button.ESCAPE.isDown()) {
            float[] c = capteurs.rgb();
            float d = capteurs.distanceCm();
            ligne(2, "Dist " + (Float.isInfinite(d) ? "MAX" : String.valueOf((int) d)) + " cm");
            ligne(3, "Touche " + capteurs.estTouche());
            ligne(4, String.format("R%.2f V%.2f", c[0], c[1]));
            ligne(5, String.format("B%.2f", c[2]));
            ligne(6, "Blanc " + capteurs.surLigneBlanche());
            Delay.msDelay(200);
        }
        attendreRelachement();
    }

    // Utilitaires d'écran
 
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
        do { b = Button.waitForAnyPress(); } while (b != Button.ID_ENTER && b != Button.ID_ESCAPE);
    }

    private static void attendreRelachement() {
        while (Button.ESCAPE.isDown()) Delay.msDelay(10);
    }
}