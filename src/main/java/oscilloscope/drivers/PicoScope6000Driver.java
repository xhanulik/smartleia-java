package oscilloscope.drivers;


import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.ShortByReference;
import oscilloscope.AbstractOscilloscope;
import oscilloscope.drivers.libraries.PicoScope6000Library;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;

public class PicoScope6000Driver extends AbstractOscilloscope {

    short handle = 0;
    private final short channel = (short) PicoScope6000Library.PicoScope6000Channel.PS6000_CHANNEL_B.ordinal();
    private final short channelRange = (short) PicoScope6000Library.PicoScope6000Range.PS6000_1V.ordinal();
    private final short triggerChannel = (short) PicoScope6000Library.PicoScope6000Channel.PS6000_TRIGGER_AUX.ordinal();
    private final double voltageThreshold = 1; //V
    short delay = 0; // no data before trigger
    short autoTriggerMs = 0; // wait indefinitely
    short direction = (short) PicoScope6000Library.PicoScope6000ThresholdDirection.PS6000_RISING.ordinal();
    int timebase = 0;
    int wantedTimeInterval = 256; //ns
    int timeInterval = 0; //ns
    static int numberOfSamples = 1_950_000;
    static short oversample = 1;
    static short downsample = 0;

    static int maxAdcValue = 32767;

    final int timebaseMax = 100;


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
        ShortByReference reqsize = new ShortByReference((short) 0);
        status = PicoScope6000Library.INSTANCE.ps6000GetUnitInfo(handle, info, (short) info.length,
                reqsize, PicoScope6000Library.PICO_VARIANT_INFO);
        if (status != PicoScope6000Library.PS6000_OK) {
            System.out.println("> Opening device NOK");
            return false;
        }
        // get device name
        String deviceName;
        try {
            deviceName = new String(info, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            deviceName = "unknown";
        }
        System.out.println("Device: " + deviceName);
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
                (short) PicoScope6000Library.PS6000_BW_25MHZ); // limit bandwidth
        if (status != PicoScope6000Library.PS6000_OK) {
            throw new RuntimeException("Cannot setup PicoScope 6000 channel");
        }
    }

    private double volt2Adc(double thresholdVoltage, double voltageRange, double maxAdcValue) {
        return (thresholdVoltage / voltageRange) * maxAdcValue;
    }

    private void setTrigger() {
        short threshold = (short) volt2Adc(voltageThreshold, 2.0, maxAdcValue);
        int status;
        try {
            status = PicoScope6000Library.INSTANCE.ps6000SetSimpleTrigger(
                    handle,
                    (short) 1,
                    triggerChannel,
                    threshold,
                    direction,
                    delay,
                    autoTriggerMs
            );
        } catch (Exception e) {
            throw new RuntimeException("ps6000SetSimpleTrigger failed");
        }
        if (status == PicoScope6000Library.PS6000_OK) {
            finish();
            throw new RuntimeException("Cannot setup PicoScope2000 trigger");
        }
    }

    private void calculateTimebase() {
        System.out.println("Getting timebase");
        IntByReference currentTimeInterval = new IntByReference(0);
        IntByReference currentMaxSamples = new IntByReference(0);
        int currentTimebase = 0;

        for (currentTimebase = 0; currentTimebase < timebaseMax; currentTimebase++) {
            int status = PicoScope6000Library.INSTANCE.ps6000GetTimebase(handle, currentTimebase, numberOfSamples,
                    currentTimeInterval, oversample, currentMaxSamples, 0);
            if (status != PicoScope6000Library.PS6000_OK) {
                timeInterval = 0;
                throw new RuntimeException("ps6000GetTimebase failed");
            }
            if (currentTimeInterval.getValue() > wantedTimeInterval) {
                break;
            }
            timeInterval = currentTimeInterval.getValue();
        }

        if (currentTimebase == timebaseMax) {
            timeInterval = 0;
            throw new RuntimeException("No timebase fitting arguments found");
        }
        timebase = currentTimebase - 1;
        System.out.printf("Timebase: %d\n", timebase);
        System.out.printf("Interval between reading: %d ns\n", timeInterval);
        System.out.printf("Number of samples: %d, maximum samples: %d", numberOfSamples, currentMaxSamples.getValue());

    }

    @Override
    public void setup() {
        setChannel(channel, channelRange);
        setTrigger();
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
