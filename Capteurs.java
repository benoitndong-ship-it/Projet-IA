package robot;

import lejos.hardware.sensor.EV3ColorSensor;
import lejos.hardware.sensor.EV3TouchSensor;
import lejos.hardware.sensor.EV3UltrasonicSensor;
import lejos.robotics.SampleProvider;

/** Lecture des capteurs : ultrasons, toucher (palet dans les pinces), couleur (lignes). */
public class Capteurs {

    // Couleurs du sol reconnues par couleurSol()
    public static final int GRIS = 0, BLANC = 1, NOIR = 2, ROUGE = 3, JAUNE = 4, VERT = 5, BLEU = 6;
    public static final String[] NOMS = {"gris", "blanc", "noir", "rouge", "jaune", "vert", "bleu"};

    private final EV3UltrasonicSensor ultrasons;
    private final EV3TouchSensor toucher;
    private final EV3ColorSensor couleur;

    private final SampleProvider modeDistance, modeToucher, modeRGB;
    private final float[] echDistance, echToucher, echRGB;

    public Capteurs() {
        ultrasons = new EV3UltrasonicSensor(Config.PORT_ULTRASONS);
        toucher   = new EV3TouchSensor(Config.PORT_TOUCHER);
        couleur   = new EV3ColorSensor(Config.PORT_COULEUR);

        modeDistance = ultrasons.getDistanceMode();
        modeToucher  = toucher.getTouchMode();
        modeRGB      = couleur.getRGBMode();

        echDistance = new float[modeDistance.sampleSize()];
        echToucher  = new float[modeToucher.sampleSize()];
        echRGB      = new float[modeRGB.sampleSize()];
    }

    /** Distance en cm devant le robot (Float.POSITIVE_INFINITY si rien n'est détecté). */
    public float distanceCm() {
        modeDistance.fetchSample(echDistance, 0);
        float m = echDistance[0];
        return Float.isInfinite(m) ? Float.POSITIVE_INFINITY : m * 100f;
    }

    /** Vrai si le capteur tactile est enfoncé (palet entre les pinces). */
    public boolean estTouche() {
        modeToucher.fetchSample(echToucher, 0);
        return echToucher[0] > 0.5f;
    }

    /** Valeurs R, G, B entre 0 et 1. */
    public float[] rgb() {
        modeRGB.fetchSample(echRGB, 0);
        return echRGB.clone();
    }

    /** Vrai si le robot est sur une ligne blanche (limite d'en-but). */
    public boolean surLigneBlanche() {
        float[] c = rgb();
        return c[0] > Config.SEUIL_BLANC && c[1] > Config.SEUIL_BLANC && c[2] > Config.SEUIL_BLANC;
    }

    /**
     * Couleur du sol sous le capteur : BLANC, NOIR, ROUGE, JAUNE, VERT, BLEU,
     * ou GRIS si aucune ne correspond. Les intervalles sont dans Config.
     */
    public int couleurSol() {
        float[] c = rgb();
        if (surLigneBlanche())           return BLANC;
        if (dans(c, Config.COUL_JAUNE))  return JAUNE;
        if (dans(c, Config.COUL_ROUGE))  return ROUGE;
        if (dans(c, Config.COUL_VERT))   return VERT;
        if (dans(c, Config.COUL_BLEU))   return BLEU;
        if (dans(c, Config.COUL_NOIR))   return NOIR;
        return GRIS;
    }

    /** Vrai si R, V et B sont chacun dans leur intervalle {Rmin, Rmax, Vmin, Vmax, Bmin, Bmax}. */
    private static boolean dans(float[] c, float[] i) {
        return c[0] >= i[0] && c[0] <= i[1]
            && c[1] >= i[2] && c[1] <= i[3]
            && c[2] >= i[4] && c[2] <= i[5];
    }

    public void fermer() {
        ultrasons.close();
        toucher.close();
        couleur.close();
    }
}