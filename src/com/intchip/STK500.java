package com.intchip;

/**
 * A simple implementation for STK500 protocol.
 * This protocol is used for pc (or mobile devices) to communicate with the
 * avr based devices, e.g. Arduino UNO and Arduino Nano.
 * Note we simplify the whole protocol to just fit the behavious of Arduino UNO's
 * optiboot.
 * This code is referenced to the opensource project 'avrdude', please
 * see http://www.nongnu.org/avrdude for details.
 */
public class STK500 {

    static final int MAX_SYNC_ATTEMPTS  = 10;

    /**** STK Response constants ****/
    static final byte Resp_STK_OK       = 0x10;
    static final byte Resp_STK_FAILED   = 0x11;
    static final byte Resp_STK_UNKNOWN  = 0x12;
    static final byte Resp_STK_NODEVICE  = 0x13;
    static final byte Resp_STK_INSYNC   = 0x14;
    static final byte Resp_STK_NOSYNC   = 0x15;

    /**** STK Special constants ****/
    static final byte Sync_CRC_EOP      = 0x20;

    /**** STK Command constants ****/

    static final byte Cmnd_STK_GET_SYNC         = 0x30;
    static final byte Cmnd_STK_GET_SIGN_ON      = 0x31;

    static final byte Cmnd_STK_SET_PARAMETER    = 0x40;
    static final byte Cmnd_STK_GET_PARAMETER    = 0x41;
    static final byte Cmnd_STK_SET_DEVICE       = 0x42;
    static final byte Cmnd_STK_SET_DEVICE_EXT   = 0x45;

    static final byte Cmnd_STK_ENTER_PROGMODE   = 0x50;
    static final byte Cmnd_STK_LEAVE_PROGMODE   = 0x51;
    static final byte Cmnd_STK_CHIP_ERASE       = 0x52;
    static final byte Cmnd_STK_CHECK_AUTOINC    = 0x53;
    static final byte Cmnd_STK_LOAD_ADDRESS     = 0x55;

    static final byte Cmnd_STK_PROG_FLASH       = 0x60;
    static final byte Cmnd_STK_PROG_PAGE        = 0x64;

    static final byte Cmnd_STK_READ_FLASH       = 0x70;
    static final byte Cmnd_STK_READ_PAGE        = 0x74;


    private SerialDeviceInterface serial;

    public STK500(SerialDeviceInterface serial) {
        this.serial = serial;
    }

    /**
     * Open the serial devive with the given baud rate.
     * @param baud  The speed to communicate with target side.
     * @return  0 if success, otherwise -1.
     */
    public int open(long baud) {
        if (serial.open() != 0 ||
                serial.setspeed(baud) != 0)
            return -1;

        // drain any extraneous input
        serial.drain();

        if (getsync() < 0)
            return -1;

        return 0;
    }

    /**
     * Close the serial device.
     */
    public void close() {
        serial.close();
    }

    /**
     * Try to get sync with the target side (may a avr-based device),
     * waiting for the target device to switch to bootloader and answer
     * the command.
     * @return 0 if success, otherwise -1.
     */
    public int getsync() {
        byte [] buf = new byte[2];
        byte [] resp = new byte[1];

        // get in sync
        buf[0] = Cmnd_STK_GET_SYNC;
        buf[1] = Sync_CRC_EOP;

        // First send and drain a few times to get rid of line noise

        serial.send(buf, 2);
        serial.drain();
        serial.send(buf, 2);
        serial.drain();

        int attempt;
        for (attempt = 0; attempt < MAX_SYNC_ATTEMPTS; attempt++) {
            serial.send(buf, 2);
            serial.recv(resp, 1);

            if (resp[0] == Resp_STK_INSYNC)
                break;
            System.err.println(
                    String.format("%s: STK500.getsync() attempt %d of %d: not in sync: resp=0x%02x",
                            serial.getName(), attempt + 1, MAX_SYNC_ATTEMPTS, resp[0])
            );
        }

        if (attempt == MAX_SYNC_ATTEMPTS) {
            serial.drain();
            return -1;
        }

        if (serial.recv(resp, 1) < 0)
            return -1;

        if (resp[0] != Resp_STK_OK) {
            System.err.println(
                String.format("%s: STK500.getsync(): can't communicate with device: resp=0x%02x",
                    serial.getName(), resp[0])
            );
            return -1;
        }

        return 0;
    }

    /**
     * Leaving the flash programming mode.
     * @return 0 if success, otherwise -1.
     */
    public int disable() {
        /*
            No need for optiboot,
            since the Cmnd_STK_LEAVE_PROGMODE will be ignored by optiboot.
         */
        return 0;
    }

    /**
     * Makeing the target device to enter the flash programming mode.
     *
     * @return 0 if success, otherwise -1.
     */
    public int programEnable() {
        byte[] buf = new byte[2];

        for (int tries = 0; ; tries++) {
            buf[0] = Cmnd_STK_ENTER_PROGMODE;
            buf[1] = Sync_CRC_EOP;

            serial.send(buf, 2);
            if (serial.recv(buf, 1) < 0)
                return -1;

            if (buf[0] == Resp_STK_NOSYNC) {
                if (tries > 33) {
                    System.err.println(
                            serial.getName() + ": STK500.programEnable(): can't get into sync"
                    );
                    return -1;
                }
                if (getsync() < 0)
                    return -1;
                continue;
            }

            if (buf[0] != Resp_STK_INSYNC) {
                System.err.println(
                        String.format("%s: STK500.programEnable(): protocal error, expect=0x%02x, resp=0x%02x",
                                serial.getName(), Resp_STK_INSYNC, buf[0])
                );
            }

            if (serial.recv(buf, 1) < 0)
                return  -1;
            if (buf[0] == Resp_STK_OK)
                return 0;

            if (buf[0] == Resp_STK_NODEVICE) {
                System.err.print(
                        serial.getName() + ": STK500.programEnable(): no device"
                );
                return  -1;
            }

            if (buf[0] == Resp_STK_FAILED) {
                System.err.print(
                        serial.getName() + ": STK500.programEnable(): failed to enter programing mode"
                );
                return  -1;
            }

            System.err.println(
                    String.format("%s: STK500.programEnable(): unknown response=0x%02x",
                            serial.getName(), buf[0])
            );

            break;
        }

        return -1;
    }

    /**
     * Telling the target device the base address will be programming.
     * @param addr The base address of flash, in byte.
     * @return 0 if success, otherwise -1.
     */
    public int loadaddr(long addr) {
        byte[] buf = new byte[4];

        for (int tries = 0; ; tries++) {
            buf[0] = Cmnd_STK_LOAD_ADDRESS;
            buf[1] = (byte)(addr & 0xff);
            buf[2] = (byte)((addr >> 8) & 0xff);
            buf[3] = Sync_CRC_EOP;

            serial.send(buf, 4);

            if (serial.recv(buf, 1) < 0)
                return -1;

            if (buf[0] == Resp_STK_NOSYNC) {
                if (tries > 33) {
                    System.err.println(
                            serial.getName() + ": STK500.loadaddr(): can't get into sync"
                    );
                    return -1;
                }
                if (getsync() < 0)
                    return -1;
                continue;
            }

            if (buf[0] != Resp_STK_INSYNC) {
                System.err.println(
                        String.format("%s: STK500.loadaddr(): protocal error, expect=0x%02x, resp=0x%02x",
                                serial.getName(), Resp_STK_INSYNC, buf[0])
                );
                return -1;
            }

            if (serial.recv(buf, 1) < 0)
                return  -1;
            if (buf[0] == Resp_STK_OK)
                return 0;

            System.err.println(
                    String.format("%s: STK500.loadaddr(): protocal error, expect=0x%02x, resp=0x%02x",
                            serial.getName(), Resp_STK_OK, buf[0])
            );
            break;
        }

        return -1;
    }


    /**
     * Call pagedWrite(mem, pagesize, addr, 0, mem.length).
     */
    public int pagedWrite(byte[] mem, int pagesize, int addr) {
      return pagedWrite(mem, pagesize, addr, 0, mem.length);
    }

    /**
     * Write a serial block date to the flash of target device, with the
     * given base address and length, both in byte.
     *
     * @param mem The data to be write on the flash.
     * @param pagesize  The page size of target device's flash.
     * @param addr  The base address to write to.
     * @param offset The start offset of mem to write.
     * @param length  The length to write.
     * @return 0 if success, -3 if try too many times, -4 if not insync,
     *          -5 if receive an unknown response, otherwise -1.
     */
    public int pagedWrite(byte[] mem, int pagesize, int addr, int offset, int length) {
        byte[] buf = new byte[pagesize + 16];

        int n = addr + length;
        int blocksize = pagesize;

        for (; addr < n; addr += blocksize) {
            if (n - addr < pagesize)
                blocksize = n - addr;
            else
                blocksize = pagesize;

            for (int tries = 0; ; tries++) {
                loadaddr(addr / 2);

                int i = 0;
                buf[i++] = Cmnd_STK_PROG_PAGE;
                buf[i++] = (byte)((blocksize >> 8) & 0xff);
                buf[i++] = (byte)(blocksize & 0xff);
                buf[i++] = (byte)('F');

                System.arraycopy(mem, offset, buf, i, blocksize);

                i += blocksize;

                buf[i++] = Sync_CRC_EOP;

                serial.send(buf, i);

                if (serial.recv(buf, 1) < 0)
                    return  -1;

                if (buf[0] == Resp_STK_NOSYNC) {
                    if (tries > 33) {
                        System.err.println(
                                serial.getName() + ": STK500.pagedWrite(): can't get into sync"
                        );
                        return -3;
                    }
                    if (getsync() < 0)
                        return -1;
                    continue;
                }

                if (buf[0] != Resp_STK_INSYNC) {
                    System.err.println(
                            String.format("%s: STK500.pagedWrite(): (a) protocol error," +
                                    "expect=0x%02x, resp=0x%02x", serial.getName(),
                                    Resp_STK_INSYNC, buf[0])
                    );
                }

                if (serial.recv(buf, 1) < 0) {
                    return -1;
                }

                if (buf[0] != Resp_STK_OK) {
                    System.err.println(
                            String.format("%s: STK500.pagedWrite(): (a) protocol error," +
                                            "expect=0x%02x, resp=0x%02x", serial.getName(),
                                    Resp_STK_OK, buf[0])
                    );
                    return -5;
                }

                break;
            }

            offset += blocksize;
        }

        return length;
    }

    /**
     *  Call pagedLoad(mem, pagesize, addr, 0, mem.length).
     */
    public int pagedLoad(byte[] mem, int pagesize, int addr) {
        return pagedLoad(mem, pagesize, addr, 0, mem.length);
    }

     /**
     * Read a serial block date from the flash of target device,
     * and save the data in the given buffer.
     *
     * @param mem The buffer to hold the received data, not null.
     * @param pagesize  The page size of target device's flash.
     * @param addr  The base address to read from.
     * @param offset The start offset of mem to store the received data.
     * @param length  The length to read.
     * @return 0 if success, -3 if try too many times, -4 if not insync,
     *          -5 if receive an unknown response, otherwise -1.
     */
    public int pagedLoad(byte[] mem, int pagesize, int addr, int offset, int length) {
        byte[] buf = new byte[pagesize > 16 ? pagesize : 16];

        int n = addr + length;
        int blocksize = pagesize;

        for (; addr < n; addr += blocksize) {
            if (n - addr < pagesize)
                blocksize = n - addr;
            else
                blocksize = pagesize;

            for (int tries = 0; ; tries++) {
                loadaddr(addr / 2);

                buf[0] = Cmnd_STK_READ_PAGE;
                buf[1] = (byte)((blocksize >> 8) & 0xff);
                buf[2] = (byte)(blocksize & 0xff);
                buf[3] = (byte)('F');
                buf[4] = Sync_CRC_EOP;

                serial.send(buf, 5);

                if (serial.recv(buf, 1) < 0)
                    return  -1;

                if (buf[0] == Resp_STK_NOSYNC) {
                    if (tries > 33) {
                        System.err.println(
                                serial.getName() + ": STK500.pagedWrite(): can't get into sync"
                        );
                        return -3;
                    }
                    if (getsync() < 0)
                        return -1;
                    continue;
                }

                if (buf[0] != Resp_STK_INSYNC) {
                    System.err.println(
                            String.format("%s: STK500.pagedLoad(): (a) protocol error," +
                                            "expect=0x%02x, resp=0x%02x", serial.getName(),
                                    Resp_STK_INSYNC, buf[0])
                    );
                    return -4;
                }

                if (serial.recv(buf, blocksize) < blocksize) {
                    System.err.println(serial.getName() + ": STK500.pagedLoad(): read error");
                    return -1;
                } else {
                    System.arraycopy(buf, 0, mem, offset, blocksize);
                    offset += blocksize;
                }

                if (serial.recv(buf, 1) < 0)
                    return -1;

                if (buf[0] != Resp_STK_OK) {
                    System.err.println(
                            String.format("%s: STK500.pagedLoad(): (a) protocol error," +
                                            "expect=0x%02x, resp=0x%02x", serial.getName(),
                                    Resp_STK_OK, buf[0])
                    );
                    return -5;
                }

                break;
            }

        }

        return length;
    }

}
