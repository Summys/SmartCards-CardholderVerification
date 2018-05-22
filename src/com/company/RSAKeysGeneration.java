package com.company;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

public class RSAKeysGeneration {
    private static final String PUBLIC_KEY_FILENAME = "public.txt";
    private static final String PRIVATE_KEY_FILENAME = "private.txt";

    public static void writeRSAKeysToFiles() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(512);
        KeyPair keyPair = keyGen.generateKeyPair();

        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        BigInteger modulus = privateKey.getModulus();
        BigInteger exponent = privateKey.getPrivateExponent();

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(PRIVATE_KEY_FILENAME))) {
            bw.write(toByteArrayString(modulus));
            bw.newLine();
            bw.write(toByteArrayString(exponent));
        } catch (IOException e) {
            e.printStackTrace();
        }


        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(PUBLIC_KEY_FILENAME))) {
            bw.write(toStringPublicKey(publicKey));
        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
        }
    }

    private static String toByteArrayString(BigInteger number) {
        byte[] array = number.toByteArray();
        if (array[0] == 0) {
            byte[] tmp = new byte[array.length - 1];
            System.arraycopy(array, 1, tmp, 0, tmp.length);
            array = tmp;
        }
        return Arrays.toString(array);
    }

    private static String toStringPublicKey(PublicKey publ) throws GeneralSecurityException {
        KeyFactory fact = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec spec = fact.getKeySpec(publ, X509EncodedKeySpec.class);
        return Base64.getEncoder().encodeToString(spec.getEncoded());
    }

    public static void main(String[] args) throws NoSuchAlgorithmException {
        RSAKeysGeneration.writeRSAKeysToFiles();
    }
}
