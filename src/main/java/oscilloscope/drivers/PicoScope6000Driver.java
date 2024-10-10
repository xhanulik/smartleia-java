package oscilloscope.drivers;


import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.ShortByReference;
import filter.LowPassFilter;
import oscilloscope.AbstractOscilloscope;
import oscilloscope.drivers.libraries.PicoScope6000Library;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class PicoScope6000Driver extends AbstractOscilloscope {
    short handle = 0;
    String deviceName;
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
        printDebug("> Opening device\n");
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
            printDebug("> Opening device NOK\n");
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
            printDebug("> Opening device \n");
            return false;
        }
        // get device name
        deviceName = new String(info, StandardCharsets.UTF_8);
        System.out.println("Connected device: " + deviceName);
        printDebug("> Opening device OK\n");
        return true;
    }

    private void setChannel(short channel, short range) {
        printDebug("> Set channel\n");
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
        printDebug("> Set channel OK\n");
    }

    private void setTrigger() {
        printDebug("> Set trigger\n");
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
        printDebug("> Set trigger OK\n");
    }

    private void calculateTimebase() {
        printDebug("> Getting timebase\n");
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
        System.out.printf("Device %s setup - Timebase: %d, time interval: %d, samples: %d, max samples: %d\n",
                deviceName, currentTimebase, currentTimeInterval.getValue(), numberOfSamples, currentMaxSamples.getValue());
        printDebug("< Getting timebase OK\n");
    }

    @Override
    public void setup() {
        setChannel(channel, channelRange);
        setTrigger();
        calculateTimebase();
        System.out.printf("Device %s configured\n", deviceName);
    }

    @Override
    public void startMeasuring() {
        printDebug("> Start measuring\n");
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
        printDebug("< Start measuring OK\n");
    }

    private void waitForSamples() {
        printDebug("> Wait for samples\n");
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
        printDebug("< Wait for samples OK\n");
    }

    private void setBuffer(Pointer buffer) {
        printDebug("> Set buffer\n");
        int status;

        try {
            status = PicoScope6000Library.INSTANCE.ps6000SetDataBuffer(handle, channel, buffer, numberOfSamples, downsample);
        } catch (Exception e) {
            throw new RuntimeException("ps6000SetDataBuffer failed with exception: " + e.getMessage());
        }
        if (status != PicoScope6000Library.PS6000_OK) {
            throw new RuntimeException("ps6000SetDataBuffer failed with error code: " + status);
        }
        printDebug("> Set buffer OK\n");
    }

    private void getValuesIntoBuffer(IntByReference adcValuesLength) {
        printDebug("> Get ADC values\n");
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
        printDebug("> Get ADC values OK\n");
    }

    private void writeIntoCSV(double[] voltValues, int sampleNumber, Path filePath, int cutOffFrequency) {
        printDebug("> Write into CSV\n");

        int samplingFrequency = (int) (1 / (timeInterval / 1_000_000_000.0)); // convert ns to
        LowPassFilter filter = null;

        if (cutOffFrequency > 0)
            filter = new LowPassFilter(3906250, cutOffFrequency);

        // Write into CSV file
        try (FileWriter writer = new FileWriter(filePath.toAbsolutePath().toFile())) {
            writer.append("Time,Channel\n");
            writer.append("(ms),(V)\n");
            writer.append(",\n");

            for (int i = 0; i < sampleNumber; i++) {
                if (filter != null)
                    writer.append(String.format("%9f,%.9f\n", (i * timeInterval) / 1e6, filter.applyLowPassFilter(voltValues[i])));
                else
                    writer.append(String.format("%9f,%.9f\n", (i * timeInterval) / 1e6, voltValues[i]));
            }

            System.out.println("Data has been written to " + filePath);

        } catch (IOException e) {
            throw new RuntimeException("Writing into CSV failed");
        }
        printDebug("> Write into CSV OK\n");
    }

    @Override
    public void store(Path file, int cutOffFrequency) {
        printDebug("> Store\n");
        // wait until all data are measured
        waitForSamples();
        // set buffer for final values
        short[] adcValues;
        // allocate memory for data
        try (Memory buffer = new Memory((long) numberOfSamples * Native.getNativeSize(Short.TYPE))) {
            // initialize buffer to zeroes
            for (int n = 0; n < numberOfSamples; n = n + 1) {
                buffer.setShort((long) n * Native.getNativeSize(Short.TYPE), (short) 0);
            }
            setBuffer(buffer);

            // retrieve ADC samples
            IntByReference numberOfCapturedSamples = new IntByReference(numberOfSamples);
            getValuesIntoBuffer(numberOfCapturedSamples);

            // store values from buffer into adcValues
            adcValues = new short[numberOfCapturedSamples.getValue()];
            buffer.read(0, adcValues, 0, adcValues.length);
        }
        // convert into volt values
        double[] voltValues = adc2Volt(adcValues, maxAdcValue, 2.0);
        writeIntoCSV(voltValues, adcValues.length, file, cutOffFrequency);

        printDebug("< Store OK\n");
    }

    @Override
    public void stopDevice() {
        printDebug("> Stop device\n");
        int status;
        try {
            status = PicoScope6000Library.INSTANCE.ps6000Stop(handle);
        } catch (Exception e) {
            throw new RuntimeException("ps6000Stop failed with exception: " + e.getMessage());
        }

        if (status != PicoScope6000Library.PS6000_OK) {
            throw new RuntimeException("ps6000Stop failed with error code: " + status);
        }
        printDebug("> Stop device OK\n");
    }

    @Override
    public void finish() {
        printDebug("> Finish\n");
        // stop measuring
        try {
            stopDevice();
        } catch (Exception e) {
            // try to close device anyway
            System.out.println(e.getMessage());
        }

        // close device
        PicoScope6000Library.INSTANCE.ps6000CloseUnit(handle);
        printDebug("> Finish OK\n");
        System.out.println("PicoScope 6000 disconnected");
    }
}
