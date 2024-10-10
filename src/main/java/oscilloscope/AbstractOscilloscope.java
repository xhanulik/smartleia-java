package oscilloscope;


import oscilloscope.drivers.PicoScope6000Driver;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

public abstract class AbstractOscilloscope {
    final private boolean DEBUG = false;

    /**
     * Array of implemented oscilloscope drivers
     */
    static Class<?>[] oscilloscopeDrivers = {
            PicoScope6000Driver.class
    };

//    protected static Args args = null;

//    protected AbstractOscilloscope(Args args) {
//        AbstractOscilloscope.args = args;
//    }

    /**
     * Convert ADC to volt values
     * @param voltValue value in Volts
     * @param voltageRange range of voltage values
     * @param maxAdcValue max ADC value
     * @return ADC value
     */
    protected double volt2Adc(double voltValue, double voltageRange, double maxAdcValue) {
        return (voltValue / voltageRange) * maxAdcValue;
    }

    /**
     *
     * @param adcValues
     * @param maxAdcValue
     * @param voltageRange
     * @return
     */
    protected static double[] adc2Volt(short[] adcValues, int maxAdcValue, double voltageRange) {
        double[] voltages = new double[adcValues.length];
        for (int i = 0; i < adcValues.length; i++) {
            voltages[i] = (adcValues[i] / (double) maxAdcValue) * voltageRange;
        }
        return voltages;
    }

    /**
     * Debug print
     * @param format A format string
     * @param args Arguments
     */
    protected void printDebug(String format, Object ... args) {
        if (DEBUG) {
            System.out.printf(format, args);
        }
    }

    /** Factory method
     *
     * @return constructed {@link AbstractOscilloscope} object
     */
    public static AbstractOscilloscope create() {
        for (Class<?> driver : oscilloscopeDrivers) {
            try {
                Constructor<?> constructor = driver.getConstructor();
                AbstractOscilloscope device = (AbstractOscilloscope) constructor.newInstance();
                if (device.connect()) {
                    return device;
                }
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        throw new RuntimeException("No oscilloscope connected!");
    }

    public abstract boolean connect();
    public abstract void setup();
    public abstract void startMeasuring();
    public abstract void stopDevice();
    public abstract void store(Path file, int cutOffFrequency) throws IOException;
    public abstract void finish();
}
