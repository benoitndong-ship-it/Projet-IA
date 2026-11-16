package robot;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;

import lejos.hardware.lcd.LCD;
import lejos.utility.Delay;

/**
 * Detection d'un palet par balayage ultrasons, puis approche et saisie.
 *
 * Balayage : le robot tourne d'un seul mouvement continu (celui qui est
 * calibre dans la config) et mesure en meme temps. L'angle de chaque mesure
 * est lu sur les encodeurs des roues via le MovePilot : pas d'arrets, pas
 * d'erreur cumulee sur des dizaines de petites rotations.
 *
 * Tous les seuils sont des INTERVALLES [min ; max] a remplir apres essais :
 * le capteur ne voit le palet que dans une fenetre de distances, et un palet
 * ne produit un creux que sur une certaine largeur angulaire.
 *
 * Approche : on se rapproche jusqu'a une distance ou le palet est ENCORE
 * visible, on revise la visee, puis on avance a l'aveugle jusqu'au contact
 * (capteur tactile), avec une distance maximale de securite.
 *
 * Cette classe n'ouvre AUCUN capteur elle-meme : elle recoit l'objet Capteurs
 * deja construit par Main.
 */
public class Attrapeur {

    // Tous les reglages (pas, retard, intervalles, approche) sont dans Config,
    // section "Ultrasons et scan".

    /** Un palet candidat : une direction et une distance. */
    public static class Candidat {
        public final double angle;      // degres, relatif au cap actuel du robot
        public final double distance;   // cm, mesuree depuis le capteur

        Candidat(double angle, double distance) {
            this.angle = angle;
            this.distance = distance;
        }
    }

    private final Deplacement dep;
    private final Pince pince;
    private final Capteurs capteurs;
    private int numeroBalayage = 0;

    public Attrapeur(Deplacement dep, Pince pince, Capteurs capteurs) {
        this.dep = dep;
        this.pince = pince;
        this.capteurs = capteurs;

        // Premiere lecture systematiquement fausse : le capteur n'est pas stabilise
        this.capteurs.distanceCm();
        Delay.msDelay(200);
    }

    /** Distance en cm, ramenee a Config.PORTEE_MAX_ULTRASONS si rien n'est vu (Infinity, NaN, trop loin). */
    private double lireDistance() {
        double v = capteurs.distanceCm();
        if (Double.isNaN(v) || v > Config.PORTEE_MAX_ULTRASONS) return Config.PORTEE_MAX_ULTRASONS;
        return v;
    }

    /** Vrai si v est dans [min ; max]. */
    private static boolean dans(double v, double min, double max) {
        return v >= min && v <= max;
    }

    /** Ramene un angle dans ]-180 ; 180] pour toujours tourner au plus court. */
    private static double normaliser(double angle) {
        double a = angle % 360.0;
        if (a > 180.0) a -= 360.0;
        if (a <= -180.0) a += 360.0;
        return a;
    }

    // =====================================================================
    // Balayage continu
    // =====================================================================

    /**
     * Balaye [debut ; fin] d'une seule rotation en mesurant au vol, avec le
     * pas habituel Config.PAS_MESURE_SCAN.
     * Le robot revient a son orientation de depart a la fin.
     */
    public ArrayList<Candidat> balayer(double debut, double fin) {
        return balayer(debut, fin, Config.PAS_MESURE_SCAN);
    }

    /**
     * Meme chose avec un pas choisi (ex. 2 degres pour une calibration fine).
     */
    public ArrayList<Candidat> balayer(double debut, double fin, double pas) {
        ArrayList<Double> listeAngles = new ArrayList<Double>();
        ArrayList<Double> listeDist = new ArrayList<Double>();

        dep.tourner(debut);
        Delay.msDelay(80);                       // on laisse le robot se stabiliser

        dep.vitesseScan();                       // Config.VITESSE_SCAN
        dep.tourner(fin - debut, true);          // rend la main, le robot tourne
        double prochaine = 0.0;                  // angle parcouru de la prochaine mesure
        // Correction du retard : on recule chaque angle dans le sens inverse de la rotation
        double sens = Math.signum(fin - debut);
        double correction = sens * Config.VITESSE_SCAN * Config.RETARD_ULTRASONS_MS / 1000.0;
        while (dep.enMouvement()) {
            // L'angle vient des encodeurs : c'est celui que la config a calibre.
            // On le surveille en permanence, mais on ne mesure qu'a chaque
            // Config.PAS_MESURE_SCAN franchi.
            double parcouru = dep.angleTourne();
            if (Math.abs(parcouru) >= prochaine) {
                listeAngles.add(debut + parcouru - correction);
                listeDist.add(lireDistance());
                while (prochaine <= Math.abs(parcouru)) prochaine += pas;
            }
            Delay.msDelay(2);
        }
        dep.vitessesNormales();

        boolean tourComplet = (fin - debut) >= 359.0;
        // Balayage partiel : derniere mesure au bout de la plage (sur un tour
        // complet, elle ferait doublon avec la premiere).
        if (!tourComplet) {
            listeAngles.add(fin);
            listeDist.add(lireDistance());
        }

        // La rotation est terminee : selon les encodeurs, on est a "fin"
        dep.tourner(normaliser(-fin));

        int n = listeAngles.size();
        double[] angles = new double[n];
        double[] dist = new double[n];
        for (int i = 0; i < n; i++) {
            angles[i] = listeAngles.get(i);
            dist[i] = listeDist.get(i);
        }

        double[] lisse = lisser(dist, tourComplet);
        if (Config.DEBUG_SCAN) enregistrerProfil(angles, dist, lisse);
        return detecter(angles, lisse, tourComplet);
    }

    /** Balayage complet sur 360 degres. */
    public ArrayList<Candidat> balayerTout() {
        return balayer(0, 360);
    }

    /**
     * Mediane glissante sur 3 mesures voisines : supprime les echos parasites
     * isoles sans deplacer les bords des creux.
     */
    private static double[] lisser(double[] d, boolean circulaire) {
        int n = d.length;
        double[] r = new double[n];
        double[] fen = new double[3];
        for (int i = 0; i < n; i++) {
            if (!circulaire && (i == 0 || i == n - 1)) {
                r[i] = d[i];
                continue;
            }
            fen[0] = d[(i - 1 + n) % n];
            fen[1] = d[i];
            fen[2] = d[(i + 1) % n];
            Arrays.sort(fen);
            r[i] = fen[1];
        }
        return r;
    }

    /**
     * Cherche les creux qui ressemblent a un palet :
     *  - bord gauche et bord droit nets (saut d'au moins Config.SAUT_MIN_PALET),
     *  - fond dans [Config.DISTANCE_MIN_PALET ; Config.DISTANCE_MAX_PALET],
     *  - largeur dans [Config.LARGEUR_MIN_PALET ; Config.LARGEUR_MAX_PALET] (un mur est trop large).
     * L'angle retenu est le centre du creux.
     */
    private ArrayList<Candidat> detecter(double[] angles, double[] dist, boolean circulaire) {
        int n = dist.length;
        ArrayList<Candidat> trouves = new ArrayList<Candidat>();
        if (n < 3) return trouves;

        double[] a;
        double[] d;
        if (circulaire) {
            // On demarre sur la mesure la plus lointaine pour ne pas couper un
            // palet en deux entre la fin et le debut du tour.
            int m = 0;
            for (int k = 1; k < n; k++) if (dist[k] > dist[m]) m = k;
            a = new double[n + 1];
            d = new double[n + 1];
            for (int k = 0; k <= n; k++) {
                int idx = (m + k) % n;
                a[k] = angles[idx] + ((m + k) >= n ? 360.0 : 0.0);
                d[k] = dist[idx];
            }
        } else {
            // Balayage partiel : rien au-dela des bords.
            a = new double[n + 2];
            d = new double[n + 2];
            a[0] = angles[0];
            d[0] = Config.PORTEE_MAX_ULTRASONS;
            a[n + 1] = angles[n - 1];
            d[n + 1] = Config.PORTEE_MAX_ULTRASONS;
            for (int k = 0; k < n; k++) {
                a[k + 1] = angles[k];
                d[k + 1] = dist[k];
            }
        }

        int len = d.length;
        int i = 1;
        while (i < len - 1) {
            if (!dans(d[i], Config.DISTANCE_MIN_PALET, Config.DISTANCE_MAX_PALET)) {
                i++;
                continue;
            }
            // Bord gauche : on remonte au-dela des echos parasites trop proches
            int p = i - 1;
            while (p > 0 && d[p] < Config.DISTANCE_MIN_PALET) p--;
            if (d[p] - d[i] < Config.SAUT_MIN_PALET) {
                i++;
                continue;
            }

            int debut = i;
            double fond = d[i];
            int j = i;
            while (j + 1 < len && d[j + 1] - fond < Config.SAUT_MIN_PALET) {
                j++;
                // Un echo parasite trop proche (hors intervalle) reste dans le
                // creux mais ne fausse pas le fond : sinon il coupait le palet.
                if (dans(d[j], Config.DISTANCE_MIN_PALET, Config.DISTANCE_MAX_PALET) && d[j] < fond) fond = d[j];
            }

            boolean ferme = j + 1 < len && d[j + 1] - fond >= Config.SAUT_MIN_PALET;
            double largeur = Math.abs(a[j] - a[debut]);
            if (ferme
                    && dans(fond, Config.DISTANCE_MIN_PALET, Config.DISTANCE_MAX_PALET)
                    && dans(largeur, Config.LARGEUR_MIN_PALET, Config.LARGEUR_MAX_PALET)) {
                double centre = (a[debut] + a[j]) / 2.0;
                trouves.add(new Candidat(normaliser(centre), fond));
            }
            i = j + 1;
        }
        return trouves;
    }

    /** Le candidat le plus proche, ou null si la liste est vide. */
    public Candidat plusProche(ArrayList<Candidat> candidats) {
        Candidat meilleur = null;
        for (Candidat c : candidats) {
            if (meilleur == null || c.distance < meilleur.distance) meilleur = c;
        }
        return meilleur;
    }

    /** Le candidat le plus en face du robot, ou null. */
    private Candidat plusAligne(ArrayList<Candidat> candidats) {
        Candidat meilleur = null;
        for (Candidat c : candidats) {
            if (meilleur == null || Math.abs(c.angle) < Math.abs(meilleur.angle)) meilleur = c;
        }
        return meilleur;
    }

    // =====================================================================
    // Approche et saisie
    // =====================================================================

    private void viser(double angle) {
        dep.tourner(normaliser(angle));
    }

    /**
     * Va chercher un palet et le saisit.
     * @return true si la pince s'est refermee sur quelque chose
     */
    public boolean attraper(Candidat cible) {
        Candidat c = cible;
        int revisees = 0;

        while (true) {
            viser(c.angle);

            // Assez pres : le palet va bientot sortir du champ du capteur
            if (c.distance <= Config.DISTANCE_REVISEE + Config.TOLERANCE_REVISEE) {
                return approcheFinale(c.distance);
            }

            if (revisees >= Config.REVISEES_MAX) return false;

            // Trop loin pour viser precisement : on se rapproche en restant
            // dans la zone ou le palet est encore visible, puis on revise
            dep.avancer(c.distance - Config.DISTANCE_REVISEE);
            revisees++;

            Candidat suivant = plusAligne(balayer(-Config.AMPLITUDE_REVISEE, Config.AMPLITUDE_REVISEE));
            if (suivant == null) {
                LCD.clear();
                LCD.drawString("Cible perdue", 0, 0);
                return false;
            }
            c = suivant;
        }
    }

    /**
     * Derniere ligne droite, a l'aveugle (le palet est sous le cone) :
     * on avance jusqu'au contact, au plus jusqu'a la distance attendue plus
     * Config.MARGE_APPROCHE. La distance exacte n'a donc pas besoin d'etre juste.
     */
    private boolean approcheFinale(double distanceCapteur) {
        double attendu = distanceCapteur - Config.OFFSET_CAPTEUR_PINCE;
        double limite = attendu + Config.MARGE_APPROCHE;

        pince.ouvrir();                          // ouverte avant d'avancer sur le palet

        dep.avancer(limite, true);               // rend la main, le robot avance
        while (dep.enMouvement()) {
            // Bouton avant enfonce = le palet est au fond de la pince
            if (capteurs.estTouche()) {
                dep.arreter();
                break;
            }
            Delay.msDelay(10);
        }
        pince.fermer();

        boolean tient = capteurs.estTouche();
        LCD.clear();
        LCD.drawString(tient ? "Palet saisi" : "Pince vide", 0, 0);
        return tient;
    }

    /** Enchaine : balayage complet, choix du plus proche, approche et saisie. */
    public boolean chercherEtAttraper() {
        Candidat cible = plusProche(balayerTout());
        if (cible == null) {
            LCD.clear();
            LCD.drawString("Aucun palet vu", 0, 0);
            return false;
        }
        LCD.clear();
        LCD.drawString("Cible " + (int) cible.angle + "deg", 0, 0);
        LCD.drawString((int) cible.distance + " cm", 0, 1);
        return attraper(cible);
    }

    // =====================================================================
    // Diagnostic
    // =====================================================================

    /**
     * Ajoute le profil d'un balayage a scan.csv (dossier du programme).
     * Colonnes : balayage ; angle ; distance brute ; distance lissee.
     * Recuperation : scp root@10.0.1.1:/home/lejos/programs/scan.csv .
     */
    private void enregistrerProfil(double[] angles, double[] brut, double[] lisse) {
        numeroBalayage++;
        PrintWriter out = null;
        try {
            out = new PrintWriter(new FileWriter("scan.csv", true));
            for (int i = 0; i < angles.length; i++) {
                out.println(numeroBalayage + ";" + angles[i] + ";" + brut[i] + ";" + lisse[i]);
            }
        } catch (IOException e) {
            LCD.drawString("CSV impossible", 0, 7);
        } finally {
            if (out != null) out.close();
        }
    }
}