package com.company;

import com.sun.javacard.apduio.CadClientInterface;
import com.sun.javacard.apduio.CadDevice;
import com.sun.javacard.apduio.CadTransportException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class Main {

    public static void main(String[] args) throws IOException, CadTransportException {

        CadClientInterface cad;
        Socket sock;
        sock = new Socket("localhost", 9025);
        InputStream is = sock.getInputStream();
        OutputStream os = sock.getOutputStream();

        cad = CadDevice.getCadClientInstance(CadDevice.PROTOCOL_T1, is, os);

        cad.powerUp();

        System.out.println("Hello world");

        cad.powerDown();
    }
}
