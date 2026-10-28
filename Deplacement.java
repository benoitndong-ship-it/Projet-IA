package robot;

import lejos.hardware.motor.EV3LargeRegulatedMotor;
import lejos.robotics.chassis.Chassis;
import lejos.robotics.chassis.Wheel;
import lejos.robotics.chassis.WheeledChassis;
import lejos.robotics.geometry.Point;
import lejos.robotics.localization.OdometryPoseProvider;
import lejos.robotics.navigation.MovePilot;
import lejos.robotics.navigation.Pose;

/**
 * Fonctions de base de déplacement, basées sur les classes OFFICIELLES de leJOS 0.9.1 :
 *   - WheeledChassis : décrit le châssis (diamètre des roues, position des roues)
 *                      et commande les deux moteurs de façon synchronisée ;
 *   - MovePilot      : avancer / reculer / tourner (rotate = une roue avance,
 *                      l'autre recule, en même temps) ;
 *   - OdometryPoseProvider : position et cap du robot.
 *
 * Repère : (0,0) = position de départ, cap 0 = face à la cage adverse,
 * angles positifs = vers la gauche (sens trigo).
 */
public class Deplacement {

    private final MovePilot pilote;
    private final OdometryPoseProvider odometrie;

    public Deplacement() {
        EV3LargeRegulatedMotor gauche = new EV3LargeRegulatedMotor(Config.PORT_ROUE_GAUCHE);
        EV3LargeRegulatedMotor droite = new EV3LargeRegulatedMotor(Config.PORT_ROUE_DROITE);

        // Description du châssis : diamètre 5.6 cm, chaque roue à DEMI_ECART_ROUES du centre
        Wheel roueG = WheeledChassis.modelWheel(gauche, Config.DIAMETRE_ROUE)
                .offset(-Config.DEMI_ECART_ROUES)
                .invert(Config.INVERSER_MOTEURS);
        Wheel roueD = WheeledChassis.modelWheel(droite, Config.DIAMETRE_ROUE)
                .offset(Config.DEMI_ECART_ROUES)
                .invert(Config.INVERSER_MOTEURS);
        Chassis chassis = new WheeledChassis(new Wheel[]{roueG, roueD}, WheeledChassis.TYPE_DIFFERENTIAL);

        pilote = new MovePilot(chassis);
        odometrie = new OdometryPoseProvider(pilote);
        vitessesNormales();
    }

    // ---------- Avancer / reculer ----------

    /** Avance de cm centimètres et attend la fin du mouvement. */
    public void avancer(double cm) { pilote.travel(cm); }

    /** Si immediat = true, rend la main tout de suite (le robot continue). */
    public void avancer(double cm, boolean immediat) { pilote.travel(cm, immediat); }

    /** Avance sans limite jusqu'à arreter(). */
    public void avancerContinu() { pilote.forward(); }

    public void reculer(double cm) { pilote.travel(-cm); }

    // ---------- Tourner (Rotate) ----------

    /** Tourne sur place de angle degrés (positif = gauche). */
    public void tourner(double angle) { pilote.rotate(angle); }

    public void tourner(double angle, boolean immediat) { pilote.rotate(angle, immediat); }

    /** Se tourne vers un cap absolu (0 = cage adverse) par le chemin le plus court. */
    public void orienterVers(double capAbsolu) {
        tourner(normaliser(capAbsolu - getCap()));
    }

    /** Va en ligne droite vers un point (x, y) du repère de départ. */
    public void allerVersPoint(float x, float y) {
        Pose pose = getPose();
        Point cible = new Point(x, y);
        orienterVers(pose.angleTo(cible));
        avancer(pose.distanceTo(cible));
    }

    // ---------- Contrôle ----------

    public void arreter() { pilote.stop(); }

    public boolean enMouvement() { return pilote.isMoving(); }

    public void vitessesNormales() {
        pilote.setLinearSpeed(Config.VITESSE_LINEAIRE);
        pilote.setAngularSpeed(Config.VITESSE_ANGULAIRE);
    }

    /** Rotation lente pour le scan aux ultrasons. */
    public void vitesseScan() { pilote.setAngularSpeed(Config.VITESSE_SCAN); }

    // ---------- Odométrie ----------

    public Pose getPose() { return odometrie.getPose(); }

    public double getCap() { return getPose().getHeading(); }

    /** Ramène un angle dans ]-180, 180]. */
    public static double normaliser(double angle) {
        angle %= 360;
        if (angle > 180) angle -= 360;
        if (angle <= -180) angle += 360;
        return angle;
    }
}