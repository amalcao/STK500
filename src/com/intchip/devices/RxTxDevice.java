package com.intchip.devices;

import com.intchip.SerialDeviceInterface;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Using RxTx to handle the serial port communication.
 */
public class RxTxDevice
        implements SerialDeviceInterface {

    private static final int OPEN_PORT_TIMEOUT = 1000;
    private static final int MAX_RECV_TIMEOUT = 1000;
    private static final int MAX_DRAIN_TIMEOUT = 250;

    private SerialPort port;
    private String name;

    public RxTxDevice(String name) {
        this.name = name;
        this.port = null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int open() {
        try {
            CommPortIdentifier portId = CommPortIdentifier.getPortIdentifier(name);

            if (portId == null)  {
                System.err.println("Cannot find COM port " + name);
                return -1;
            }

            port = (SerialPort) portId.open(this.getClass().getName(),
                    OPEN_PORT_TIMEOUT);


            // Clear the DTR and RTS to unload the RESET capacitor
            port.setDTR(false);
            port.setRTS(false);

            Thread.sleep(250);

            // Set DTR and RTS back to high
            port.setDTR(true);
            port.setRTS(true);

            Thread.sleep(50);

        } catch (Exception e) {
            return -1;
        }

        return 0;
    }

    @Override
    public void close() {
        port.setDTR(false);
        port.setRTS(false);

        port.close();
    }

    @Override
    public int setspeed(long baud) {
        try {
            port.setSerialPortParams((int) baud,
                    SerialPort.DATABITS_8,
                    SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);
        } catch (Exception e) {
            return -1;
        }

        return 0;
    }

    @Override
    public int send(byte[] buf, int size) {
        try {
            OutputStream out = port.getOutputStream();
            out.write(buf, 0, size);
            out.flush();
        } catch (Exception e) {
            System.err.println("send error!");
            return -1;
        }

        return size;
    }

    @Override
    public int recv(byte[] buf, int size) {
        int nr = 0;
        try {
            port.enableReceiveTimeout(MAX_RECV_TIMEOUT);

            InputStream in = port.getInputStream();

            while (nr < size) {
                int n = in.read(buf, nr, size - nr);
                // FIXME: read zero byte, maybe tiemout?
                if (n == 0) break;
                nr += n;
            }
        } catch (Exception e) {
            System.err.println("recv error!");
            return -1;
        }
        return nr;
    }

    @Override
    public int drain() {
        try {
            port.enableReceiveTimeout(MAX_DRAIN_TIMEOUT);

            InputStream in = port.getInputStream();

            byte[] buf = new byte[128];
            int nr = buf.length;

            do {
                in.read(buf, 0, nr);
                nr = in.available();
            } while (nr > 0);

        } catch (Exception e) {
            System.err.println("drain error!");
            return -1;
        }
        return 0;
    }
}
