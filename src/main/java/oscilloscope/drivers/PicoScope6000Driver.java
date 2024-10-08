package oscilloscope.drivers;


import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.ShortByReference;
import oscilloscope.AbstractOscilloscope;
import oscilloscope.drivers.libraries.PicoScope6000Library;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    int numberOfSamples = 1_950_000;
    short oversample = 0;
    short downsample = 0;

    int maxAdcValue = 32767;

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
            throw new RuntimeException("ps6000OpenUnit failed with exception: " + status);
        }

        if (status != PicoScope6000Library.PS6000_OK) {
            // do not throw exception here
            System.out.println("> Opening device NOK");
            return false;
        }

        // get info about device
        byte[] info = new byte[40];
        ShortByReference infoLength = new ShortByReference((short) 0);
        try {
            status = PicoScope6000Library.INSTANCE.ps6000GetUnitInfo(handle, info, (short) info.length,
                    infoLength, PicoScope6000Library.PICO_VARIANT_INFO);
        } catch (Exception e) {
            throw new RuntimeException("ps6000GetUnitInfo failed with exception: " + e.getMessage());
        }

        if (status != PicoScope6000Library.PS6000_OK) {
            System.out.println("> Opening device NOK");
            return false;
        }
        // get device name
        String deviceName;
        deviceName = new String(info, StandardCharsets.UTF_8);
        System.out.println("Device: " + deviceName);
        System.out.println("> Opening device OK");
        return true;
    }

    private void setChannel(short channel, short range) {
        System.out.println("> Set channel");
        int status;
        try {
            status = PicoScope6000Library.INSTANCE.ps6000SetChannel(handle, channel, (short) 1, (short) PicoScope6000Library.PicoScope6000Coupling.PS6000_DC_1M.ordinal(),
                    range, (float) 0, (short) PicoScope6000Library.PS6000_BW_25MHZ);
        } catch (Exception e) {
            throw new RuntimeException("ps6000SetChannel failed with exception: " + e.getMessage());
        }
        if (status != PicoScope6000Library.PS6000_OK) {
            throw new RuntimeException("ps6000SetChannel failed with error code: " + status);
        }
        System.out.println("> Set channel OK");
    }

    private void setTrigger() {
        System.out.println("> Set trigger");
        short threshold = (short) volt2Adc(voltageThreshold, 2.0, maxAdcValue);
        int status;
        try {
            status = PicoScope6000Library.INSTANCE.ps6000SetSimpleTrigger(handle, (short) 1, triggerChannel, threshold,
                    direction, delay, autoTriggerMs );
        } catch (Exception e) {
            throw new RuntimeException("ps6000SetSimpleTrigger failed with exception: " + e.getMessage());
        }
        if (status != PicoScope6000Library.PS6000_OK) {
            throw new RuntimeException("ps6000SetSimpleTrigger failed with error code: " + status);
        }
        System.out.println("> Set trigger OK");
    }

    private void calculateTimebase() {
        System.out.println("> Getting timebase");
        IntByReference currentTimeInterval = new IntByReference(0);
        IntByReference currentMaxSamples = new IntByReference(0);
        int currentTimebase;

        for (currentTimebase = 0; currentTimebase < timebaseMax; currentTimebase++) {
            currentTimeInterval.setValue(0);
            int status;
            try {
                status = PicoScope6000Library.INSTANCE.ps6000GetTimebase(handle, currentTimebase, numberOfSamples,
                        currentTimeInterval, oversample, currentMaxSamples, 0);
            } catch (Exception e) {
                throw new RuntimeException("ps6000GetTimebase failed with exception: " + e.getMessage());
            }
            if (status == PicoScope6000Library.PS6000_OK && currentTimeInterval.getValue() > wantedTimeInterval) {
                break;
            }
            timeInterval = currentTimeInterval.getValue();
        }

        if (currentTimebase == timebaseMax) {
            timeInterval = 0;
            throw new RuntimeException("No timebase fitting arguments found");
        }
        timebase = currentTimebase - 1;
        System.out.printf("Timebase: %d, time interval: %d, samples: %d, max samples: %d\n",
                currentTimebase, currentTimeInterval.getValue(), numberOfSamples, currentMaxSamples.getValue());
        System.out.println("< Getting timebase OK");
    }

    @Override
    public void setup() {
        setChannel(channel, channelRange);
        //setTrigger();
        calculateTimebase();
    }

    @Override
    public void startMeasuring() {
        System.out.println("> Start measuring");
        IntByReference timeIndisposedMs = new IntByReference(0);
        int status;
        try {
            status = PicoScope6000Library.INSTANCE.ps6000RunBlock(handle, 0, numberOfSamples, timebase, oversample,
                    timeIndisposedMs, 0, null, null);
        } catch (Exception e) {
            throw new RuntimeException("ps6000RunBlock failed with exception: " + e.getMessage());
        }
        if (status != PicoScope6000Library.PS6000_OK) {
            throw new RuntimeException("ps6000RunBlock failed with error code: " + status);
        }
        System.out.println("< Start measuring OK");
    }

    private void waitForSamples() {
        System.out.println("> Wait for samples");
        ShortByReference ready = new ShortByReference((short) 0);
        while (ready.getValue() == 0) {
            int status;
            try {
                status = PicoScope6000Library.INSTANCE.ps6000IsReady(handle, ready);
                Thread.sleep(100);
                System.out.println("...waiting for data...");
            } catch (Exception e) {
                throw new RuntimeException("ps6000IsReady failed with exception: " + e.getMessage());
            }
            if (status != PicoScope6000Library.PS6000_OK) {
                throw new RuntimeException("ps6000IsReady failed with error code: " + status);
            }
        }
        System.out.println("< Wait for samples OK");
    }

    private void setBuffer(short[] adcValues) {
        int status;
        try {
            status = PicoScope6000Library.INSTANCE.ps6000SetDataBuffer(handle, channel, adcValues, numberOfSamples, downsample);
        } catch (Exception e) {
            throw new RuntimeException("ps6000SetDataBuffer failed with exception: " + e.getMessage());
        }
        if (status != PicoScope6000Library.PS6000_OK) {
            throw new RuntimeException("ps6000SetDataBuffer failed with error code: " + status);
        }
    }

    private void getADCValues(IntByReference adcValuesLength) {
        int status;
        try {
            status = PicoScope6000Library.INSTANCE.ps6000GetValues(handle, 0, adcValuesLength, 1, (short) 0, 0, null);
        } catch (Exception e) {
            throw  new RuntimeException("ps6000GetValues failed with exception: " + e.getMessage());
        }
        if (status != PicoScope6000Library.PS6000_OK) {
            throw new RuntimeException("ps6000GetValues failed with error code: " + status);
        }
        System.out.printf("Captured %d samples\n", adcValuesLength.getValue());
    }

    private void writeIntoCSV(double[] voltValues, int sampleNumber, Path filePath) {
        double currentTime = 0;

        // Write into CSV file
        try (FileWriter writer = new FileWriter(filePath.toAbsolutePath().toFile())) {
            writer.append("Time,Channel\n");
            writer.append("(ms),(V)\n");
            writer.append(",\n");

            for (int i = 0; i < sampleNumber; i++) {
                writer.append(String.format("%.6f,%.6f\n", currentTime, voltValues[i]));
                currentTime = (currentTime + timeInterval) / 1e6;
            }

            System.out.println("Data has been written to " + filePath);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void store(Path file) {
        System.out.println("> Store");
        // wait until all data are measured
        waitForSamples();
        // set buffer for final values
//        short[] adcValues = new short[numberOfSamples];
//        setBuffer(adcValues);
//
//        // retrieve ADC samples
//        IntByReference numberOfCapturedSamples = new IntByReference(numberOfSamples);
//        getADCValues(numberOfCapturedSamples);
//
//        // convert into volt values
//        double[] voltValues = adc2Volt(adcValues, maxAdcValue, 2.0);
//        writeIntoCSV(voltValues, numberOfCapturedSamples.getValue(), file);
        System.out.println("< Store done");
    }

    @Override
    public void stopDevice() {
        System.out.println("> Stop device");
        int status;
        try {
            status = PicoScope6000Library.INSTANCE.ps6000Stop(handle);
        } catch (Exception e) {
            throw new RuntimeException("ps6000Stop failed with exception: " + e.getMessage());
        }

        if (status != PicoScope6000Library.PS6000_OK) {
            throw new RuntimeException("ps6000Stop failed with error code: " + status);
        }
        System.out.println("> Stop device OK");
    }

    @Override
    public void finish() {
        System.out.println("> Finish");
        // stop measuring
        try {
            stopDevice();
        } catch (Exception e) {
            // try to close device anyway
            System.out.println(e.getMessage());
        }

        // close device
        PicoScope6000Library.INSTANCE.ps6000CloseUnit(handle);
        System.out.println("> Finish OK");
    }
}
