import driver.ATR;
import driver.ConfigureSmartcardCommand;
import driver.TargetController;
import filter.LowPassFilter;
import oscilloscope.AbstractOscilloscope;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting PicoScope");

        System.out.println("START\n");
        AbstractOscilloscope oscilloscope = null;
        TargetController target = null;
        Path resultCSV = Paths.get("./measurements.csv");
        try {
            // PicoScope start
            oscilloscope = AbstractOscilloscope.create();
            oscilloscope.setup();
            oscilloscope.startMeasuring();

            // Leia code
            target = new TargetController();
            target.open();
            if (target.isCardInserted())
                System.out.println("Card is inserted");
            else
                System.out.println("Card is NOT inserted");
            target.configureSmartcard(ConfigureSmartcardCommand.T.T1, 0, 0, true, true);
            ATR atr = target.getATR();
            System.out.printf("We are using protocol T=%d and the frequency of the ISO7816 clock is %d kHz !\n", atr.tProtocolCurr, atr.fMaxCurr / 1000);
            target.resetTriggerStrategy();
            target.sendAPDU("00A404000712345678900101");
            target.sendAPDU("00010202620155389167c900028a37a264541ae18c5733902c0b51d7665ed41afe6788fe9fba042b32ab827bdffa6f63ccf9f27b1d03017f4f5d909c13294e8a4c389d3f57373f767033b8932942aded1a0ec48d9a1e1d26fb1eec023f74aa48f6a891e73076ca");
            target.sendAPDU("0002400042035963fbaa2b953f2aec5fee0d9c926f9d1b65d5e150445fbe21ba437c602544d7111963fbaa2b953f2aec5fee0d9c926f9d1b65d5e150445fbe21ba437c60254111");
            target.sendAPDU("000301008204e6d003a2ed575e28d6b3a195d5e3d4c0548e6438c7f179f7b940c856358d52fa145d0e25d5883c8c96f1b0a1688ad6a610e6a6e40979ade049dc834de4340c2d04ebbf9f544b880466ee7168f8e8725d7401a1a6af80270fd033a5fda75935ae0c4802c67865d8de591e008032d88852a2bfb0259afeab9151a8c2482dd358ec0e");
            target.sendAPDU("0003020082047a4b983117b6f6a47d9960b80c261e6122a5bc33661861f59a8a5793778ad0c42a3a9a3856f8e6bafa8a34e01ee287ea0914aae956b86a47691dfe3f3084bf5504b4c3e234502ae5c97b7d14fee0ac980023de2b2acf3d3db1d03338b9535def0e5306406a2b58ba89f01415e16152426270b9e38f546331db6496adc752831651");
            target.sendAPDU("0004400040CFE5203F6E095EF063648BE69B7965DBAA57E1D04206CCEF36D864534CBDDDCB4AFD27D2F5AAB4555EBFD1BC98C18A0CB9DFCD9B9D644FA771CAA3A29AA4FDBD");
            target.setPreSendAPDUTriggerStrategy();
            target.sendAPDU("0007400040CFE5203F6E095EF063648BE69B7965DBAA57E1D04206CCEF36D864534CBDDDCB4AFD27D2F5AAB4555EBFD1BC98C18A0CB9DFCD9B9D644FA771CAA3A29AA4FDBD");
            // Leia code end

            // PicoScope store and close
            oscilloscope.store(resultCSV, 20000);
            oscilloscope.finish();

            // Leia close
            target.close();
        } catch (Exception e) {
            if (target != null)
                target.close();
            if (oscilloscope != null)
                oscilloscope.stopDevice();
            System.out.println("Caught exception:");
            System.out.println(e.getMessage());
        }
        System.out.println("\nDone");
    }
}
