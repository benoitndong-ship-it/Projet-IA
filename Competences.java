package robot;

import lejos.hardware.lcd.LCD;
import lejos.robotics.navigation.Pose;
import lejos.utility.Delay;

/**
 * Les grosses competences du robot, chacune assemblee a partir de
 * sous-competences (Deplacement, Pince, Attrapeur).
 *
 * Une competence lancee va jusqu'au bout, sauf pour la raison d'arret
 * indiquee dans son commentaire.
 *
 * SOUS-COMPETENCES (briques)
 *   rouler(d)     : avancer de d cm -> a chaque ligne croisee, corriger X ou Y
 *   allerA(x, y)  : se tourner vers le point -> rouler
 *   lacher()      : ouvrir la pince -> reculer -> fermer la pince
 *   (Attrapeur) scanner devant + saisir
 *
 * COMPETENCES
 *   prendrePaletDepart() : lire la distance droit devant (sans balayage) -> ouvrir -> avancer -> fermer
 *   allerChercher(x, y)  : ligne droite si libre (sinon par le couloir) -> se tourner -> scanner (cote terrain)
 *                          -> petit recalage sur le palet -> saisir
 *   marquer(yProchain)   : ligne droite si libre (sinon couloir en diagonale), en face du
 *                          palet suivant -> face a la cage -> rouler jusqu'au mur adverse (vue)
 *                          -> lacher -> se retourner
 *
 * ZONES : un palet est cherche dans un carre de +/- ZONE_PALET_CM autour de son
 * croisement ; un objet vu hors de cette zone est ignore.
 * COULEURS : la position de chaque couleur est apprise pendant le match ; une
 * couleur lue a un endroit impossible est ignoree (pas de recalage absurde).
 *
 * PRINCIPE : la VUE (ultrasons) decide. Les autres sens (couleur, odometrie,
 * bouton) ne servent qu'a verifier ou a recaler, jamais obligatoires.
 * L'odometrie reste un filet de securite (ne jamais depasser X_DEPOT_MAX).
 *
 * Reperes du terrain : voir Config, section "Terrain".
 */
public class Competences {

    // ---- Carte du terrain (cm) ----
    /** Couloirs sans palet, entre les rangees (Y). */
    static final double[] COULOIRS = {75, 125};
    /** Lignes en travers : vert et bleu (on ne sait pas lequel est ou). */
    static final double[] LIGNES_X_COULEUR = {90, 210};
    /** Lignes en long : jaune et rouge. */
    static final double[] LIGNES_Y_COULEUR = {50, 150};
    static final double LIGNE_X_NOIRE = 150, LIGNE_Y_NOIRE = 100;
    static final double[] LIGNES_X_BLANCHES = {30, 270};

    /** Pour scanner un palet, on s'arrete a cette distance (en X) a cote de lui. */
    static final double RECUL_SCAN_X = 45;
    /** On ne recale que si la ligne attendue est a moins de ca (cm). */
    static final double ECART_MAX_RECALAGE = 12;
    /** Lectures identiques de suite pour valider une couleur. */
    static final int LECTURES_CONFIRMATION = 3;
    /** La ligne blanche ne compte que dans cette zone, cote adverse (X > ...). */
    static final double X_ZONE_ADVERSE = 200;
    /**
     * Le mur adverse ne peut etre a moins de MUR_DEPOT_CM que si le robot est deja
     * presque dans l'en-but : avant ce X (odometrie), une lecture courte aux
     * ultrasons est ignoree (ce n'est pas le mur).
     */
    static final double X_MUR_PLAUSIBLE = 255;
    /** Lectures courtes de suite aux ultrasons pour croire au mur. */
    static final int LECTURES_MUR = 3;
    /** Pour rejoindre un couloir en diagonale : on avance de ca en X pendant le decalage (cm). */
    static final double DIAGONALE_X = 40;
    /** Scan de visee : jusqu'ou regarder du cote d'un mur (deg). L'autre cote : SCAN_VISEE_AMPLITUDE. */
    static final double SCAN_COTE_MUR = 12;
    /** Recalage sur un palet : correction maximale acceptee (au-dela, on l'ignore). */
    static final double RECALAGE_PALET_MAX_DEG = 10, RECALAGE_PALET_MAX_CM = 15;
    /** La derniere correction sur un palet a-t-elle ete appliquee ? */
    private boolean derniereCorrection = true;
    /** Le dernier depot a-t-il ete decide par la vue (mur) et non par le filet ? */
    private boolean dernierDepotSurMur = true;

    /** false si le dernier recalage sur un palet a ete refuse (correction trop grande). */
    public boolean dernierRecalageAccepte() { return derniereCorrection; }
    /** false si le dernier depot s'est fait au filet de securite (mur jamais vu). */
    public boolean dernierDepotSurMur() { return dernierDepotSurMur; }

    /** Rayon d'un palet (cm) : le capteur voit son bord, on vise son centre. */
    static final double RAYON_PALET = 3;
    /** Rangees de palets le long d'un mur (Y) : le mur est a Y = 0 ou Y = 200. */
    static final double Y_RANGEE_MUR_DROIT = 50, Y_RANGEE_MUR_GAUCHE = 150;

    /** Recul apres avoir lache le palet (cm), avec un demi-tour ensuite. */
    static final double RECUL_APRES_DEPOT = 40;
    /** Recul court quand on enchaine directement vers le palet suivant (juste degager le palet). */
    static final double RECUL_COURT = 25;
    /** Un trajet est libre si aucun palet restant n'est a moins de ca de la ligne droite (cm). */
    static final double MARGE_PASSAGE = 22;
    /** Approche directe : on s'arrete a cette distance du palet (centre du robot) pour le scanner. */
    static final double APPROCHE_DIRECTE = 52;

    /** Palets encore sur le terrain {x, y}, donnes par le planificateur. null = inconnus (couloirs). */
    private double[][] obstacles = null;

    /** Le planificateur donne les palets encore sur le terrain, pour tester si une ligne droite est libre. */
    public void definirObstacles(double[][] palets) {
        obstacles = palets;
    }

    // ---- Raisons d'arret de rouler() ----
    static final int FIN_DISTANCE = 0, MUR_ADVERSE = 2, CONTACT = 3, PALET_PERDU = 4;

    /** Ce qui a provoque le dernier arret de rouler() vers la cage (pour l'ecran). */
    private String indicesArret = "distance";

    private final Deplacement dep;
    private final Pince pince;
    private final Capteurs capteurs;
    private final Attrapeur attrapeur;

    public Competences(Deplacement dep, Pince pince, Capteurs capteurs, Attrapeur attrapeur) {
        this.dep = dep;
        this.pince = pince;
        this.capteurs = capteurs;
        this.attrapeur = attrapeur;
    }

    // =====================================================================
    // Competences
    // =====================================================================

    /**
     * PRENDRE LE PALET DE DEPART (juste devant le robot, a l'aveugle).
     * Ouvrir la pince -> avancer tout droit -> fermer la pince.
     * Avec le bouton : arret au contact, AVANCE_PALET_DEPART + MARGE au plus.
     * Sans le bouton : on avance exactement AVANCE_PALET_DEPART.
     * @return true si le palet est tenu (toujours true sans le bouton)
     */
    public boolean prendrePaletDepart(double px, double py) {
        ArretUrgence.verifier();
        // Pas de balayage : on lit la distance droit devant (vue). Si le palet est
        // vu, on avance de cette distance ; sinon, distance prevue AVANCE_PALET_DEPART.
        double avance = Config.AVANCE_PALET_DEPART;
        double vu = distanceDevant();
        if (vu >= Config.DISTANCE_MIN_PALET && vu <= Config.DISTANCE_MAX_PALET) {
            avance = vu - Config.OFFSET_CAPTEUR_PINCE;
        }
        pince.ouvrir();
        if (Config.UTILISER_BOUTON) {
            rouler(avance + Config.MARGE_APPROCHE, false, true);
        } else {
            rouler(avance, false, false);
        }
        pince.fermer();
        return paletTenu();
    }

    /** Distance droit devant : mediane de 5 lectures (cm). */
    private double distanceDevant() {
        float[] l = new float[5];
        for (int i = 0; i < l.length; i++) {
            l[i] = capteurs.distanceCm();
            Delay.msDelay(30);
        }
        java.util.Arrays.sort(l);
        return l[2];
    }

    /**
     * RECALER SUR UN PALET CONNU : le palet (px, py) est vu a l'angle c.angle et
     * a la distance c.distance. On en deduit le vrai cap du robot et sa position
     * le long de la ligne de visee. Securite : si la correction est trop grande
     * (plus de RECALAGE_PALET_MAX_DEG ou RECALAGE_PALET_MAX_CM), c'est sans doute
     * un autre objet : on ne touche a rien.
     */
    public void recalerSurPalet(Attrapeur.Candidat c, double px, double py) {
        Pose p = dep.getPose();
        double dirAttendue = Math.toDegrees(Math.atan2(py - p.getY(), px - p.getX()));
        double capVrai = dirAttendue - c.angle;             // le palet est vu a c.angle du cap
        double d = c.distance + Config.DIST_CENTRE_CAPTEUR_US + RAYON_PALET;
        double a = Math.toRadians(dirAttendue);
        double x = px - d * Math.cos(a);
        double y = py - d * Math.sin(a);

        double ecartCap = Math.abs(Deplacement.normaliser(capVrai - p.getHeading()));
        double ecartPos = Math.hypot(x - p.getX(), y - p.getY());
        derniereCorrection = ecartCap <= RECALAGE_PALET_MAX_DEG && ecartPos <= RECALAGE_PALET_MAX_CM;
        if (derniereCorrection) dep.definirPose(x, y, capVrai);
    }


    /**
     * ALLER CHERCHER LE PALET (x, y).
     * Rejoindre le couloir -> longer le couloir jusqu'a cote du palet ->
     * se tourner vers lui -> scanner devant -> viser -> saisir.
     * S'arrete si : rien n'est vu au scan (palet absent).
     * @return true si un palet est tenu
     */
    public boolean allerChercher(double x, double y) {
        ArretUrgence.verifier();
        Pose p = dep.getPose();
        double dx = x - p.getX(), dy = y - p.getY();
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d > APPROCHE_DIRECTE && voieLibre(p.getX(), p.getY(), x, y)) {
            // Voie libre : tout droit jusqu'a APPROCHE_DIRECTE du palet
            allerA(x - dx / d * APPROCHE_DIRECTE, y - dy / d * APPROCHE_DIRECTE);
        } else {
            // Sinon par le couloir, sans traverser de palet
            double couloir = couloirPour(y);
            allerA(dep.getPose().getX(), couloir);
            allerA(x + RECUL_SCAN_X, couloir);       // a cote du palet, cote en-but adverse
        }
        p = dep.getPose();
        dep.orienterVers(Math.toDegrees(Math.atan2(y - p.getY(), x - p.getX())));

        // Scan cote terrain : pour un palet le long d'un mur, on ne regarde
        // presque pas du cote du mur (rien a y trouver)
        double gauche = Config.SCAN_VISEE_AMPLITUDE, droite = Config.SCAN_VISEE_AMPLITUDE;
        double dirMur = Double.NaN;
        if (y == Y_RANGEE_MUR_DROIT)  dirMur = -90;    // mur Y = 0
        if (y == Y_RANGEE_MUR_GAUCHE) dirMur = 90;     // mur Y = 200
        if (!Double.isNaN(dirMur)) {
            if (Deplacement.normaliser(dirMur - dep.getCap()) > 0) gauche = SCAN_COTE_MUR;
            else droite = SCAN_COTE_MUR;
        }
        // Scan, puis on ne garde que ce qui est DANS LA ZONE du palet vise
        java.util.ArrayList<Attrapeur.Candidat> vus =
            attrapeur.balayer(-droite, gauche, Config.SCAN_VISEE_PAS, Config.SCAN_VISEE_VITESSE);
        Attrapeur.Candidat cible = dansLaZone(vus, x, y);
        if (cible == null) {
            LCD.clear();
            LCD.drawString(vus.isEmpty() ? "Rien devant" : "Hors zone", 0, 0);
            return false;
        }
        recalerSurPalet(cible, x, y);                 // petit ajustement de la position
        return attrapeur.viserEtSaisir(cible);
    }

    /**
     * Parmi les objets vus au scan, celui qui tombe dans la zone du palet (x, y)
     * (carre de +/- ZONE_PALET_CM), le plus pres du croisement. null si aucun :
     * un mur, un autre palet ou l'autre robot hors zone sont ignores.
     */
    private Attrapeur.Candidat dansLaZone(java.util.ArrayList<Attrapeur.Candidat> vus, double x, double y) {
        Pose p = dep.getPose();
        Attrapeur.Candidat meilleur = null;
        double ecartMin = Double.MAX_VALUE;
        for (Attrapeur.Candidat c : vus) {
            // Position de l'objet sur le terrain : robot + distance dans la direction vue
            double a = Math.toRadians(p.getHeading() + c.angle);
            double d = c.distance + Config.DIST_CENTRE_CAPTEUR_US + RAYON_PALET;
            double xo = p.getX() + d * Math.cos(a);
            double yo = p.getY() + d * Math.sin(a);
            if (Math.abs(xo - x) > Config.ZONE_PALET_CM || Math.abs(yo - y) > Config.ZONE_PALET_CM) continue;
            double ecart = Math.hypot(xo - x, yo - y);
            if (ecart < ecartMin) { ecartMin = ecart; meilleur = c; }
        }
        return meilleur;
    }

    /**
     * MARQUER : amener le palet tenu dans l'en-but adverse.
     * Rejoindre le couloir en diagonale -> se tourner vers la cage adverse -> rouler jusqu'a ce
     * que la VUE (ultrasons) voie le mur adverse a moins de MUR_DEPOT_CM
     * -> lacher -> demi-tour du cote du terrain.
     * Verifications non obligatoires (affichees) : ligne blanche vue, X plausible.
     * Filet de securite : jamais au-dela de X_DEPOT_MAX (odometrie).
     * S'arrete si : avec le bouton, le palet n'est plus tenu (perdu en route).
     * @return true si le palet a ete lache dans l'en-but
     */
    public boolean marquer() {
        return marquer(Double.NaN);
    }

    /**
     * MARQUER en preparant la suite : on depose EN FACE du palet suivant
     * (a son Y), on prend la ligne droite si elle est libre, et apres le depot
     * on recule juste de quoi degager le palet, sans demi-tour (le trajet
     * suivant tournera directement vers le palet suivant).
     * @param yProchain Y du palet suivant, ou NaN si on ne sait pas (couloirs + demi-tour)
     */
    public boolean marquer(double yProchain) {
        ArretUrgence.verifier();
        LCD.clear();
        LCD.drawString("Vers en-but", 0, 0);
        Pose p = dep.getPose();
        double xApproche = Config.X_DEPOT_MAX - 40;          // juste avant l'en-but
        double yDest = Double.isNaN(yProchain) ? Double.NaN
                     : Math.max(Y_RANGEE_MUR_DROIT, Math.min(Y_RANGEE_MUR_GAUCHE, yProchain));

        boolean ok;
        if (!Double.isNaN(yDest) && p.getX() < xApproche && voieLibre(p.getX(), p.getY(), xApproche, yDest)) {
            ok = allerA(xApproche, yDest);                    // ligne droite, en face du suivant
        } else {
            // Couloir en DIAGONALE (on avance en meme temps qu'on se decale)
            double couloir = couloirPour(Double.isNaN(yDest) ? p.getY() : yDest);
            double xCouloir = Math.min(p.getX() + DIAGONALE_X, Config.X_DEPOT_MAX - 20);
            if (!voieLibre(p.getX(), p.getY(), xCouloir, couloir)) couloir = couloirPour(p.getY());
            ok = allerA(Math.max(xCouloir, p.getX()), couloir);
        }
        if (!ok) return abandon();
        dep.orienterVers(Config.CAP_CAGE_ADVERSE);

        // On roule jusqu'au mur adverse (ultrasons). Filet de securite : on ne
        // depasse jamais X_DEPOT_MAX, meme si le mur n'est pas vu.
        double reste = Config.X_DEPOT_MAX - dep.getPose().getX();
        int raison = (reste > 0) ? rouler(reste, true, false) : FIN_DISTANCE;
        dernierDepotSurMur = (raison == MUR_ADVERSE);
        if (raison == PALET_PERDU) return abandon();

        // Ecran : "mur" + les verifications qui confirment (+blanc, +X), ou "filet"
        LCD.drawString("Arret: " + (raison == MUR_ADVERSE ? indicesArret : "filet X"), 0, 1);

        if (Double.isNaN(yProchain)) {
            lacher();
            seRetournerCoteTerrain();                // demi-tour, en tournant vers le terrain
        } else {
            lacher(RECUL_COURT);                     // pas de demi-tour : on enchaine
        }
        return true;
    }

    // =====================================================================
    // Competences du mode autonome (quand le plan fixe deraille)
    // =====================================================================

    /** Limites ou un palet peut se trouver (hors en-buts, loin des murs). */
    static final double X_MIN_PALET = 35, X_MAX_PALET = 255, Y_MIN_PALET = 12, Y_MAX_PALET = 188;

    /**
     * CHERCHER AUTOUR : tour complet -> position sur le terrain de chaque objet vu
     * -> on ne garde que ce qui peut etre un palet (hors en-buts, loin des murs)
     * -> le meilleur = le plus proche de nous ET des cages adverses
     *    (distance robot -> palet + distance palet -> ligne blanche adverse).
     * @return {x, y} du meilleur palet, ou null si rien
     */
    public double[] chercherAutour() {
        ArretUrgence.verifier();
        LCD.clear();
        LCD.drawString("Scan 360", 0, 0);
        java.util.ArrayList<Attrapeur.Candidat> vus = attrapeur.balayerTout();
        Pose p = dep.getPose();
        double[] meilleur = null;
        double scoreMin = Double.MAX_VALUE;
        for (Attrapeur.Candidat c : vus) {
            double a = Math.toRadians(p.getHeading() + c.angle);
            double d = c.distance + Config.DIST_CENTRE_CAPTEUR_US + RAYON_PALET;
            double xo = p.getX() + d * Math.cos(a);
            double yo = p.getY() + d * Math.sin(a);
            if (xo < X_MIN_PALET || xo > X_MAX_PALET || yo < Y_MIN_PALET || yo > Y_MAX_PALET) continue;
            double score = d + (Config.X_LIGNE_ADVERSE - xo);
            if (score < scoreMin) { scoreMin = score; meilleur = new double[] {xo, yo}; }
        }
        return meilleur;
    }

    /**
     * ALLER PRENDRE UN PALET VU (position approximative, pas une position de la
     * carte) : se tourner vers lui -> avancer jusqu'a DISTANCE_VISEE -> scan de
     * visee -> objet dans la zone -> viser -> saisir. Pas de recalage : la
     * position du palet n'est pas sure.
     */
    public boolean allerPrendreVu(double x, double y) {
        ArretUrgence.verifier();
        Pose p = dep.getPose();
        double dx = x - p.getX(), dy = y - p.getY();
        double d = Math.sqrt(dx * dx + dy * dy);
        dep.orienterVers(Math.toDegrees(Math.atan2(dy, dx)));
        if (d > Config.DISTANCE_VISEE) rouler(d - Config.DISTANCE_VISEE, false, false);
        java.util.ArrayList<Attrapeur.Candidat> vus = attrapeur.balayerVisee();
        Attrapeur.Candidat cible = dansLaZone(vus, x, y);
        if (cible == null) return false;
        return attrapeur.viserEtSaisir(cible);
    }

    /** SE RAPPROCHER d'un point (x, y) de pas cm au plus, en restant a garde cm de lui. */
    public void seRapprocher(double x, double y, double pas, double garde) {
        ArretUrgence.verifier();
        Pose p = dep.getPose();
        double dx = x - p.getX(), dy = y - p.getY();
        double d = Math.sqrt(dx * dx + dy * dy);
        double avance = Math.min(pas, d - garde);
        if (avance < 5) return;
        dep.orienterVers(Math.toDegrees(Math.atan2(dy, dx)));
        rouler(avance, false, false);
    }

    // =====================================================================
    // Sous-competences
    // =====================================================================

    /** LACHER : ouvrir la pince -> reculer -> fermer la pince. */
    public void lacher() {
        lacher(RECUL_APRES_DEPOT);
    }

    /** LACHER en reculant de recul cm. */
    public void lacher(double recul) {
        ArretUrgence.verifier();
        pince.ouvrir();
        dep.reculer(recul);
        pince.fermer();
    }

    /**
     * ALLER A (x, y) : se tourner vers le point -> rouler.
     * @return false si le palet tenu a ete perdu en route (avec le bouton)
     */
    public boolean allerA(double x, double y) {
        ArretUrgence.verifier();
        Pose p = dep.getPose();
        double dx = x - p.getX(), dy = y - p.getY();
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d < 2) return true;
        dep.orienterVers(Math.toDegrees(Math.atan2(dy, dx)));
        return rouler(d, false, false) != PALET_PERDU;
    }

    /**
     * ROULER : avancer de d cm. A chaque ligne croisee, corrige X ou Y.
     * @param versCage  arret devant le mur adverse (ultrasons), le blanc sert de confirmation
     * @param auContact arret des que le bouton avant est enfonce
     * @return la raison de l'arret (FIN_DISTANCE, MUR_ADVERSE, CONTACT, PALET_PERDU)
     */
    public int rouler(double d, boolean versCage, boolean auContact) {
        // Avec le bouton, un palet tenu au depart doit le rester tout le trajet
        boolean surveillerPalet = false;
        if (Config.UTILISER_BOUTON) {
            surveillerPalet = !auContact && capteurs.estTouche();
        }
        int precedente = Capteurs.GRIS, candidate = Capteurs.GRIS, vues = 0;
        int murVu = 0;
        boolean blancVu = false;
        indicesArret = "distance";

        dep.avancer(d, true);
        while (dep.enMouvement()) {
            ArretUrgence.verifier();
            if (auContact && capteurs.estTouche()) { dep.arreter(); return CONTACT; }
            if (surveillerPalet && !capteurs.estTouche()) { dep.arreter(); return PALET_PERDU; }

            double x = dep.getPose().getX();

            // Couleurs : recalage de la position, et le blanc adverse sert de confirmation
            int c = capteurs.couleurSol();
            if (c == Capteurs.NOIR && !Config.RECALAGE_NOIR) c = Capteurs.GRIS;  // ombres
            vues = (c == candidate) ? vues + 1 : 1;
            if (vues >= LECTURES_CONFIRMATION && c != precedente) {
                precedente = c;
                if (c != Capteurs.GRIS) {
                    recaler(c);
                    if (versCage && c == Capteurs.BLANC && x > X_ZONE_ADVERSE) blancVu = true;
                }
            }
            candidate = c;

            // SENS PRINCIPAL : la vue (ultrasons). Le mur adverse a moins de
            // MUR_DEPOT_CM, lu LECTURES_MUR fois de suite = on est dans l'en-but.
            if (versCage) {
                murVu = (capteurs.distanceCm() < Config.MUR_DEPOT_CM) ? murVu + 1 : 0;
                if (murVu >= LECTURES_MUR) {
                    dep.arreter();
                    // Verifications supplementaires, non obligatoires : affichees seulement
                    indicesArret = "mur" + (blancVu ? "+blanc" : "")
                                 + (dep.getPose().getX() > X_MUR_PLAUSIBLE ? "+X" : "");
                    return MUR_ADVERSE;
                }
            }
            Delay.msDelay(10);
        }
        ArretUrgence.verifier();
        return FIN_DISTANCE;
    }

    // =====================================================================
    // Outils
    // =====================================================================

    /**
     * Demi-tour vers notre camp en tournant du cote du TERRAIN, jamais du cote
     * du mur le plus proche : moitie droite du terrain (Y < 100) -> on tourne a
     * gauche ; moitie gauche -> a droite. Avant, le sens dependait d'un degre
     * pres du cap et le robot pouvait tourner vers le mur.
     */
    private void seRetournerCoteTerrain() {
        double delta = Deplacement.normaliser(Config.CAP_CAGE_PERSO - dep.getCap());
        double y = dep.getPose().getY();
        if (y < LIGNE_Y_NOIRE && delta < 0) delta += 360;   // forcer a gauche (positif)
        if (y > LIGNE_Y_NOIRE && delta > 0) delta -= 360;   // forcer a droite (negatif)
        dep.tourner(delta);
    }


    /** Avec le bouton : le palet est-il au fond de la pince ? Sans le bouton : on suppose que oui. */
    private boolean paletTenu() {
        if (Config.UTILISER_BOUTON) {
            return capteurs.estTouche();
        }
        return true;
    }

    private boolean abandon() {
        LCD.clear();
        LCD.drawString("Palet perdu", 0, 0);
        pince.fermer();
        return false;
    }

    // ---- Carte des couleurs, apprise pendant le match ----
    // On ne sait pas a l'avance de quel cote est le vert ou le jaune. On
    // l'apprend la premiere fois qu'on croise la ligne a un endroit sur ; ensuite
    // une couleur lue au mauvais endroit est jugee ABSURDE et ignoree.
    private double xVert = Double.NaN, xBleu = Double.NaN;
    private double yJaune = Double.NaN, yRouge = Double.NaN;
    private int incoherences = 0;

    /**
     * Au depart, lit la couleur sous le capteur : sur un R de cote, on est sur
     * la ligne jaune ou rouge, ce qui donne tout de suite leur position.
     */
    public void apprendreCouleurDepart(double yDepart) {
        if (yDepart == LIGNE_Y_NOIRE) return;                   // au centre : ligne noire, rien a apprendre
        int c = capteurs.couleurSol();
        double autre = 200 - yDepart;
        if (c == Capteurs.JAUNE) { yJaune = yDepart; yRouge = autre; }
        if (c == Capteurs.ROUGE) { yRouge = yDepart; yJaune = autre; }
    }

    /** Nombre de couleurs lues au mauvais endroit et ignorees (pour l'ecran). */
    public int incoherences() {
        return incoherences;
    }

    /**
     * Corrige X ou Y : le capteur de couleur est sur une ligne de couleur c.
     * Securites : la ligne doit etre a moins de ECART_MAX_RECALAGE de la position
     * estimee, ET etre de la bonne couleur a cet endroit (si on l'a deja apprise).
     */
    private void recaler(int c) {
        Pose p = dep.getPose();
        double cap = Math.toRadians(p.getHeading());
        double off = Config.OFFSET_CAPTEUR_COULEUR;
        double xc = p.getX() + off * Math.cos(cap);   // position du capteur
        double yc = p.getY() + off * Math.sin(cap);
        boolean traverseX = Math.abs(Math.cos(cap)) > 0.5;   // on coupe les lignes en travers
        boolean traverseY = Math.abs(Math.sin(cap)) > 0.5;   // on coupe les lignes en long

        double ligneX = Double.NaN, ligneY = Double.NaN;
        if (c == Capteurs.VERT)  ligneX = ligneCouleur(xc, xVert, xBleu, LIGNES_X_COULEUR, true);
        if (c == Capteurs.BLEU)  ligneX = ligneCouleur(xc, xBleu, xVert, LIGNES_X_COULEUR, false);
        if (c == Capteurs.JAUNE) ligneY = ligneCouleur(yc, yJaune, yRouge, LIGNES_Y_COULEUR, true);
        if (c == Capteurs.ROUGE) ligneY = ligneCouleur(yc, yRouge, yJaune, LIGNES_Y_COULEUR, false);
        if (c == Capteurs.BLANC) ligneX = plusProche(xc, LIGNES_X_BLANCHES);
        if (c == Capteurs.NOIR) {
            if (Math.abs(xc - LIGNE_X_NOIRE) < Math.abs(yc - LIGNE_Y_NOIRE)) ligneX = LIGNE_X_NOIRE;
            else ligneY = LIGNE_Y_NOIRE;
        }

        double x = p.getX(), y = p.getY();
        if (!Double.isNaN(ligneX) && traverseX && Math.abs(xc - ligneX) < ECART_MAX_RECALAGE) {
            x = ligneX - off * Math.cos(cap);
        }
        if (!Double.isNaN(ligneY) && traverseY && Math.abs(yc - ligneY) < ECART_MAX_RECALAGE) {
            y = ligneY - off * Math.sin(cap);
        }
        dep.definirPose(x, y, p.getHeading());
    }

    /**
     * Position de la ligne d'une couleur, avec controle de coherence.
     * @param v       position estimee du capteur (X ou Y)
     * @param connue  position deja apprise pour cette couleur (NaN si inconnue)
     * @param autre   position apprise pour l'autre couleur du meme sens
     * @param lignes  les deux positions possibles
     * @param premiere true pour vert/jaune (premiere variable a remplir)
     * @return la position de la ligne, ou NaN si la lecture est absurde
     */
    private double ligneCouleur(double v, double connue, double autre, double[] lignes, boolean premiere) {
        if (!Double.isNaN(connue)) {
            // Couleur deja situee : elle doit etre la ou on l'attend
            if (Math.abs(v - connue) < ECART_MAX_RECALAGE) return connue;
            if (Math.abs(v - autre) < ECART_MAX_RECALAGE) incoherences++;   // absurde : on ignore
            return Double.NaN;
        }
        // Premiere fois : on apprend, seulement si on est clairement pres d'une des deux lignes
        double l = plusProche(v, lignes);
        if (Math.abs(v - l) >= ECART_MAX_RECALAGE) return Double.NaN;
        double l2 = (l == lignes[0]) ? lignes[1] : lignes[0];
        if (lignes == LIGNES_X_COULEUR) {
            if (premiere) { xVert = l; xBleu = l2; } else { xBleu = l; xVert = l2; }
        } else {
            if (premiere) { yJaune = l; yRouge = l2; } else { yRouge = l; yJaune = l2; }
        }
        return l;
    }

    /**
     * Vrai si la ligne droite (x1, y1) -> (x2, y2) passe a plus de MARGE_PASSAGE
     * de tous les palets restants (sauf celui qui est au bout du trajet).
     * Si les palets restants ne sont pas connus : jamais libre (on prend les couloirs).
     */
    private boolean voieLibre(double x1, double y1, double x2, double y2) {
        if (obstacles == null) return false;
        for (double[] o : obstacles) {
            if (Math.hypot(o[0] - x2, o[1] - y2) < 5) continue;          // c'est la destination
            if (distanceAuSegment(o[0], o[1], x1, y1, x2, y2) < MARGE_PASSAGE) return false;
        }
        return true;
    }

    private static double distanceAuSegment(double px, double py,
                                            double ax, double ay, double bx, double by) {
        double vx = bx - ax, vy = by - ay;
        double l2 = vx * vx + vy * vy;
        if (l2 == 0) return Math.hypot(px - ax, py - ay);
        double t = Math.max(0, Math.min(1, ((px - ax) * vx + (py - ay) * vy) / l2));
        return Math.hypot(px - ax - t * vx, py - ay - t * vy);
    }

    /** Le couloir le plus proche de y. */
    static double couloirPour(double y) {
        return (Math.abs(y - COULOIRS[0]) <= Math.abs(y - COULOIRS[1])) ? COULOIRS[0] : COULOIRS[1];
    }

    private static double plusProche(double v, double[] valeurs) {
        double meilleur = valeurs[0];
        for (double w : valeurs) if (Math.abs(w - v) < Math.abs(meilleur - v)) meilleur = w;
        return meilleur;
    }
}