package robot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Programme a lancer SUR LE PC (pas sur la brique).
 *
 * Il attend les connexions du robot sur le port PC_PORT et ajoute chaque
 * mesure recue a la suite du fichier scan360.csv, place dans le dossier du
 * package. Le fichier n'est jamais ecrase : a chaque nouveau scan, les lignes
 * s'ajoutent avec un numero de run et un horodatage, ce qui permet de garder
 * l'historique complet et de separer les scans a l'analyse.
 *
 * Le programme reste en ecoute : on peut relancer le scan sur le robot
 * autant de fois qu'on veut sans le redemarrer. CTRL+C pour l'arreter.
 */
public class RecepteurScan {

    static final int PC_PORT = 5555;

    // Fichier dans le dossier du package robot, a cote des sources.
    static final String FICHIER = "src/robot/scan360.csv";

    public static void main(String[] args) throws Exception {
        File fichier = new File(FICHIER);
        File dossier = fichier.getParentFile();
        if (dossier != null && !dossier.exists()) {
            dossier.mkdirs();
        }

        // true = append : on ajoute a la suite, on n'ecrase jamais
        boolean nouveauFichier = !fichier.exists();
        PrintWriter csv = new PrintWriter(new FileWriter(fichier, true), true);
        if (nouveauFichier) {
            csv.println("run;date;angle_deg;distance_cm");
        }

        ServerSocket serveur = new ServerSocket(PC_PORT);
        System.out.println("En ecoute sur le port " + PC_PORT);
        System.out.println("Fichier : " + fichier.getAbsolutePath());

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        int numeroRun = 0;

        while (true) {
            Socket socket = serveur.accept();
            numeroRun++;
            String date = format.format(new Date());
            System.out.println("--- Scan " + numeroRun + " recu le " + date + " ---");

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            String ligne;
            int nbMesures = 0;

            while ((ligne = in.readLine()) != null) {
                // On ignore l'entete et le marqueur de run envoyes par le robot
                if (ligne.startsWith("#") || ligne.startsWith("angle_deg")) {
                    continue;
                }
                csv.println(numeroRun + ";" + date + ";" + ligne);
                System.out.println(ligne);
                nbMesures++;
            }

            in.close();
            socket.close();
            System.out.println("--- " + nbMesures + " mesures ajoutees au CSV ---");
        }
    }
}