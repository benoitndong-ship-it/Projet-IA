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
    public static final double TOLERANCE_REVISEE   = 8;
    /** Amplitude du scan de re-visée, de part et d'autre (deg). */
    public static final double AMPLITUDE_REVISEE   = 45;
    /** Nombre maximal de re-visées avant d'abandonner un palet. */
    public static final int    REVISEES_MAX        = 3;
    /** Distance entre le capteur à ultrasons et le fond de la pince (cm). À MESURER. */
    public static final double OFFSET_CAPTEUR_PINCE = 5;
    /** Distance en plus autorisée à l'aveugle si le capteur tactile n'est pas touché (cm). */
    public static final double MARGE_APPROCHE      = 10;
    /** true : chaque scan est ajouté à scan.csv sur la brique. Mettre false en match. */
    public static final boolean DEBUG_SCAN         = true;

    // ---------- Couleur — À CALIBRER ----------
    public static final float SEUIL_BLANC = 0.25f; // R, G et B au-dessus => ligne blanche

    // ---------- Terrain ----------
    /** Cap (deg) vers la cage adverse. Le robot doit être posé face à la cage adverse au départ. */
    public static final double CAP_CAGE_ADVERSE = 0;
    public static final double CAP_CAGE_PERSO   = 180;
}