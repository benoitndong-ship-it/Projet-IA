package robot;

import lejos.hardware.Button;
import lejos.utility.Delay;

/**
 * Arret d'urgence : un appui sur le bouton central (ENTREE) arrete le robot
 * pendant un match ou une recherche de palet, et rend la main au programme
 * (retour au menu), sans avoir a redemarrer la brique.
 *
 * Fonctionnement :
 *  - armer() lance un "veilleur" qui regarde le bouton toutes les 20 ms ;
 *  - a l'appui, il arrete les roues tout de suite (le mouvement en cours
 *    s'interrompt) et leve un drapeau ;
 *  - le code du robot appelle verifier() dans ses boucles : si le drapeau
 *    est leve, verifier() lance une exception Arret, qui remonte jusqu'a
 *    celui qui a arme (Main ou Match) ;
 *  - desarmer() arrete le veilleur.
 */
public class ArretUrgence {

    /** Levee par verifier() quand l'arret est demande. */
    public static class Arret extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static volatile boolean demande = false;
    private static volatile boolean actif = false;

    /** Lance la surveillance du bouton central. */
    public static void armer(final Deplacement dep) {
        demande = false;
        // On attend que le bouton qui a lance l'action soit relache
        while (Button.ENTER.isDown()) Delay.msDelay(10);
        actif = true;
        Thread veilleur = new Thread() {
            public void run() {
                while (actif) {
                    if (Button.ENTER.isDown()) {
                        demande = true;
                        dep.arreter();        // coupe le mouvement en cours
                        actif = false;
                    }
                    Delay.msDelay(20);
                }
            }
        };
        veilleur.setDaemon(true);   // ne retient pas la fin du programme
        veilleur.start();
    }

    /** Arrete la surveillance. */
    public static void desarmer() {
        actif = false;
    }

    /** A appeler dans les boucles : interrompt l'action si l'arret est demande. */
    public static void verifier() {
        if (demande) throw new Arret();
    }

    /** Vrai si l'arret a ete demande. */
    public static boolean estDemande() {
        return demande;
    }
}