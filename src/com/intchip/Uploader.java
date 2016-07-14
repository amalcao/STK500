package com.intchip;

import com.intchip.devices.RxTxDevice;
import cz.jaybee.intelhex.DataListener;
import cz.jaybee.intelhex.Parser;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Top level class to upload the user compiled binary code to the flash
 * of avr mcu board.
 */
public class Uploader {
    /**
     * Abstract class to show the progress bar for read/write.
     */
    public static abstract class ProgressReporter {
        /**
         * Show the percentage of current progress,
         * note you should reset the inner state if current
         * progress reaches 100%.
         *
         * @param percentage 0 ~ 100
         */
        public abstract void report(int percentage);
    }

    /**
     * A naive class to hold the section memory read from
     * the intel hex or other output file.
     */
    private static class Section {
        public int address;
        public byte[] content;

        public Section(int address, byte[] content) {
            this.address = address;
            this.content = content;
        }

        /**
         * If the sect can be appended to the end of current section,
         * we append it to current one and merge the sections' data together.
         *
         * @param sect Another section try to append to.
         * @return true if merged, otherwise false.
         */
        public boolean merge(Section sect) {
            if (address + content.length == sect.address) {
                byte[] newContent =
                        new byte[content.length + sect.content.length];

                System.arraycopy(content, 0, newContent, 0, content.length);
                System.arraycopy(sect.content, 0, newContent, content.length, sect.content.length);

                this.content = newContent;
                return true;
            }
            return false;
        }
    }

    private STK500 protocol;
    private int baud;
    private int pagesize;
    private ProgressReporter progress;

    public Uploader(STK500 protocol, int speed, int pagesize) {
        this.protocol = protocol;
        this.baud = speed;
        this.pagesize = pagesize;
        this.progress = null;
    }

    /**
     * Set the current progress reporter. If reporter is null, show nothing.
     * @param progress
     */
    public void addProgressReporter(ProgressReporter progress) {
        this.progress = progress;
    }

    /**
     * Get the current progress reporter.
     * @return
     */
    public ProgressReporter getProgressReporter() {
        return progress;
    }

    /**
     * Set the baud rate of the serial port to communicate with the target MCU.
     * @param baud
     */
    public void setBaud(int baud) {
        this.baud = baud;
    }

    /**
     * Get the current baud rate.
     * @return
     */
    public int getBaud() {
        return this.baud;
    }

    /**
     * Set the pagesize of target MCU's programming flash.
     * @param pagesize
     */
    public void setPagesize(int pagesize) {
        this.pagesize = pagesize;
    }

    /**
     * Get the pagesize.
     * @return
     */
    public int getPagesize() {
        return pagesize;
    }

    /**
     * Call the progress reporter to report current progress.
     *
     * @param done  How many works have been done.
     * @param total The total size of works.
     */
    private void reportProgress(int done, int total) {
        if (progress != null) {
            int percentage = (int)((double) done / (double) total * 100);
            progress.report(percentage);
        }
    }

    /**
     * Upload a serial of sections to the target MCU.
     * Note the base address of each section must align to
     * the pagesize.
     *
     * @param sections
     * @return 0 if success, otherwise -1.
     */
    public int upload(List<Section> sections) {
        try {
            int total = 0;
            int done = 0;

            // Get the total size to upload
            for (Section sect : sections) {
                total += sect.content.length;
            }

            // Init the device size
            if (protocol.open(baud) != 0) {
                System.err.println("Cannot open serial port.");
                return -1;
            }

            // Upload all the section data to the target side
            System.out.println("Uploading the data ...");

            protocol.programEnable();

            for (Section sect : sections) {
                if (protocol.pagedWrite(sect.content, pagesize, sect.address) < 0) {
                    System.err.println(
                            String.format("Error when write page at 0x%04x!", sect.address)
                    );
                    return -1;
                }

                done += sect.content.length;
                reportProgress(done, total);
            }

            protocol.disable();

            System.out.println("Upload done.");

            System.out.println("Downloading the data ...");

            // Try to get the data from the target side
            done = 0;
            byte[] buf = new byte[total];

            for (Section sect : sections) {
                if (protocol.pagedLoad(buf, pagesize, sect.address, done, sect.content.length) < 0) {
                    System.err.println(
                            String.format("Error when load page at 0x%04x", sect.address)
                    );
                    return -1;
                }

                done += sect.content.length;
                reportProgress(done, total);
            }

            System.out.println("Download done.");

            System.out.println("Verify the data ...");

            // All data received , verify if they are same with the original data.
            int offset = 0;
            for (Section sect : sections) {
                int length = sect.content.length;
                for (int i = 0; i < length; i++) {
                    if (buf[offset++] != sect.content[i]) {
                        System.err.println(
                                String.format("Verfiy failure at 0x%04x, expected is 0x%02x, received is 0x%02x.",
                                        sect.address + i, sect.content[i], buf[offset])
                        );
                        return -1;
                    }
                }
            }

            System.out.println("Verify OK!");

            return 0;
        } finally {
            protocol.close();
        }
    }

    /**
     * Upload a single serial section.
     *
     * @param mem  The memory data of the section.
     * @param address The base address of the section, must align to pagesize.
     * @return 0 if success, otherwise -1.
     */
    public int upload(byte[] mem, int address) {
        List<Section> sections = new ArrayList<>();
        sections.add(new Section(address, mem));

        return upload(sections);
    }

    /**
     * Try to upload code of an intel hex format file to the programming space of target MCU,
     * and then read the data back to verify if all the data is written correctly.
     *
     * @param ihex The intel hex format file to hold the executable code for MCU.
     * @param tty   The target serial port connected to the MCU's uart port.
     * @param speed The baud rate.
     * @param pagesize The pagesize of the programming flash of target MCU.
     * @return  0 if success, otherwise -1.
     * @throws Exception
     */
    public static int avrdude(String ihex, String tty, int speed, int pagesize)
        throws Exception
    {
        Uploader up = new Uploader(
                new STK500(new RxTxDevice(tty)), speed, pagesize
        );

        up.addProgressReporter(new ProgressReporter() {
            private int last = 0;
            @Override
            public void report(int percentage) {
                int cnt = percentage / 5;
                while (cnt > last) {
                    System.out.print("#");
                    last ++;
                }

                if (percentage == 100) {
                    System.out.println();
                    last = 0;
                }
            }
        });

        List<Section> sections = new ArrayList<>();

        InputStream is = new FileInputStream(ihex);

        Parser parser = new Parser(is);

        parser.setDataListener(new DataListener() {
            Section section = null;

            @Override
            public void data(long address, byte[] data) {
                Section sect = new Section((int)address, data);

                // The base address of each section must be align to pagesize,
                // however, the ihex parser not do any merge, so we must merge
                // the data of sections nearby.
                if (section == null ||
                        section.content.length >= pagesize ||
                        !section.merge(sect)) {
                    section = sect;
                    sections.add(sect);
                }
            }

            @Override
            public void eof() {}
        });

        parser.parse();

        if (sections.size() == 0)
            return -1;

        return up.upload(sections);
    }

    /**
     * Load a section start with address from the target MCU into the buffer mem.
     */
    public int load(byte[] mem, int address) {
        try {
            if (protocol.open(baud) != 0 ||
                    protocol.pagedLoad(mem, pagesize, address) < 0) {
                return -1;
            }

            return 0;
        } finally {
            protocol.close();
        }
    }

    /**
    // Load the bootloader of target MCU and verify if it is same with the given ihex file.
    //  Just for testing ..
    public static void verifyBootloader(String bootloader, String tty) throws Exception {
        Uploader up = new Uploader(
                new STK500(new RxTxDevice(tty)), 115200, 128
        );

        byte[] mem = new byte[1024]; // 1KB optibootloader
        if (up.load(mem, 0x7C00) != 0) {
            System.err.println("error when loading bootloader!");
            return;
        }

        InputStream is = new FileInputStream(bootloader);

        Parser parser = new Parser(is);

        parser.setDataListener(new DataListener() {
            Section section = null;

            @Override
            public void data(long address, byte[] data) {
                for (int i = 0; i < data.length; i++) {
                    int index = (int)address + i - (int)0x7c00;
                    if (data[i] != mem[index]) {
                        System.err.println(
                                String.format("verify failure at 0x%04x, expected 0x%02x, got 0x%02x",
                                        address, mem[index], data[i])
                        );
                        break;
                    }
                }
            }

            @Override
            public void eof() {}
        });

        parser.parse();
    }
    */

    public static void main(String[] arguments) throws Exception {
       avrdude(arguments[0], "/dev/ttyUSB1", 115200, 128);
    }
}
