package oscilloscope.drivers;


import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.ShortByReference;
import oscilloscope.AbstractOscilloscope;
import oscilloscope.drivers.libraries.PicoScope6000Library;
import oscilloscope.drivers.libraries.PicoScopeVoltageDefinitions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

public class PicoScope6000Driver extends AbstractOscilloscope {

    short handle = 0;
    private final short channelA = (short) PicoScope6000Library.PicoScope6000Channel.PS6000_CHANNEL_A.ordinal();
    private final short channelB = (short) PicoScope6000Library.PicoScope6000Channel.PS6000_CHANNEL_B.ordinal();
    private short chARange = (short) PicoScope6000Library.PicoScope6000Range.PS6000_10V.ordinal();
    private short chBRange = (short) PicoScope6000Library.PicoScope6000Range.PS6000_1V.ordinal();

    private double mvThreshold = 1000.0;
    short delay = 0; // no data before trigger
    short autoTriggerMs = 0; // wait indefinitely
    short direction = (short) PicoScope6000Library.PicoScope6000ThresholdDirection.PS6000_RISING.ordinal();
    short timebase = 0;
    int timeIntervalNanoseconds = 51;
    static int numberOfSamples = 9748538;
    static short oversample = 1;
    ShortByReference timeUnitsSbr = new ShortByReference((short) 0);



    @Override
    public boolean connect() {
        System.out.println("> Opening device");
        ShortByReference handleRef = new ShortByReference();

        // try to open connection to PicoScope
        int status = 0;
        try {
            // TODO: Support serial string to differentiate between device
            status = PicoScope6000Library.INSTANCE.ps6000OpenUnit(handleRef, null);
            handle = handleRef.getValue();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        if (status != PicoScope6000Library.PS6000_OK) {
            System.out.println("> Opening device NOK");
            return false;
        }

        // get info about device
        byte[] info = new byte[40];
        ShortByReference reqsize =new ShortByReference((short) 0);
        status = PicoScope6000Library.INSTANCE.ps6000GetUnitInfo(handle, info, (short) info.length,
                reqsize, PicoScope6000Library.PICO_VARIANT_INFO);
        if (status != PicoScope6000Library.PS6000_OK) {
            System.out.println("> Opening device NOK");
            return false;
        }
        System.out.println("Device: " + Arrays.toString(info));
        System.out.println("> Opening device OK");
        return true;
    }

    private void setChannel(short channel, short range) {
        int status = PicoScope6000Library.INSTANCE.ps6000SetChannel(
                handle,
                channel,
                (short) 1,
                (short) PicoScope6000Library.PicoScope6000Coupling.PS6000_DC_1M.ordinal(),
                range,
                (float) 0,
                (short) 0);
        if (status != PicoScope6000Library.PS6000_OK) {
            finish();
            throw new RuntimeException("Cannot setup PicoScope6000 channel");
        }
    }

    private void calculateTimebase() {
        short currentTimebase = 0;
        int oldTimeInterval = 0;

        IntByReference timeInterval = new IntByReference();
        IntByReference maxSamples = new IntByReference();

        while (PicoScope6000Library.INSTANCE.ps6000GetTimebase(
                handle,
                currentTimebase,
                numberOfSamples,
                timeInterval,
                oversample,
                maxSamples, 0) == PicoScope6000Library.PS6000_OK || timeInterval.getValue() < timeIntervalNanoseconds) {
            currentTimebase++;
            oldTimeInterval = timeInterval.getValue();
        }

        timebase = (short) (currentTimebase - 1);
        timeIntervalNanoseconds = oldTimeInterval;
    }

    @Override
    public void setup() {
        // Set channel A
        setChannel(channelA, chARange);

        // Set channel B
        setChannel(channelB, chBRange);
        // Set trigger
        short threshold = (short) (mvThreshold / PicoScopeVoltageDefinitions.SCOPE_INPUT_RANGES_MV[chARange] * PicoScope6000Library.PS6000_MAX_VALUE);
        int status = PicoScope6000Library.INSTANCE.ps6000SetSimpleTrigger(
                handle,
                (short) 1,
                channelA,
                threshold,
                direction,
                delay,
                autoTriggerMs
        );
        if (status == PicoScope6000Library.PS6000_OK) {
            finish();
            throw new RuntimeException("Cannot setup PicoScope2000 channel A");
        }
        // Set timebase
        calculateTimebase();
    }

    @Override
    public void startMeasuring() {

    }

    @Override
    public void stopDevice() {
        System.out.println("> Stop device");
        int status = PicoScope6000Library.INSTANCE.ps6000Stop(handle);
        if (status != PicoScope6000Library.PS6000_OK) {
            throw new RuntimeException("Error while stopping device!");
        }
        System.out.println("> Stop device OK");
    }

    @Override
    public void store(Path file) throws IOException {

    }

    @Override
    public void finish() {
        System.out.println("> Finish");
        // stop measuring
        try {
            stopDevice();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        // close device
        PicoScope6000Library.INSTANCE.ps6000CloseUnit(handle);
        System.out.println("> Finish OK");
    }
}
