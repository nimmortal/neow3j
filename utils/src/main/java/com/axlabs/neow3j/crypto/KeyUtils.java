package com.axlabs.neow3j.crypto;

import static com.axlabs.neow3j.constants.NeoConstants.COIN_VERSION;
import static com.axlabs.neow3j.crypto.Hash.sha256;
import static com.axlabs.neow3j.utils.ArrayUtils.concatenate;

public class KeyUtils {

    // private key size (in bytes)
    public static final int PRIVATE_KEY_SIZE = 32;

    // encoded public key size (in bytes)
    public static final int PUBLIC_KEY_SIZE = 33;
    //
    public static final int PUBLIC_KEY_SIZE_NOT_ENCODED = 65;

    public static final int ADDRESS_SIZE = 34;

    public static final int PRIVATE_KEY_LENGTH_IN_HEX = PRIVATE_KEY_SIZE << 1;

    public static String toAddress(byte[] scriptHash) {
        byte[] data = new byte[1];
        data[0] = COIN_VERSION;
        byte[] dataAndScriptHash = concatenate(data, scriptHash);
        byte[] checksum = sha256(sha256(dataAndScriptHash));
        byte[] first4BytesCheckSum = new byte[4];
        System.arraycopy(checksum, 0, first4BytesCheckSum, 0, 4);
        byte[] dataToEncode = concatenate(dataAndScriptHash, first4BytesCheckSum);
        return Base58.encode(dataToEncode);
    }

    public static byte[] toScriptHash(String address) {
        byte[] data = Base58.decode(address);
        if (data.length != 25) {
            throw new IllegalArgumentException();
        }
        if (data[0] != COIN_VERSION) {
            throw new IllegalArgumentException();
        }
        byte[] checksum = sha256(sha256(data, 0, 21));
        for (int i = 0; i < 4; i++) {
            if (data[data.length - 4 + i] != checksum[i]) {
                throw new IllegalArgumentException();
            }
        }
        byte[] buffer = new byte[20];
        System.arraycopy(data, 1, buffer, 0, 20);
        return buffer;
    }

}