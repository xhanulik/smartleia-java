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
            RESP response = target.sendAPDU("00A404000712345678900101");
            System.out.println("APDU: 00A404000712345678900101");
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