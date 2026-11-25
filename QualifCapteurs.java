package robot;

import lejos.hardware.lcd.LCD;
import lejos.robotics.navigation.Pose;

/**
 * Qualification AVEC capteurs, en deux modes.
 *
 * MODE PLAN (tant que tout se passe comme prevu) : ordre fixe du dessin, et
 * chaque etape verifie et fait de petits ajustements :
 *   - palet de depart : scan -> calibrage du robot sur ce palet connu ;
 *   - chaque palet : scan cote terrain -> objet dans la zone ? -> recalage -> saisie ;
 *   - trajets : recalage sur les lignes de couleur ;
 *   - depot : la vue (mur aux ultrasons) decide.
 *
 *        cage adverse
 *     4     3     2        X = 210
 *     5     6     7        X = 150
 *     9     8     1        X = 90
 *        notre cage        (depart sur le R de droite, sous le 1)
 *
 * LE PLAN DERAILLE si une seule de ces alarmes se declenche (en qualification
 * les palets ne bougent pas, donc chacune veut dire que le robot s'est perdu) :
 *   - le palet attendu n'est pas dans sa zone (rien vu, ou hors zone) ;
 *   - le recalage sur un palet est refuse (correction trop grande) ;
 *   - le depot s'est fait au filet de securite (mur jamais vu) ;
 *   - le palet a ete perdu en route.
 *
 * TRACE : ligne droite quand aucun palet restant ne gene (sinon couloirs),
 * depot en face du palet suivant, et pas de demi-tour apres le depot (le
 * trajet suivant tourne directement vers le palet suivant).
 *
 * MODE AUTONOME (apres une alarme, jusqu'a la fin) :
 *   scan 360 -> le palet le plus proche de nous ET des cages adverses -> aller
 *   le prendre -> marquer. Si le scan ne trouve rien : se rapprocher de
 *   l'emplacement theorique du palet restant le plus proche, puis rescanner.
 *
 * Chaque etape est ecrite dans journal.csv : on voit a partir de quand le plan
 * a deraille et pourquoi. Bouton central = arret d'urgence.
 */
public class QualifCapteurs {

    /** L'ordre du dessin, depart a droite : {X, Y} de chaque palet. */
    static final double[][] ORDRE_DROITE = {
        {90, 50},                            // 1 : devant le robot
        {210, 50}, {210, 100}, {210, 150},   // 2, 3, 4
        {150, 150}, {150, 100}, {150, 50},   // 5, 6, 7
        {90, 100}, {90, 150}                 // 8, 9
    };

    static final int A_PRENDRE = 0, MARQUE = 1, ABSENT = 2, PERDU = 3;
    static final char[] SYMBOLES = {'o', 'X', '.', '?'};

    /** Mode autonome : on se rapproche d'un emplacement theorique de ce pas (cm) avant de rescanner. */
    static final double PAS_RAPPROCHEMENT = 50;
    /** ... en s'arretant a cette distance de lui (pour pouvoir le voir). */
    static final double GARDE_RAPPROCHEMENT = 45;
    /** Scans vides de suite (en se rapprochant) avant d'abandonner. */
    static final int SCANS_VIDES_MAX = 4;
    /** Un palet vu a moins de ca d'un emplacement theorique est considere comme lui (cm). */
    static final double RAYON_IDENTIFICATION = 30;

    private final Deplacement dep;
    private final Competences comp;
    private final int[] etat = new int[ORDRE_DROITE.length];
    private final double[][] palets = new double[ORDRE_DROITE.length][];
    private int marques = 0;
    private String alarme = null;

    public QualifCapteurs(Deplacement dep, Competences comp) {
        this.dep = dep;
        this.comp = comp;
    }

    /** @param yDepart 50 = droite (ordre du dessin), 150 = gauche (miroir) */
    public void jouer(double yDepart) {
        long fin = System.currentTimeMillis() + Config.DUREE_MATCH_MS;
        boolean miroir = yDepart > 100;
        for (int n = 0; n < palets.length; n++) {
            palets[n] = new double[] {ORDRE_DROITE[n][0],
                                      miroir ? 200 - ORDRE_DROITE[n][1] : ORDRE_DROITE[n][1]};
        }
        dep.definirPose(Config.X_DEPART, yDepart, Config.CAP_CAGE_ADVERSE);
        comp.apprendreCouleurDepart(yDepart);
        Journal.nouveauMatch("qualif capteurs y=" + (int) yDepart);

        // ---- MODE PLAN ----
        for (int n = 0; n < palets.length && alarme == null; n++) {
            ArretUrgence.verifier();
            if (System.currentTimeMillis() >= fin) { finir(); return; }
            double x = palets[n][0], y = palets[n][1];
            afficher("Plan: palet " + (n + 1));

            comp.definirObstacles(restants(n));
            boolean tenu = (n == 0) ? comp.prendrePaletDepart(x, y) : comp.allerChercher(x, y);
            if (!comp.dernierRecalageAccepte()) alarme = "recalage refuse";
            if (!tenu && n > 0) {
                etat[n] = ABSENT;
                if (alarme == null) alarme = "palet absent";
                noter("palet " + (n + 1), "absent");
                break;
            }
            if (System.currentTimeMillis() >= fin) { finir(); return; }   // on le garde
            etat[n] = MARQUE;                                            // il est dans la pince
            comp.definirObstacles(restants(-1));
            double yProchain = (n + 1 < palets.length) ? palets[n + 1][1] : Double.NaN;
            if (comp.marquer(yProchain)) {
                etat[n] = MARQUE;
                marques++;
                noter("palet " + (n + 1), "marque");
                if (!comp.dernierDepotSurMur() && alarme == null) alarme = "depot sans mur";
            } else {
                etat[n] = PERDU;
                if (alarme == null) alarme = "palet perdu";
                noter("palet " + (n + 1), "perdu");
            }
        }

        // ---- MODE AUTONOME ----
        if (alarme != null) {
            noter("ALARME", alarme);
            modeAutonome(fin);
        }
        finir();
    }

    /** Scan 360 -> meilleur palet -> prendre -> marquer ; sinon se rapprocher d'un emplacement theorique. */
    private void modeAutonome(long fin) {
        int scansVides = 0;
        while (System.currentTimeMillis() < fin) {
            ArretUrgence.verifier();
            afficher("Autonome");
            comp.definirObstacles(null);                   // positions incertaines : couloirs
            double[] vu = comp.chercherAutour();

            if (vu == null) {
                // Rien a portee : on va vers l'emplacement theorique du palet restant le plus proche
                int n = plusProcheRestant();
                if (n < 0) break;                                  // plus rien a chercher
                Pose p = dep.getPose();
                if (Math.hypot(palets[n][0] - p.getX(), palets[n][1] - p.getY()) <= GARDE_RAPPROCHEMENT + 10) {
                    // On est a cote de son emplacement et on ne le voit pas : il n'y est pas
                    etat[n] = ABSENT;
                    noter("scan 360", "palet " + (n + 1) + " absent");
                    continue;
                }
                if (++scansVides > SCANS_VIDES_MAX) break;
                noter("scan 360", "rien -> vers palet " + (n + 1));
                comp.seRapprocher(palets[n][0], palets[n][1], PAS_RAPPROCHEMENT, GARDE_RAPPROCHEMENT);
                continue;
            }
            scansVides = 0;
            int n = identifier(vu);
            noter("scan 360", "vu x" + Math.round(vu[0]) + " y" + Math.round(vu[1])
                              + (n >= 0 ? " = palet " + (n + 1) : ""));
            if (!comp.allerPrendreVu(vu[0], vu[1])) {
                noter("prendre", "rate");
                continue;
            }
            if (System.currentTimeMillis() >= fin) return;               // on le garde
            boolean ok = comp.marquer();
            if (ok) marques++;
            if (n >= 0) etat[n] = ok ? MARQUE : PERDU;
            noter("autonome", ok ? "marque" : "perdu");
        }
    }

    /** Les palets encore sur le terrain (a prendre), sauf le numero sauf. */
    private double[][] restants(int sauf) {
        int k = 0;
        for (int n = 0; n < palets.length; n++) if (etat[n] == A_PRENDRE && n != sauf) k++;
        double[][] r = new double[k][];
        k = 0;
        for (int n = 0; n < palets.length; n++) if (etat[n] == A_PRENDRE && n != sauf) r[k++] = palets[n];
        return r;
    }

    /** Le palet de la carte (pas encore marque) le plus proche de la position vue, ou -1. */
    private int identifier(double[] vu) {
        int meilleur = -1;
        double dMin = RAYON_IDENTIFICATION;
        for (int n = 0; n < palets.length; n++) {
            if (etat[n] == MARQUE) continue;
            double d = Math.hypot(palets[n][0] - vu[0], palets[n][1] - vu[1]);
            if (d < dMin) { dMin = d; meilleur = n; }
        }
        return meilleur;
    }

    /** Le palet pas encore marque dont l'emplacement theorique est le plus proche du robot, ou -1. */
    private int plusProcheRestant() {
        Pose p = dep.getPose();
        int meilleur = -1;
        double dMin = Double.MAX_VALUE;
        for (int n = 0; n < palets.length; n++) {
            if (etat[n] == MARQUE || etat[n] == ABSENT) continue;
            double d = Math.hypot(palets[n][0] - p.getX(), palets[n][1] - p.getY());
            if (d < dMin) { dMin = d; meilleur = n; }
        }
        return meilleur;
    }

    // =====================================================================
    // Ecran et journal
    // =====================================================================

    private void noter(String etape, String resultat) {
        Pose p = dep.getPose();
        Journal.ecrire(etape, resultat, p.getX(), p.getY(), p.getHeading());
    }

    private String resume() {
        StringBuilder sb = new StringBuilder();
        for (int e : etat) sb.append(SYMBOLES[e]);
        return sb.toString();
    }

    private void afficher(String texte) {
        LCD.clear();
        LCD.drawString(texte, 0, 0);
        LCD.drawString("Marques : " + marques, 0, 1);
        LCD.drawString(resume(), 0, 3);
        if (alarme != null) LCD.drawString("! " + alarme, 0, 5);
    }

    private void finir() {
        LCD.clear();
        LCD.drawString("Fin qualif", 0, 0);
        LCD.drawString("Marques : " + marques, 0, 1);
        LCD.drawString(resume(), 0, 3);
        LCD.drawString(alarme == null ? "Plan sans alarme" : "! " + alarme, 0, 5);
        LCD.drawString("Couleurs ignorees " + comp.incoherences(), 0, 7);
        noter("FIN", marques + " marques");
    }

    /** Lance la qualification avec l'arret d'urgence (bouton central). */
    public static void jouerAvecArret(Deplacement dep, Pince pince, Capteurs capteurs,
                                      Attrapeur attrapeur, double yDepart) {
        ArretUrgence.armer(dep);
        try {
            new QualifCapteurs(dep, new Competences(dep, pince, capteurs, attrapeur)).jouer(yDepart);
        } catch (ArretUrgence.Arret a) {
            LCD.clear();
            LCD.drawString("Arret demande", 0, 0);
        } finally {
            ArretUrgence.desarmer();
            dep.arreter();
        }
    }
}