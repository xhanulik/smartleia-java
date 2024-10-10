package filter;

import uk.me.berndporr.iirj.Butterworth;

/**
 * Class that represents low-pass filter.
 * It is used to filter voltage array of the trace.
 * 
 * @author Martin Podhora
 */
public class LowPassFilter {

    Butterworth butterworth;
    public static final int ORDER = 1;

    /**
     * Constructor
     *
     * @param samplingFreq  Sampling frequency in Hz
     * @param cutOffFreq    Cut-off frequency in Hz
     */
    public LowPassFilter(int samplingFreq, int cutOffFreq) {
        butterworth = new Butterworth();
        butterworth.lowPass(ORDER, samplingFreq, cutOffFreq);
    }

    public double applyLowPassFilter(double value) {
        return butterworth.filter(value);
    }
}
