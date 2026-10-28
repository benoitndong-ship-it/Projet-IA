package robot;

import java.io.PrintWriter;
import java.net.Socket;

import lejos.hardware.Button;
import lejos.hardware.lcd.LCD;
import lejos.hardware.motor.EV3LargeRegulatedMotor;
import lejos.hardware.port.MotorPort;
import lejos.hardware.port.SensorPort;
import lejos.hardware.sensor.EV3UltrasonicSensor;
import lejos.robotics.SampleProvider;
import lejos.robotics.chassis.Chassis;
import lejos.robotics.chassis.Wheel;
import lejos.robotics.chassis.WheeledChassis;
import lejos.robotics.navigation.MovePilot;

/** Scans 360 degrees and streams each CSV row directly to the computer. */
public class Scan360 {
    static final double WHEEL_DIAMETER_MM = 56.0;
    static final double AXLE_WIDTH_MM = 120.0;
    static final double STEP_DEGREES = 10.0;
    static final double ROTATION_FACTOR = 1.0;
    static final double ROTATION_SPEED = 60.0;

    // Replace with the computer's IPv4 address on the same Wi-Fi network.
    static final String PC_HOST = "192.168.1.100";
    static final int PC_PORT = 5555;

    public static void main(String[] args) {
        EV3LargeRegulatedMotor leftMotor = new EV3LargeRegulatedMotor(MotorPort.C);
        EV3LargeRegulatedMotor rightMotor = new EV3LargeRegulatedMotor(MotorPort.A);
        EV3UltrasonicSensor ultrasonic = null;
        Socket socket = null;
        PrintWriter out = null;

        try {
            Wheel leftWheel = WheeledChassis.modelWheel(leftMotor, WHEEL_DIAMETER_MM)
                    .offset(-AXLE_WIDTH_MM / 2);
            Wheel rightWheel = WheeledChassis.modelWheel(rightMotor, WHEEL_DIAMETER_MM)
                    .offset(AXLE_WIDTH_MM / 2);
            Chassis chassis = new WheeledChassis(new Wheel[] { leftWheel, rightWheel },
                    WheeledChassis.TYPE_DIFFERENTIAL);
            MovePilot pilot = new MovePilot(chassis);
            pilot.setAngularSpeed(ROTATION_SPEED);

            ultrasonic = new EV3UltrasonicSensor(SensorPort.S1);
            SampleProvider distance = ultrasonic.getDistanceMode();
            float[] sample = new float[distance.sampleSize()];

            LCD.clear();
            LCD.drawString("Scan 360", 0, 0);
            LCD.drawString("ENTER = start", 0, 1);
            Button.ENTER.waitForPressAndRelease();

            LCD.clear();
            LCD.drawString("Connexion PC...", 0, 0);
            socket = new Socket(PC_HOST, PC_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            out.println("angle_deg;distance_cm");

            int steps = (int) Math.round(360.0 / STEP_DEGREES);
            double angle = 0;
            for (int i = 0; i <= steps; i++) {
                distance.fetchSample(sample, 0);
                double centimeters = sample[0] * 100.0;
                String row = (int) angle + ";" + Double.toString(centimeters);
                out.println(row);
                if (out.checkError()) throw new Exception("Failed to send scan data to PC");

                LCD.drawString("Mesure " + (i + 1) + "/" + (steps + 1) + "   ", 0, 0);
                LCD.drawString("Angle " + (int) angle + "   ", 0, 1);
                LCD.drawString("Dist " + (int) centimeters + " cm   ", 0, 2);

                if (i < steps) {
                    pilot.rotate(STEP_DEGREES * ROTATION_FACTOR, false);
                    angle += STEP_DEGREES;
                }
            }

            LCD.clear();
            LCD.drawString("Scan envoye au PC", 0, 0);
            LCD.drawString("ENTER pour quitter", 0, 1);
            Button.ENTER.waitForPressAndRelease();
        } catch (Exception e) {
            LCD.clear();
            LCD.drawString("Erreur scan/PC", 0, 0);
            LCD.drawString(e.getMessage() == null ? "Voir console" : e.getMessage(), 0, 1);
            System.err.println("Scan360 failed: " + e);
            Button.waitForAnyPress();
        } finally {
            if (out != null) out.close();
            if (socket != null) {
                try { socket.close(); } catch (Exception ignored) { }
            }
            if (ultrasonic != null) ultrasonic.close();
            leftMotor.close();
            rightMotor.close();
        }
    }
}
