package driver;


import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting LEIA:\n");
        LEIA target = null;
        try {
            target = new LEIA();
            target.open();
            if (target.isCardInserted())
                System.out.println("Card is inserted");
            else
                System.out.println("Card is NOT inserted");
            target.configureSmartcard(ConfigureSmartcardCommand.T.T1, 0, 0, true, true);
            ATR atr = target.getATR();
            System.out.printf("We are using protocol T=%d and the frequency of the ISO7816 clock is %d kHz !\n", atr.tProtocolCurr, atr.fMaxCurr / 1000);

            System.out.println("RESET TRIGGER");
            target.resetTriggerStrategy();

            RESP response = target.sendAPDU("00A404000712345678900101");
            System.out.println("APDU: 00A404000712345678900101");
            System.out.println("RESP: " + response.toString());

            System.out.println("PRESEND TRIGGER");
            target.resetTriggerStrategy();

            response = target.sendAPDU("00010202620155389167c900028a37a264541ae18c5733902c0b51d7665ed41afe6788fe9fba042b32ab827bdffa6f63ccf9f27b1d03017f4f5d909c13294e8a4c389d3f57373f767033b8932942aded1a0ec48d9a1e1d26fb1eec023f74aa48f6a891e73076ca");
            System.out.println("APDU: 00010202620155389167c900028a37a264541ae18c5733902c0b51d7665ed41afe6788fe9fba042b32ab827bdffa6f63ccf9f27b1d03017f4f5d909c13294e8a4c389d3f57373f767033b8932942aded1a0ec48d9a1e1d26fb1eec023f74aa48f6a891e73076ca");
            System.out.println("RESP: " + response.toString());
            target.close();
        } catch (Exception e) {
            if (target != null)
                target.close();
            System.out.println("Caught exception:");
            System.out.println(e.getMessage());
        }
        System.out.println("\nDone");
    }
}