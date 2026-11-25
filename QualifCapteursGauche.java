package robot;

/**
 * Qualification avec capteurs, depart sur le R de gauche (vu de derriere le robot,
 * face a la cage adverse). Lancer ce programme a distance au coup d'envoi.
 * Bouton central = arret d'urgence.
 */
public class QualifCapteursGauche {
    public static void main(String[] args) {
        Deplacement dep = new Deplacement();
        Pince pince = new Pince();
        Capteurs capteurs = new Capteurs();
        Attrapeur attrapeur = new Attrapeur(dep, pince, capteurs);
        try {
            QualifCapteurs.jouerAvecArret(dep, pince, capteurs, attrapeur, 150);
        } finally {
            capteurs.fermer();
        }
    }
}