package com.company;

import com.sun.javacard.apduio.Apdu;
import com.sun.javacard.apduio.CadClientInterface;
import com.sun.javacard.apduio.CadDevice;
import com.sun.javacard.apduio.CadTransportException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final String capFilePath =
            "F:\\Facultate\\SmartCards\\SmartCards-LoyalityApplet\\Wallet\\applet\\apdu_scripts\\cap-com.sun.jcclassic.samples.wallet.script";

    private static final byte[][] TERMINAL_CVM_LIST = new byte[][]{new byte[]{50, 95, 6}, new byte[]{100, 65, 6}, new byte[]{100, 68, 7}};
    private static byte[][] CARD_CVM_LIST;

    public static void main(String[] args) throws IOException, CadTransportException {
        CadClientInterface cad;
        Socket sock;
        sock = new Socket("localhost", 9025);
        InputStream is = sock.getInputStream();
        OutputStream os = sock.getOutputStream();

        cad = CadDevice.getCadClientInstance(CadDevice.PROTOCOL_T1, is, os);

        cad.powerUp();

        try (Stream<String> stream = Files.lines(Paths.get(capFilePath))) {
            stream.filter(s -> !s.isEmpty() && s.charAt(1) != '/' && !s.equals("powerup;"))
                    .map(s -> {
                        List<String[]> strings = new ArrayList<>();

                        String[] splits = s.split(" ");
                        strings.add(Arrays.copyOfRange(splits, 0, 4));
                        strings.add(Arrays.copyOfRange(splits, 5, splits.length - 1));
                        strings.add(Arrays.copyOfRange(splits, splits.length - 1, splits.length));

                        return strings;
                    })
                    .forEach(strings -> {
                        Apdu apdu = new Apdu();

                        List<Byte> collect = Arrays.stream(strings.get(0))
                                .map(s -> {
                                    byte b = 0;
                                    b += Integer.parseInt(String.valueOf(s.charAt(2)), 16) * 16;
                                    b += Integer.parseInt(String.valueOf(s.charAt(3)), 16);

                                    return b;
                                })
                                .collect(Collectors.toList());
                        byte[] bytes = new byte[4];
                        for (int i = 0; i < collect.size(); i++) {
                            Byte aByte = collect.get(i);
                            bytes[i] = aByte;
                        }
                        apdu.command = bytes;

                        collect = Arrays.stream(strings.get(1))
                                .map(s -> {
                                    byte b = 0;
                                    b += Integer.parseInt(String.valueOf(s.charAt(2)), 16) * 16;
                                    b += Integer.parseInt(String.valueOf(s.charAt(3)), 16);

                                    return b;
                                })
                                .collect(Collectors.toList());
                        bytes = new byte[strings.get(1).length];
                        for (int i = 0; i < collect.size(); i++) {
                            Byte aByte = collect.get(i);
                            bytes[i] = aByte;
                        }
                        byte b = 0;
                        b += Integer.parseInt(String.valueOf(strings.get(2)[0].charAt(2)), 16) * 16;
                        b += Integer.parseInt(String.valueOf(strings.get(2)[0].charAt(3)), 16);

                        apdu.setDataIn(bytes);

                        try {
                            cad.exchangeApdu(apdu);
                        } catch (IOException | CadTransportException e) {
                            e.printStackTrace();
                        }

                        System.out.println(apdu);
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }

        // create wallet
        Apdu apdu = new Apdu();
        apdu.command = new byte[]{(byte) 0x80, (byte) 0xB8, 0x00, 0x00};
        apdu.setDataIn(new byte[]{0x0a, (byte) 0xa0, 0x00, 0x00, 0x00, 0x62, 0x03, 0x01, 0x0c, 0x06, 0x01, 0x08, 0x00,
                0x00, 0x05, 0x01, 0x02, 0x03, 0x04, 0x05});
        cad.exchangeApdu(apdu);

        System.out.println(apdu);

        // select wallet
        apdu = new Apdu();
        apdu.command = new byte[]{0x00, (byte) 0xA4, 0x04, 0x00};
        apdu.setDataIn(new byte[]{(byte) 0xa0, 0x0, 0x0, 0x0, 0x62, 0x3, 0x1, 0xc, 0x6, 0x1});
        cad.exchangeApdu(apdu);

        System.out.println(apdu);

        // receive the CVM_LIST
        apdu = new Apdu();
        apdu.command = new byte[]{(byte) 0x80, (byte) 0x51, 0x00, 0x00};
        apdu.setDataIn(new byte[]{});
        apdu.setLe(0x1E);

        cad.exchangeApdu(apdu);

        // creating the CARD_CVM_LIST
        CARD_CVM_LIST = new byte[][]{new byte[]{apdu.getDataOut()[3], apdu.getDataOut()[8], apdu.getDataOut()[9]},
                new byte[]{apdu.getDataOut()[13], apdu.getDataOut()[18], apdu.getDataOut()[19]},
                new byte[]{apdu.getDataOut()[23], apdu.getDataOut()[28], apdu.getDataOut()[29]}};

        cad.powerDown();
    }
}