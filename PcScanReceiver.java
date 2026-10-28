package robot;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Run this class on the computer before starting Scan360 on the EV3. */
public class PcScanReceiver {
    private static final int PORT = 5555;
    private static final String OUTPUT_DIRECTORY = "scans";

    public static void main(String[] args) throws Exception {
        File directory = new File(OUTPUT_DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new Exception("Cannot create output directory: " + directory.getAbsolutePath());
        }

        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File output = new File(directory, "scan360-" + stamp + ".csv");

        ServerSocket server = new ServerSocket(PORT);
        System.out.println("Waiting for EV3 on port " + PORT + "...");
        System.out.println("CSV destination: " + output.getAbsolutePath());

        try {
            Socket client = server.accept();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), "UTF-8"));
            PrintWriter file = new PrintWriter(output, "UTF-8");
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    file.println(line);
                    file.flush();
                    System.out.println(line);
                }
            } finally {
                file.close();
                in.close();
                client.close();
                server.close();
            }
            System.out.println("Scan saved: " + output.getAbsolutePath());
        } finally {
            if (!server.isClosed()) server.close();
        }
    }
}
