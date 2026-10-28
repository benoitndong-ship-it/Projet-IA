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

    // ---------- Châssis (cm) — utilisé par WheeledChassis ----------
    public static final double DIAMETRE_ROUE    = 5.6;  // roues de 56 mm
    /** Distance entre le centre du robot et le milieu d'UNE roue (= moitié de l'écart entre les roues). À MESURER. */
    public static final double DEMI_ECART_ROUES = 6.0;
    /** Mettre true si "Avancer 30cm" fait reculer le robot (moteurs montés à l'envers). */
    public static final boolean INVERSER_MOTEURS = false;

    // ---------- Vitesses ----------
    public static final double VITESSE_LINEAIRE  = 20;  // cm/s
    public static final double VITESSE_ANGULAIRE = 90;  // deg/s
    public static final double VITESSE_SCAN      = 45;  // deg/s, plus lent = scan plus précis

    // ---------- Pince — À CALIBRER ----------
    public static final int ANGLE_PINCE_OUVERTE = 1100; // position moteur (deg) pince ouverte
    public static final int VITESSE_PINCE       = 900;  // deg/s
    public static final int DELAI_PINCE_MS      = 2000; // sécurité si le moteur force
    public static final int DELAI_CALIBRAGE_MS = 3000;

    // ---------- Ultrasons (cm) ----------
    public static final float DISTANCE_MAX_PALET = 120f; // au-delà, mesure peu fiable
    public static final float DISTANCE_MIN_PALET = 20f;  // en dessous, le palet passe sous le faisceau
    public static final float SAUT_SEGMENT       = 8f;   // écart qui sépare deux "objets" dans un scan
    public static final float SAUT_MIN_PALET     = 15f;  // un palet est au moins 15 cm devant ce qu'il y a autour
    public static final float LARGEUR_MAX_PALET  = 40f;  // largeur angulaire max (deg) d'un palet vu au scan
    public static final float DISTANCE_OBSTACLE  = 12f;  // mur ou robot trop proche

    // ---------- Couleur — À CALIBRER ----------
    public static final float SEUIL_BLANC = 0.25f; // R, G et B au-dessus => ligne blanche

    // ---------- Terrain ----------
    /** Cap (deg) vers la cage adverse. Le robot doit être posé face à la cage adverse au départ. */
    public static final double CAP_CAGE_ADVERSE = 0;
    public static final double CAP_CAGE_PERSO   = 180;
}