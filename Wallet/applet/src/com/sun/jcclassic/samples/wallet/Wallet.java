/** 
 * Copyright (c) 1998, 2017, Oracle and/or its affiliates. All rights reserved.
 * 
 */

/*
 */

/*
 * @(#)Wallet.java	1.11 06/01/03
 */

package com.sun.jcclassic.samples.wallet;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.OwnerPIN;
import javacard.framework.Util;
import javacard.security.CryptoException;
import javacard.security.ECPublicKey;
import javacard.security.KeyBuilder;
import javacard.security.KeyPair;
import javacard.security.PrivateKey;
import javacard.security.PublicKey;
import javacard.security.RSAPrivateKey;
import javacard.security.RSAPublicKey;
import javacard.security.RandomData;
import javacardx.crypto.Cipher;

public class Wallet extends Applet {

	protected static KeyPair basicKeyPair;
	
    /* constants declaration */

    // code of CLA byte in the command APDU header
    final static byte Wallet_CLA = (byte) 0x80;
    
    final static byte CVM_RULE_SIZE = 10;
    final static byte CVM_LIST_SIZE = 3;
    
    final static byte AMOUNT1 = 50;
    final static byte AMOUNT2 = 100;

    // codes of INS byte in the command APDU header
    final static byte VERIFY = (byte) 0x20;
    final static byte CREDIT = (byte) 0x30;
    final static byte DEBIT = (byte) 0x40;
    final static byte GET_BALANCE = (byte) 0x50;
    final static byte SEND_CVM_LIST = (byte) 0x51;
    final static byte GET_CHALLENGE = (byte) 0x52;
    final static byte RECEIVE_ENCRYPTED_PIN = (byte) 0x53;

    // maximum balance
    final static short MAX_BALANCE = 0x7FFF;
    // maximum transaction amount
    final static byte MAX_TRANSACTION_AMOUNT = 127;

    // maximum number of incorrect tries before the
    // PIN is blocked
    final static byte PIN_TRY_LIMIT = (byte) 0x03;
    // maximum size PIN
    final static byte MAX_PIN_SIZE = (byte) 0x08;

    // signal that the PIN verification failed
    final static short SW_VERIFICATION_FAILED = 0x6300;
    // signal the the PIN validation is required
    // for a credit or a debit transaction
    final static short SW_PIN_VERIFICATION_REQUIRED = 0x6301;
    // signal invalid transaction amount
    // amount > MAX_TRANSACTION_AMOUNT or amount < 0
    final static short SW_INVALID_TRANSACTION_AMOUNT = 0x6A83;

    // signal that the balance exceed the maximum
    final static short SW_EXCEED_MAXIMUM_BALANCE = 0x6A84;
    // signal the the balance becomes negative
    final static short SW_NEGATIVE_BALANCE = 0x6A85;

    /* instance variables declaration */
    OwnerPIN pin;
    short balance;
    private boolean wasEncrypted = false;
    byte challenge = 0;
	private PrivateKey privateKey;

    private Wallet(byte[] bArray, short bOffset, byte bLength) {

        // It is good programming practice to allocate
        // all the memory that an applet needs during
        // its lifetime inside the constructor
        pin = new OwnerPIN(PIN_TRY_LIMIT, MAX_PIN_SIZE);

        byte iLen = bArray[bOffset]; // aid length
        bOffset = (short) (bOffset + iLen + 1);
        byte cLen = bArray[bOffset]; // info length
        bOffset = (short) (bOffset + cLen + 1);
        byte aLen = bArray[bOffset]; // applet data length

        // The installation parameters contain the PIN
        // initialization value
        pin.update(bArray, (short) (bOffset + 1), aLen);
        register();

    } // end of the constructor

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        // create a Wallet applet instance
        new Wallet(bArray, bOffset, bLength);
    } // end of install method

    @Override
    public boolean select() {

        // The applet declines to be selected
        // if the pin is blocked.
        if (pin.getTriesRemaining() == 0) {
            return false;
        }

        return true;

    }// end of select method

    @Override
    public void deselect() {

        // reset the pin value
        pin.reset();

    }

    @Override
    public void process(APDU apdu) {

        // APDU object carries a byte array (buffer) to
        // transfer incoming and outgoing APDU header
        // and data bytes between card and CAD

        // At this point, only the first header bytes
        // [CLA, INS, P1, P2, P3] are available in
        // the APDU buffer.
        // The interface javacard.framework.ISO7816
        // declares constants to denote the offset of
        // these bytes in the APDU buffer

        byte[] buffer = apdu.getBuffer();
        // check SELECT APDU command

        if (apdu.isISOInterindustryCLA()) {
            if (buffer[ISO7816.OFFSET_INS] == (byte) (0xA4)) {
                return;
            }
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        // verify the reset of commands have the
        // correct CLA byte, which specifies the
        // command structure
        if (buffer[ISO7816.OFFSET_CLA] != Wallet_CLA) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        switch (buffer[ISO7816.OFFSET_INS]) {
            case GET_BALANCE:
                getBalance(apdu);
                return;
            case DEBIT:
                debit(apdu);
                return;
            case CREDIT:
                credit(apdu);
                return;
            case VERIFY:
                verify(apdu);
                return;
            case SEND_CVM_LIST:
                sendCVMList(apdu);
                return;
            case GET_CHALLENGE:
                getChallenge(apdu);
                return;
            case RECEIVE_ENCRYPTED_PIN:
            	receiveEncryptedPIN(apdu);
                return;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }

    } // end of process method

    private void credit(APDU apdu) {

        // access authentication
        if (!pin.isValidated()) {
            ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
        }

        byte[] buffer = apdu.getBuffer();

        // Lc byte denotes the number of bytes in the
        // data field of the command APDU
        byte numBytes = buffer[ISO7816.OFFSET_LC];

        // indicate that this APDU has incoming data
        // and receive data starting from the offset
        // ISO7816.OFFSET_CDATA following the 5 header
        // bytes.
        byte byteRead = (byte) (apdu.setIncomingAndReceive());

        // it is an error if the number of data bytes
        // read does not match the number in Lc byte
        if ((numBytes != 1) || (byteRead != 1)) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        // get the credit amount
        byte creditAmount = buffer[ISO7816.OFFSET_CDATA];

        // check the credit amount
        if ((creditAmount > MAX_TRANSACTION_AMOUNT) || (creditAmount < 0)) {
            ISOException.throwIt(SW_INVALID_TRANSACTION_AMOUNT);
        }

        // check the new balance
        if ((short) (balance + creditAmount) > MAX_BALANCE) {
            ISOException.throwIt(SW_EXCEED_MAXIMUM_BALANCE);
        }

        // credit the amount
        balance = (short) (balance + creditAmount);

    } // end of deposit method

    private void debit(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        byte numBytes = (buffer[ISO7816.OFFSET_LC]);

        byte byteRead = (byte) (apdu.setIncomingAndReceive());

        if ((numBytes != 1) || (byteRead != 1)) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        // get debit amount
        byte debitAmount = buffer[ISO7816.OFFSET_CDATA];

        // access authentication
        if (debitAmount <= AMOUNT1 || (debitAmount <= AMOUNT2 && pin.isValidated()) || (debitAmount > AMOUNT2 && pin.isValidated() && wasEncrypted == true)) {
            // check debit amount
            if ((debitAmount > MAX_TRANSACTION_AMOUNT) || (debitAmount < 0)) {
                ISOException.throwIt(SW_INVALID_TRANSACTION_AMOUNT);
            }

            // check the new balance
            if ((short) (balance - debitAmount) < (short) 0) {
                ISOException.throwIt(SW_NEGATIVE_BALANCE);
            }

            balance = (short) (balance - debitAmount);
             
            pin.reset();
            wasEncrypted = false;
        } else {
        	ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
        }
    } // end of debit method
    
    private void sendCVMList(APDU apdu) {
    	
        byte[] buffer = apdu.getBuffer();

        short le = apdu.setOutgoing();

        if ((le != 30)) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        apdu.setOutgoingLength((byte)((byte) CVM_LIST_SIZE * (byte)CVM_RULE_SIZE));
        
        // X for the first rule
        buffer[0] = 0;
        buffer[1] = 0;
        buffer[2] = 0;
        buffer[3] = AMOUNT1;
        
        // Y for the first rule
        buffer[4] = 0;
        buffer[5] = 0;
        buffer[6] = 0;
        buffer[7] = 0;
        
        // The CVM code for the first rule
        // No CVM required
        buffer[8] = 0b01011111;
        
        // The condition code
        // If the currency is under the X value(AMOUNT1)
        buffer[9] = 0x06;
        
        
        // X for the second rule
        buffer[10] = 0;
        buffer[11] = 0;
        buffer[12] = 0;
        buffer[13] = AMOUNT2;
        
        // Y for the first rule
        buffer[14] = 0;
        buffer[15] = 0;
        buffer[16] = 0;
        buffer[17] = 0;
        
        // The CVM code for the first rule
        // Plain text PIN verification performed by the ICC
        buffer[18] = 0b01000001;
        
        // The condition code
        // If the currency is under the X value(AMOUNT2 this time)
        buffer[19] = 0x06;
        
        // X for the third rule
        buffer[20] = 0;
        buffer[21] = 0;
        buffer[22] = 0;
        buffer[23] = AMOUNT2;
        
        // Y for the first rule
        buffer[24] = 0;
        buffer[25] = 0;
        buffer[26] = 0;
        buffer[27] = 0;
        
        // The CVM code for the first rule
        // Enciphered PIN verification performed by the ICC
        buffer[28] = 0b01000100;
        
        // The condition code
        // If the currency is over the X value(AMOUNT2 again)
        buffer[29] = 0x07;
       
        // send the CVM_LIST_SIZE * CVM_RULE_SIZE-byte CVM_LIST at the offset
        // 0 in the apdu buffer
        apdu.sendBytes((short) 0, (byte)((byte) CVM_LIST_SIZE * (byte)CVM_RULE_SIZE));
    } // end of debit method
    
    private void getChallenge(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        // inform system that the applet has finished
        // processing the command and the system should
        // now prepare to construct a response APDU
        // which contains data field
        short le = apdu.setOutgoing();

        buffer[0] = 0x01;
        if (le < 1) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        // informs the CAD the actual number of bytes
        // returned
        apdu.setOutgoingLength((byte) 1);

//        // CREATE RNG OBJECT
        RandomData rngRandom = RandomData.getInstance(RandomData.ALG_PSEUDO_RANDOM );
        byte[] randomArray = new byte[8];
        rngRandom.setSeed(randomArray,(short)0,(short)8);
//        // GENERATE RANDOM BLOCK WITH 16 BYTES
        rngRandom.generateData(buffer, (short) 0, (short)1); 

        // send the 2-byte balance at the offset
        // 0 in the apdu buffer
        challenge = buffer[0];
        apdu.sendBytes((short) 0, (short) 1);
    } // end of debit method
    
    private void receiveEncryptedPIN(APDU apdu) {
    	byte[] buffer = apdu.getBuffer();
//
//        // Lc byte denotes the number of bytes in the
//        // data field of the command APDU
        byte numBytes = buffer[ISO7816.OFFSET_LC];
//
//        // indicate that this APDU has incoming data
//        // and receive data starting from the offset
//        // ISO7816.OFFSET_CDATA following the 5 header
//        // bytes.
        byte byteRead = (byte) (apdu.setIncomingAndReceive());
        
        try {
            
            RSAPrivateKey rsaPrivate = (RSAPrivateKey) KeyBuilder.buildKey(KeyBuilder.TYPE_RSA_PRIVATE, KeyBuilder.LENGTH_RSA_512, false);
            
            byte[] modulus = new byte[]{-117, 102, 38, -25, 11, 111, 15, -69, 105, 99, -58, -2, -20, 121, 71, 9, 24, -119, 127, -93, 52, 76, 26, 25, 76, 28, 67, 12, 57, 58, 63, 120, 95, 112, 62, 6, -51, 18, -16, 60, -37, 107, 18, -4, -86, -4, 54, 83, 55, -36, -85, -63, 76, -10, 117, -97, -25, -78, 120, -36, 9, -68, -3, 107};
            byte[] exponent = new byte[]{-122, 116, -80, 127, 88, 98, -10, -116, -79, 57, -63, 94, 111, -33, 6, -86, 122, 85, 93, -100, -96, -69, -22, -52, -115, -62, 16, -43, -64, 121, 51, 111, -50, 45, -20, -75, 119, -51, -111, 99, -112, 91, -112, -95, 72, -13, 14, 16, 18, 75, 5, -97, -122, -119, -120, 41, 45, 105, 72, -60, 98, -8, -21, 81};
            
            rsaPrivate.setExponent(exponent, (short) 0, (short) exponent.length);
            rsaPrivate.setModulus(modulus, (short) 0, (short) modulus.length);
            
            Cipher rsaCipher = Cipher.getInstance(Cipher.ALG_RSA_PKCS1, false);
            rsaCipher.init(rsaPrivate, Cipher.MODE_DECRYPT);
             
            short size = rsaCipher.doFinal(buffer, ISO7816.OFFSET_CDATA, (short)numBytes, buffer,  (short) 0);
            
            if (pin.check(buffer, (byte)0, (byte) 5) == false && challenge == buffer[5]) {
                ISOException.throwIt(SW_VERIFICATION_FAILED);
            }
            
            wasEncrypted = true;
       } catch (CryptoException e) {
            short reason = e.getReason();
            
            ISOException.throwIt(reason);
       }
    } // end of debit method

    private void getBalance(APDU apdu) {

        byte[] buffer = apdu.getBuffer();

        // inform system that the applet has finished
        // processing the command and the system should
        // now prepare to construct a response APDU
        // which contains data field
        short le = apdu.setOutgoing();

        if (le < 2) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        // informs the CAD the actual number of bytes
        // returned
        apdu.setOutgoingLength((byte) 2);

        // move the balance data into the APDU buffer
        // starting at the offset 0
        buffer[0] = (byte) (balance >> 8);
        buffer[1] = (byte) (balance & 0xFF);

        // send the 2-byte balance at the offset
        // 0 in the apdu buffer
        apdu.sendBytes((short) 0, (short) 2);

    } // end of getBalance method

    private void verify(APDU apdu) {

        byte[] buffer = apdu.getBuffer();
        // retrieve the PIN data for validation.
        byte byteRead = (byte) (apdu.setIncomingAndReceive());

        // check pin
        // the PIN data is read into the APDU buffer
        // at the offset ISO7816.OFFSET_CDATA
        // the PIN data length = byteRead
        if (pin.check(buffer, ISO7816.OFFSET_CDATA, byteRead) == false) {
            ISOException.throwIt(SW_VERIFICATION_FAILED);
        }

    } // end of validate method
   
} // end of class Wallet

