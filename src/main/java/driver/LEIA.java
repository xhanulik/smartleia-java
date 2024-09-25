package driver;

import com.fazecast.jSerialComm.*;

public class LEIA {
    private SerialPort serialPort = null;
    private String device;
    private final int USB_VID = 0x3483;
    private final int USB_PID = 0x0BB9;

    public LEIA() {
        this.open();
    }

    public void open() {
        SerialPort[] availablePorts = SerialPort.getCommPorts();
        int count = 0;

        // Iterate through available ports and match by VID and PID
        for (SerialPort port : availablePorts) {
            if (port.getVendorID() == USB_VID && port.getProductID() == USB_PID) {
                System.out.printf("Found matching device %s (%d/%d)\n", port.getDescriptivePortName(), USB_VID, USB_PID);
                count++;
            }
        }

        if (count > 2 || count == 0) {
            throw new RuntimeException("No LEIA device connected");
        }

        // Test connection to the ports
        for (SerialPort port : availablePorts) {
            if (port.getVendorID() == USB_VID && port.getProductID() == USB_PID) {
                System.out.printf("Connecting to device %s (%d/%d)\n", port.getDescriptivePortName(), USB_VID, USB_PID);
                try {
                    port.setBaudRate(115200);
                    port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 1, 0);
                    if (port.openPort()) {
                        serialPort = port;
                        break;
                    }
                } catch (Exception e) {
                    System.out.println("Exception caught");
                }
            }
        }
        serialPort.closePort();
    }
}
