package robot;

import lejos.hardware.port.MotorPort;
import lejos.hardware.port.Port;
import lejos.hardware.port.SensorPort;

/**
 * Toutes les constantes du robot au même endroit.
 * Les valeurs marquées "À CALIBRER" doivent être mesurées sur le vrai robot.
 */
public final class Config {
    private Config() {}

    // ---------- Ports (à adapter au câblage réel) ----------
    public static final Port PORT_ROUE_GAUCHE = MotorPort.C;
    public static final Port PORT_ROUE_DROITE = MotorPort.A;
    public static final Port PORT_PINCE       = MotorPort.D;
    public static final Port PORT_ULTRASONS   = SensorPort.S1;
    public static final Port PORT_TOUCHER     = SensorPort.S3;
    public static final Port PORT_COULEUR     = SensorPort.S4;

    // ---------- Châssis (cm) — utilisé par WheeledChassis ---------- A MODIFIER AUSSI
    public static final double DIAMETRE_ROUE    = 5.6;  // roues de 56 mm
    /** Distance entre le centre du robot et le milieu d'UNE roue (= moitié de l'écart entre les roues). À MESURER. */
    public static final double DEMI_ECART_ROUES = 6.1;
    /** Mettre true si "Avancer 30cm" fait reculer le robot (moteurs montés à l'envers). */
    public static final boolean INVERSER_MOTEURS = false;

    // ---------- Vitesses ----------
    public static final double VITESSE_LINEAIRE  = 20;  // cm/s
    public static final double VITESSE_ANGULAIRE = 110;  // deg/s
    public static final double VITESSE_SCAN      = 45;  // deg/s, plus lent = scan plus précis

    // ---------- Pince — À CALIBRER ----------
    public static final int ANGLE_PINCE_OUVERTE = 1100; // position moteur (deg) pince ouverte
    public static final int VITESSE_PINCE       = 900;  // deg/s
    public static final int DELAI_PINCE_MS      = 2000; // sécurité si le moteur force
    public static final int DELAI_CALIBRAGE_MS = 3000;

    // ---------- Ultrasons et scan (utilisés par Attrapeur) ----------
    /** Portée max du capteur (cm) : "rien vu" (Infinity) est remplacé par cette valeur. */
    public static final double PORTEE_MAX_ULTRASONS = 190;
    /**
     * Retard de lecture du capteur (ms), mesuré avec "Mesurer retard" du menu.
     * Le scan recule chaque angle de VITESSE_SCAN x retard (45 deg/s x 244 ms = 11 deg).
     * Ne dépend pas de la vitesse : pas besoin de remesurer si VITESSE_SCAN change.
     */
    public static final double RETARD_ULTRASONS_MS = 244;
    // Réglages par type de balayage (vitesse en deg/s, pas et amplitude en deg).
    // La correction du retard s'adapte toute seule à la vitesse.
    /** Tour complet (recherche d'un palet n'importe où) : large, rapide. */
    public static final double SCAN_TOUR_PAS          = 5;
    public static final double SCAN_TOUR_VITESSE      = 45;
    /** Visée juste avant de saisir : petit, lent, précis. De part et d'autre de l'axe. */
    public static final double SCAN_VISEE_AMPLITUDE   = 30;
    public static final double SCAN_VISEE_PAS         = 3;
    public static final double SCAN_VISEE_VITESSE     = 30;

    /** Une mesure tous les ... degrés pendant le scan. Ne change PAS la durée du scan. */
    public static final double PAS_MESURE_SCAN = 10;

    // Reconnaissance d'un palet : intervalles [min ; max] — À CALIBRER avec scan.csv
    public static final float DISTANCE_MIN_PALET = 32f;  // en dessous, le palet passe sous le faisceau
    public static final float DISTANCE_MAX_PALET = 70f;  // au-delà, mesure peu fiable
    public static final float SAUT_MIN_PALET     = 10f;  // un palet est au moins X cm devant ce qu'il y a autour
    public static final float LARGEUR_MIN_PALET  = 8f;   // largeur angulaire min (deg) d'un palet vu au scan
    public static final float LARGEUR_MAX_PALET  = 50f;  // largeur angulaire max (deg) ; un mur est plus large
    public static final float SAUT_SEGMENT       = 8f;   // écart qui sépare deux "objets" dans un scan
    public static final float DISTANCE_OBSTACLE  = 12f;  // mur ou robot trop proche

    // Approche d'un palet
    /** On s'arrête à cette distance pour re-viser. DOIT être dans [DISTANCE_MIN_PALET ; DISTANCE_MAX_PALET]. */
    public static final double DISTANCE_REVISEE    = 40;
    /** Si le palet est à moins de DISTANCE_REVISEE + ceci, on passe directement à la saisie. */
    public static final double TOLERANCE_REVISEE   = 0;
    /** Amplitude du scan de re-visée, de part et d'autre (deg). */
    public static final double AMPLITUDE_REVISEE   = 45;
    /** Nombre maximal de re-visées avant d'abandonner un palet. */
    public static final int    REVISEES_MAX        = 3;
    /** Distance entre le capteur à ultrasons et le fond de la pince (cm). À MESURER. */
    public static final double OFFSET_CAPTEUR_PINCE = 5;
    /**
     * true : pendant la saisie, on s'arrête quand le bouton avant est enfoncé, et il sert
     *        à vérifier que le palet est tenu.
     * false : on avance de la distance mesurée, et on suppose le palet tenu.
     */
    public static final boolean UTILISER_BOUTON = false;
    /** Distance en plus autorisée à l'aveugle si le capteur tactile n'est pas touché (cm). */
    public static final double MARGE_APPROCHE      = 10;
    /** true : chaque scan est ajouté à scan.csv sur la brique. Mettre false en match. */
    public static final boolean DEBUG_SCAN         = true;

    // ---------- Couleur — À CALIBRER ----------
    public static final float SEUIL_BLANC = 0.15f; // R, G et B au-dessus => ligne blanche

    // Lignes de couleur : intervalles {Rmin, Rmax, Vmin, Vmax, Bmin, Bmax}.
    // Relevés du 16/11 (gris = 0,10 / 0,09 / 0,08). À élargir si une ligne n'est pas reconnue.
    public static final float[] COUL_JAUNE = {0.18f, 0.40f,  0.12f, 0.30f,  0.00f, 0.09f}; // relevé 0,26 / 0,18 / 0,04
    public static final float[] COUL_ROUGE = {0.10f, 0.25f,  0.00f, 0.06f,  0.00f, 0.05f}; // relevé 0,14 / 0,03 / 0,02
    public static final float[] COUL_VERT  = {0.00f, 0.09f,  0.08f, 0.18f,  0.00f, 0.07f}; // relevé 0,06 / 0,11 / 0,04
    public static final float[] COUL_BLEU  = {0.00f, 0.06f,  0.00f, 0.06f,  0.05f, 0.12f}; // relevé 0,03 / 0,03 / 0,06
    public static final float[] COUL_NOIR  = {0.00f, 0.05f,  0.00f, 0.05f,  0.00f, 0.04f}; // relevé 0,03 / 0,03 / 0,02
    /** false : le noir n'est pas utilisé pour recaler (le gris à l'ombre se confond avec lui). */
    public static final boolean RECALAGE_NOIR = false;

    // ---------- Terrain (cm) ----------
    // Repère du match : X le long du terrain, 0 = notre mur du fond, 300 = mur adverse.
    //                   Y en travers, 0 = mur à droite du robot au départ, 200 = mur à gauche.
    // Palets aux croisements X = 90 / 150 / 210 et Y = 50 / 100 / 150.
    // Départ R sur notre ligne blanche (X = 30), Y = 50 (droite), 100 (centre) ou 150 (gauche).
    public static final double X_DEPART             = 30;
    /** Ligne blanche de l'en-but adverse. */
    public static final double X_LIGNE_ADVERSE      = 270;
    /** Filet de sécurité : si le mur n'est pas vu, on s'arrête quand le centre du robot arrive ici. */
    public static final double X_DEPOT_MAX          = 280;
    /** Distance entre le centre des roues et le capteur de couleur, vers l'avant (cm). À MESURER. */
    public static final double OFFSET_CAPTEUR_COULEUR = 8;
    /** Palet de départ (juste devant) : distance à parcourir pour l'avoir au fond de la pince (cm). À MESURER. */
    public static final double AVANCE_PALET_DEPART  = 45;
    /** Dépôt : arrêt quand les ultrasons voient le mur adverse à moins de ça (cm). Sens principal. */
    public static final double MUR_DEPOT_CM         = 20;
    /** Fond de la cage : on n'avance jamais plus près du mur que ça (cm, lu aux ultrasons). */
    public static final double MUR_FOND_CM          = 10;
    /** Après la ligne blanche, on avance encore de ça avant de lâcher le palet (cm). */
    public static final double AVANCE_APRES_LIGNE   = 10;
    /** On s'arrête à cette distance du palet visé pour le scanner (dans [DISTANCE_MIN ; MAX]). */
    public static final double DISTANCE_VISEE       = 45;
    /** Zone d'un palet : carré de ± ZONE_PALET_CM autour de son croisement. Un objet vu hors zone est ignoré. */
    public static final double ZONE_PALET_CM        = 22;
    /** Distance entre le centre des roues et l'avant du capteur à ultrasons (cm). À MESURER. */
    public static final double DIST_CENTRE_CAPTEUR_US = 10;
    /** true : chaque étape du match est écrite dans journal.csv (pour comprendre où ça déraille). */
    public static final boolean JOURNAL = true;
    /** Durée du programme de match (ms) : un peu moins que les 5 min d'une manche. */
    public static final long   DUREE_MATCH_MS       = 290000;

    // ---------- Terrain ----------
    /** Cap (deg) vers la cage adverse. Le robot doit être posé face à la cage adverse au départ. */
    public static final double CAP_CAGE_ADVERSE = 0;
    public static final double CAP_CAGE_PERSO   = 180;
}