package com.intchip;

/**
 * Any class implemening this interface can be used for STK500 protocol.
 */
public interface SerialDeviceInterface {
    /**
     * @return The name of the serial device
     */
    String getName();

    /**
     * Open the serial device
     * @return 0 if success, otherwise -1
     */
    int open();

    /**
     * Close the serial device, do some finalize works here.
     */
    void close();

    /**
     * Set the baud rate to commnicate with the target serial device.
     * @param baud The baud rate.
     * @return 0 if success, otherwise -1
     */
    int setspeed(long baud);

    /**
     * Send some bytes to the target serial device.
     *
     * @param buf   The byte buffer to send
     * @param size  The size of bytes to send
     * @return  How many bytes have been sent, -1 if failed.
     */
    int send(byte[] buf, int size);

    /**
     * Receive some bytes from the target serial device and store them
     * in the buf. Note the buf must be not null and larger or equal to size.
     *
     * @param buf   The buffer to hold the received bytes, not null
     * @param size  The length to receive
     * @return  How many bytes have been received, -1 if errors happen.
     */
    int recv(byte[] buf, int size);

    /**
     * Read and ingore the data from the target serial device,
     * until nothing can be read.
     *
     * @return How many bytes have been read.
     */
    int drain();
}
