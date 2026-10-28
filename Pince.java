package robot;

import lejos.hardware.motor.EV3MediumRegulatedMotor;
import lejos.robotics.RegulatedMotor;
import lejos.utility.Delay;

/**
 * Ouvrir / fermer les pinces.
 *
 * Le zero moteur correspond a la position FERMEE.
 * Comme le robot peut etre pose pinces ouvertes, le constructeur appelle
 * calibrer() : on ferme a fond jusqu'a la butee mecanique, puis on remet le
 * compteur a zero sur cette position. Apres ca, les positions absolues
 * (rotateTo) sont fiables quelle que soit la position de depart.
 *
 * Si la pince utilise un grand moteur, remplacer par EV3LargeRegulatedMotor.
 */
public class Pince {

    private final RegulatedMotor moteur;
    private boolean ouverte = false;

    public Pince() {
        moteur = new EV3MediumRegulatedMotor(Config.PORT_PINCE);
        moteur.setSpeed(Config.VITESSE_PINCE);
        calibrer();
    }

    /**
     * Ferme la pince au maximum, jusqu'a la butee mecanique, puis definit
     * cette position comme le zero. A appeler une fois au demarrage.
     */
    public void calibrer() {
        // Vitesse reduite pour ne pas forcer sur le mecanisme en arrivant en butee
        moteur.setSpeed(Config.VITESSE_PINCE / 2);

        // Sens de fermeture = l'inverse du sens d'ouverture
        if (Config.ANGLE_PINCE_OUVERTE >= 0) {
            moteur.backward();
        } else {
            moteur.forward();
        }

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
        moteur.resetTachoCount();   // cette position devient le zero = FERMEE
        moteur.setSpeed(Config.VITESSE_PINCE);
        ouverte = false;
    }

    public void ouvrir() {
        allerA(Config.ANGLE_PINCE_OUVERTE);
        ouverte = true;
    }

    public void fermer() {
        allerA(0);
        ouverte = false;
    }

    public boolean estOuverte() {
        return ouverte;
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
}