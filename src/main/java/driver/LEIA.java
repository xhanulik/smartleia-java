package driver;

import com.fazecast.jSerialComm.*;

import java.nio.ByteBuffer;

public class LEIA {

    final int RESPONSE_LEN_SIZE = 4;
    final int COMMAND_LEN_SIZE = 4;
    private SerialPort serialPort = null;
    private String device;
    private final int USB_VID = 0x3483;
    private final int USB_PID = 0x0BB9;

    private final Object lock = new Object();


    public LEIA() {

    }

    /**
     * Try to detect connected LEIA board and open serial port for communication.
     */
    public void open() {
        System.out.println("Opening ports");
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

        // Test connection to the ports and try to open the final one
        for (SerialPort port : availablePorts) {
            if (port.getVendorID() == USB_VID && port.getProductID() == USB_PID) {
                System.out.printf("Connecting to device %s (%d/%d)\n", port.getDescriptivePortName(), USB_VID, USB_PID);
                try {
                    port.setBaudRate(115200);
                    port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 1, 0);
                    if (port.openPort()) {
                        serialPort = port;
                        device = port.getPortDescription();
                        System.out.printf("Serial port %s (%d/%d) is open and ready for communication\n",
                                serialPort.getDescriptivePortName(), USB_VID, USB_PID);
                        break;
                    }
                } catch (Exception e) {
                    port.closePort();
                    throw new RuntimeException("Cannot connect to LEIA device");
                }
            }
        }

        // read all bytes from port
        readAvailableBytes();
        testWaitingFlag();
        System.out.println("Ports opening OK");
    }

    private void isValidPort() {
        if (serialPort == null ||  !serialPort.isOpen()) {
            throw new RuntimeException("No serial connection created!");
        }
    }

    private byte[] readAvailableBytes() {
        isValidPort();

        System.out.println("Reading all bytes");

        int bytesRead = 0;
        int availableBytes = serialPort.bytesAvailable();
        byte[] buffer = new byte[availableBytes];  // Create a buffer with an appropriate size

        if (serialPort.bytesAvailable() > 0) {
            int bytes = serialPort.readBytes(buffer, availableBytes, bytesRead);
            bytesRead += bytes;
        }
        System.out.printf("%d bytes read\n", bytesRead);
        return buffer;
    }

    private void wait(int miliseconds) {
        try {
            Thread.sleep(miliseconds);
        } catch (InterruptedException ignored) {
            System.out.println("Sleep is overrated");
        }
    }

    private void testWaitingFlag() {
        System.out.println("Test waiting flag");
        isValidPort();
        readAvailableBytes();

        byte[] command = new byte[] { ' ' }; // b" "
        serialPort.writeBytes(command, command.length, 0);
        wait(100); // wait for 0.1s

        // Read 1 + all available bytes
        byte[] singleByte = new byte[1];
        int bytesRead = serialPort.readBytes(singleByte, 1);
        byte[] allBytes = readAvailableBytes();
        if (bytesRead == 0 && allBytes.length == 0)
            throw new RuntimeException();

        // combine
        byte[] buffer = new byte[1 + allBytes.length];
        buffer[0] = singleByte[0];  // First byte
        System.arraycopy(allBytes, 0, buffer, 1, allBytes.length);

        if (buffer[buffer.length - 1] != 87) {
            throw new RuntimeException("Cannot connect to LEIA.");
        }
        System.out.println("Waiting flag OK");
    }

    private void checkStatus() {
        System.out.println("Check status");
        byte[] status = new byte[1];
        int readBytes = serialPort.readBytes(status, status.length);

        if (readBytes == 0)
            throw new RuntimeException("No status flag received.");

        while(status[0] == 'w') {
            // reading wait extension flag
            readBytes = serialPort.readBytes(status, status.length);
            if (readBytes == 0)
                throw new RuntimeException("No status flag received.");
        }

        if (status[0] == 'U')
            throw new RuntimeException("LEIA firmware do not handle this command.");
        else if (status[0] == 'E')
            throw new RuntimeException("Unkwown error (E).");
        else if (status[0] != 'S')
            throw new RuntimeException("Invalid status flag '{s}' received.");

        readBytes = serialPort.readBytes(status, status.length);
        if (readBytes == 0)
            throw new RuntimeException("Status not received.");
        else if (status[0] != 0x00)
            throw new RuntimeException("Error status!");

        System.out.println("Status OK");
    }

    private void checkAck() {
        System.out.println("Check ACK");
        byte[] status = new byte[1];
        int readBytes = serialPort.readBytes(status, status.length);
        if (readBytes == 0 || status[0] != 'R')
            throw new RuntimeException("No response ack received.");
        System.out.println("ACK OK");
    }

    private void sendCommand(byte[] command, LEIAStructure struct) {
        System.out.println("Sending command");
        testWaitingFlag();
        serialPort.writeBytes(command, command.length, 0);

        if (struct == null) {
            // send simple byte command filled with zeroes
            byte[] zeroCommand = new byte[COMMAND_LEN_SIZE];
            serialPort.writeBytes(zeroCommand, zeroCommand.length, 0);
        } else {
            // send comprehensive command
            byte[] packedData = struct.pack();
            byte[] size = ByteBuffer.allocate(COMMAND_LEN_SIZE).putInt(packedData.length).array();
            serialPort.writeBytes(size, size.length, 0);
            serialPort.writeBytes(packedData, packedData.length, 0);
        }
        checkStatus();
        checkAck();
        System.out.println("Sending command OK");
    }

    private int readResponseSize() {
        System.out.println("Read response size");
        byte[] response = new byte[RESPONSE_LEN_SIZE];
        int read = serialPort.readBytes(response, RESPONSE_LEN_SIZE);
        if (read != RESPONSE_LEN_SIZE)
            throw new RuntimeException("Unexpected bytes for response size!");
        System.out.println("Read response size OK");
        return ByteBuffer.wrap(response).getInt();
    }

    /**
     *
     * @return True if card is inserted, false otherise
     * @implNote command ID: "?"
     */
    public boolean isCardInserted() {
        byte[] response = null;
        synchronized (lock) {
            this.sendCommand("?".getBytes(), null);
            int resSize = this.readResponseSize();
            if (resSize != 1) {
                throw new RuntimeException("Invalid response size for 'isCardInserted' (?) command.");
            }
            response = new byte[1];
            serialPort.readBytes(response, resSize);
        }
        return response[0] == 1;
    }

    /**
     * Configure connected smartcard
     * @param protocolToUse
     * @param ETUToUse
     * @param freqToUse
     * @param negotiatePts
     * @param negotiateBaudrate
     * @implNote command ID: "c" + LEIA structure
     */
    public void configureSmartcard(T protocolToUse, int ETUToUse, int freqToUse, boolean negotiatePts, boolean negotiateBaudrate) {

    }

    /**
     * @implNote command ID: "t"
     */
    public void getATR() {

    }

    /**
     * Simplified set_trigger_strategy routine for resetting all trigger strategies to none
     * @implNote target.set_trigger_strategy(1, point_list=[], delay=0)
     * @implNote command ID: "O" + trigger strategy struct
     */
    public void resetTriggerStrategy() {

    }

    /**
     * Simplified set_trigger_strategy routine for setting only pre-send APDu strategy
     * @implNote target.set_trigger_strategy(1, point_list=[TriggerPoints.TRIG_PRE_SEND_APDU], delay=0)
     * @implNote command ID: "O" + trigger strategy struct
     */
    public void setPreSendAPDUTriggerStrategy() {

    }

    /**
     * @implNote command ID: "a" + APDU struct packed
     */
    public void send_APDU() {}

    /**
     * Close opened port for LEIA device
     */
    public void close() {
        if (serialPort.isOpen()) {
            System.out.printf("Closing serial port %s (%d/%d)\n", serialPort.getDescriptivePortName(), USB_VID, USB_PID);
            serialPort.closePort();
            serialPort = null;
        }
    }

    public static void createAPDUFromBytes() {}
}
