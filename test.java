package driver;

public class test {
    //@Test
    //@Manual
    @Fuzzing
    @DisplayName("SIGNUP interrupted via power loss test")
    void interruptedPowerSignup() throws Exception {
        // Idea: we will perform one execution which is interrupted, then        second one without interruption which must succeed
        // Similar to interrupts via perfTraps, but this setup stops        powering reader/card

        SerialPort[] ports = SerialPort.getCommPorts();
        assertTrue(ports.length > 0);
        // Assume first port to be used by Arduino controlling power
        interruptions
        SerialPort comPort = ports[0];
        assertTrue(comPort.openPort());
        Thread.sleep(3000);

        PMC.buildPerfMapping();
        ArrayList<ActionAPDUDecorator> interrupts = new ArrayList<>();
        short DELAY_STEP = 3;

        int POWEROFF_DURATION = 300;
        //NXPJCOP180 int DELAY_APDU_SEND = 90; // time necessary for
        arduino to receive interruption setting via serial port
        int DELAY_APDU_SEND = 150; // time necessary for arduino to receive
        interruption setting via serial port

        // DEBUGGING: these operations after interrupt was NOT successfully
        finished on JCOP3 (OK on JCOP4)
        // crypto.checkTLV(Consts.TLV_CERTIFICATE_AND_SIG, apduBuffer,
        offset); failed due to incorrect decryption of input buffer (but MAC was OK)
        //generatePowerInterupts(Consts.INS_CARD_INIT, 240, 270, 1,
        POWEROFF_DURATION, DELAY_APDU_SEND, comPort, interrupts);
        //generatePowerInterupts(Consts.INS_CARD_INIT, 440, 480, 1,
        POWEROFF_DURATION, DELAY_APDU_SEND, comPort, interrupts);

        HashMap<String, Short> max_delays = new HashMap<>();
/*
     // NXP JCOP4 J3R180 older
     max_delays.put("INS_CARD_INIT", (short) 1300);
     max_delays.put("INS_CARD_SETUEK", (short) 330);
     max_delays.put("INS_CARD_BACKUP_PHONE", (short) 480);
     max_delays.put("INS_CARD_BACKUP_SERVER", (short) 450);
     max_delays.put("INS_CARD_COMPUTEMASTERKEY", (short) 1400);
  */
        // contacless-only NXP JCOP4 J3R180
        max_delays.put("INS_CARD_INIT", (short) 700);
        max_delays.put("INS_CARD_SETUEK", (short) 60);
        max_delays.put("INS_CARD_BACKUP_PHONE", (short) 220);
        max_delays.put("INS_CARD_BACKUP_SERVER", (short) 200);
        max_delays.put("INS_CARD_COMPUTEMASTERKEY", (short) 700);
        /**/

        // Add interrupts for doCardInit()
        short INS_CARD_INIT_MIN_DELAY = 4;
        short INS_CARD_INIT_MAX_DELAY = max_delays.get("INS_CARD_INIT");
        generatePowerInterupts(Consts.INS_CARD_INIT,
                INS_CARD_INIT_MIN_DELAY, INS_CARD_INIT_MAX_DELAY, DELAY_STEP,
                POWEROFF_DURATION, DELAY_APDU_SEND, comPort, interrupts);
        // BUGBUG: intentionally incorrect apdu INS
        //generatePowerInterupts(Consts.INS_VERIFY_PIN,
        INS_CARD_INIT_MIN_DELAY, INS_CARD_INIT_MAX_DELAY, DELAY_STEP,
                POWEROFF_DURATION, comPort, interrupts);

        /**/


        // Add interrupts for setUEK()
        int INS_CARD_SETUEK_MIN_DELAY = 4;
        short INS_CARD_SETUEK_MAX_DELAY = max_delays.get("INS_CARD_SETUEK");
        generatePowerInterupts(Consts.INS_CARD_SETUEK,
                INS_CARD_SETUEK_MIN_DELAY, INS_CARD_SETUEK_MAX_DELAY, DELAY_STEP,
                POWEROFF_DURATION, DELAY_APDU_SEND, comPort, interrupts);
        /**/


        // Add interrupts for backupPhone()
        int INS_CARD_BACKUP_PHONE_MIN_DELAY = 4;
        short INS_CARD_BACKUP_PHONE_MAX_DELAY =
                max_delays.get("INS_CARD_BACKUP_PHONE");
        generatePowerInterupts(Consts.INS_CARD_BACKUP_PHONE,
                INS_CARD_BACKUP_PHONE_MIN_DELAY, INS_CARD_BACKUP_PHONE_MAX_DELAY,
                DELAY_STEP, POWEROFF_DURATION, DELAY_APDU_SEND, comPort, interrupts);
        /**/

        // Add interrupts for backupServer()
        int INS_CARD_BACKUP_SERVER_MIN_DELAY = 10;
        short INS_CARD_BACKUP_SERVER_MAX_DELAY =
                max_delays.get("INS_CARD_BACKUP_SERVER");
        generatePowerInterupts(Consts.INS_CARD_BACKUP_SERVER,
                INS_CARD_BACKUP_SERVER_MIN_DELAY, INS_CARD_BACKUP_SERVER_MAX_DELAY,
                DELAY_STEP, POWEROFF_DURATION, DELAY_APDU_SEND, comPort, interrupts);
        /**/

        // Add interrupts for computeMasterKey
        int INS_CARD_COMPUTEMASTERKEY_MIN_DELAY = 10;
        short INS_CARD_COMPUTEMASTERKEY_MAX_DELAY =
                max_delays.get("INS_CARD_COMPUTEMASTERKEY");
        generatePowerInterupts(Consts.INS_CARD_COMPUTEMASTERKEY,
                INS_CARD_COMPUTEMASTERKEY_MIN_DELAY,
                INS_CARD_COMPUTEMASTERKEY_MAX_DELAY, DELAY_STEP, POWEROFF_DURATION,
                DELAY_APDU_SEND, comPort, interrupts);
        /**/
        // These were detected with failures
        //interrupts.add(new
        ActionAPDUDecoratorSingleInterrupt(Consts.INS_CARD_INIT,
                PMC.TRAP_doCardInit_1, PMC.getPerfStopName(PMC.TRAP_doCardInit_1)));

        interruptedSignup(interrupts, comPort, true, INTERRUPTED_PHASE.SIGNUP);
    }





import javax.smartcardio.ResponseAPDU;
import java.io.IOException;

import com.fazecast.jSerialComm.SerialPort;

    public class ActionAPDUDecoratorSinglePowerInterrupt implements
            ActionAPDUDecorator {
        CardChannel m_origChannel;
        byte        m_matchINS = 0;
        int         m_interuptDelay = 0;  // number of ms to wait before
        power is interrupted
        int         m_interuptDuration = 0; // number of ms for which te
        power is interrupted
        int         m_delayApduSend = 0;
        String      m_stopName = "";
        SerialPort  m_comPort = null;
        boolean     m_interruptSet = false;

        public ActionAPDUDecoratorSinglePowerInterrupt(byte matchINS, int
                delayInMS, int durationInMS, int delayApduSendMS, String stopName,
                                                       SerialPort comPort) {
            m_matchINS = matchINS;
            m_interuptDelay = delayInMS;
            m_interuptDuration = durationInMS;
            m_delayApduSend = delayApduSendMS;
            m_stopName = stopName;
            m_comPort = comPort;
            m_interruptSet = false;
        }

        @Override
        public boolean match(APDUCommand cmd) {
            return m_matchINS == (byte) cmd.getIns();
        }

        /**
         * Execute an action before sending apdu (if required)
         */
        @Override
        public void preAction(APDUCommand cmd) {
            // Write to Arduino board delay to next power interrupt
            if (!m_interruptSet) { // do it only once
                byte[] writeBuffer =
                        Util.concat(Util.intToByteArray(m_interuptDelay),
                                Util.intToByteArray(m_interuptDuration));
                System.out.println(String.format("\n-----> Setting power
                        interrupt in %d ms, for %d ms, send postponed for %d ms\n",
                m_interuptDelay, m_interuptDuration, m_delayApduSend));
                try {
                    m_comPort.writeBytes(writeBuffer, writeBuffer.length);
                    m_interruptSet = true;
                    if (m_delayApduSend > 0) { // Sleep before continuing
                        to allow Arduino to process data
                        Thread.sleep(m_delayApduSend);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * Execute an action after apdu is send (if required)
         */
        @Override
        public ResponseAPDU postAction(ResponseAPDU resp) throws IOException {
            // no change to response apdu, no action
            return resp;
        }

        /**
         * Sets currently used channel for apdu transmission
         * @param channel
         */
        @Override
        public void setChannel(CardChannel channel) {
            m_origChannel = channel;
        }

        /**
         * Cleanup to remove action presence
         */
        @Override
        public void cleanup() {
            // no action required
        }

        @Override
        public boolean isExpectedError(int sw) {
            // Return true as no specific error is expected, but check if
            the trigger was set based on the detected INS
            return m_interruptSet ? true : false;
        }

        public int getInterruptDelay() {
            return m_interuptDelay;
        }

        public String toString() {
            return String.format("INS = 0x%x, stopName = %s, delay = %d
                    ms", this.m_matchINS, this.m_stopName, this.m_interuptDelay);
}
