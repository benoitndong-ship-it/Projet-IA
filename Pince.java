package robot;

import lejos.hardware.motor.EV3MediumRegulatedMotor;
import lejos.robotics.RegulatedMotor;
import lejos.utility.Delay;

/**
 * Ouvrir / fermer les pinces.
 *
 * PRINCIPE : la fermeture ne vise pas une position memorisee, elle pousse
 * jusqu'a la butee mecanique et repose le zero a cet endroit. Consequence,
 * on peut relancer fermer() autant de fois qu'on veut : la pince se referme
 * toujours au maximum de ce que la mecanique permet, quelle que soit sa
 * position de depart et sans qu'aucune reference ne puisse se decaler.
 *
 * L'ouverture, elle, vise la position Config.ANGLE_PINCE_OUVERTE, comptee
 * depuis ce zero. Elle reste en position absolue a dessein : c'est ce qui
 * empeche d'ouvrir "deux fois" et de forcer sur la butee d'ouverture.
 *
 * Le constructeur ne fait rien bouger : au lancement, la pince reste ou elle
 * est. La premiere fermeture etablira le zero.
 *
 * Si la pince utilise un grand moteur, remplacer par EV3LargeRegulatedMotor.
 */
public class Pince {

    /** Marge de securite entre la butee d'ouverture et la position utilisee. */
    static final int MARGE_BUTEE = 40;

    private final RegulatedMotor moteur;
    private boolean ouverte = false;

    public Pince() {
        moteur = new EV3MediumRegulatedMotor(Config.PORT_PINCE);
        moteur.setSpeed(Config.VITESSE_PINCE);
        // Aucun mouvement ici : la position de depart devient le zero provisoire.
        moteur.resetTachoCount();
        ouverte = false;
    }

    // =====================================================================
    // Mouvements
    // =====================================================================

    /**
     * Ferme la pince au maximum, jusqu'a la butee mecanique, et repose le
     * zero a cet endroit. Peut etre appelee autant de fois que voulu.
     *
     * Si un palet bloque la fermeture, la detection d'arret s'en apercoit de
     * la meme maniere et le moteur s'arrete en serrant le palet.
     */
    public void fermer() {
        pousserJusquaButee(false);
        moteur.resetTachoCount();   // cette position devient le zero = FERMEE
        ouverte = false;
    }

    /**
     * Ouvre la pince a l'angle Config.ANGLE_PINCE_OUVERTE, sans chercher la
     * butee et sans detection de blocage.
     *
     * A appeler pince FERMEE : l'angle est compte depuis le zero. Lancee
     * pince deja ouverte apres un redemarrage, elle forcerait sur la butee
     * d'ouverture pendant DELAI_PINCE_MS.
     */
    public void ouvrir() {
        // Aucune verification : on va directement a l'angle de Config,
        // compte depuis le zero (= pince fermee). Seule securite : allerA
        // coupe le moteur au bout de Config.DELAI_PINCE_MS.
        allerA(Config.ANGLE_PINCE_OUVERTE);
        ouverte = true;
    }

    /**
     * Conservee pour le menu : le calibrage et la fermeture sont desormais
     * la meme operation, puisque fermer() repose le zero a chaque fois.
     */
    public void calibrer() {
        fermer();
    }

    /**
     * Mesure jusqu'ou la pince peut s'ouvrir, pour regler ANGLE_PINCE_OUVERTE
     * sans taper dans la butee a chaque ouverture.
     *
     * Ouvre lentement jusqu'a l'arret mecanique, lit le compteur, puis recule
     * d'une marge et s'arrete la. La valeur renvoyee est celle a reporter dans
     * Config.ANGLE_PINCE_OUVERTE.
     *
     * A lancer APRES une fermeture, sinon le zero n'est pas pose et la valeur
     * ne veut rien dire.
     */
    public int mesurerOuvertureMax() {
        int butee = pousserJusquaButee(true);

        // On se replie d'une marge pour ne plus jamais forcer sur la butee
        int conseille = (butee >= 0) ? butee - MARGE_BUTEE : butee + MARGE_BUTEE;
        allerA(conseille);
        ouverte = true;
        return conseille;
    }

    // =====================================================================
    // Etat
    // =====================================================================

    public boolean estOuverte() {
        return ouverte;
    }

    /** Position actuelle du moteur, utile pour regler ANGLE_PINCE_OUVERTE. */
    public int position() {
        return moteur.getTachoCount();
    }

    // =====================================================================
    // Interne
    // =====================================================================

    /**
     * Pousse le moteur dans un sens jusqu'a ce qu'il ne tourne plus, signe
     * qu'une butee mecanique (ou un palet) est atteinte. Renvoie la position.
     *
     * @param versOuverture true pour ouvrir, false pour fermer
     */
    private int pousserJusquaButee(boolean versOuverture) {
        // Vitesse reduite pour ne pas forcer sur le mecanisme en arrivant en butee
        moteur.setSpeed(Config.VITESSE_PINCE / 2);

        boolean sensPositif = (Config.ANGLE_PINCE_OUVERTE >= 0);
        if (versOuverture == sensPositif) {
            moteur.forward();
        } else {
            moteur.backward();
        }

        // On laisse au moteur le temps de demarrer : une pince coincee contre
        // une butee met plus de 150 ms a se decoller, et etait prise pour bloquee.
        Delay.msDelay(500);

        // On avance tant que le compteur bouge ; quand il se fige, on est en butee
        int precedent = moteur.getTachoCount();
        long limite = System.currentTimeMillis() + Config.DELAI_CALIBRAGE_MS;
        while (System.currentTimeMillis() < limite) {
            Delay.msDelay(150);
            int actuel = moteur.getTachoCount();
            if (Math.abs(actuel - precedent) < 3) {
                break;              // le moteur ne tourne plus : butee atteinte
            }
            precedent = actuel;
        }

        moteur.stop();
        moteur.setSpeed(Config.VITESSE_PINCE);
        return moteur.getTachoCount();
    }

    /** Va à une position avec un délai max, pour ne pas bloquer si le moteur force contre le palet. */
    private void allerA(int position) {
        moteur.rotateTo(position, true);
        long fin = System.currentTimeMillis() + Config.DELAI_PINCE_MS;
        while (moteur.isMoving() && System.currentTimeMillis() < fin) {
            Delay.msDelay(10);
        }
        moteur.stop();
    }

    // =====================================================================
    // Reglage manuel (ajout pour ReglagePince)
    // =====================================================================

    /**
     * Tourne la pince de quelques degres, sans aucun controle.
     * Positif = vers l'ouverture, negatif = vers la fermeture.
     * S'arrete au bout d'une seconde au plus, meme si la pince est bloquee.
     */
    public void tourner(int degres) {
        boolean sensPositif = (Config.ANGLE_PINCE_OUVERTE >= 0);
        moteur.rotate(sensPositif ? degres : -degres, true);
        long fin = System.currentTimeMillis() + 1000;
        while (moteur.isMoving() && System.currentTimeMillis() < fin) {
            Delay.msDelay(10);
        }
        moteur.stop();
    }
}