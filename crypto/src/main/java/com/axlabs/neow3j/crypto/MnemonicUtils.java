package com.axlabs.neow3j.crypto;

import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Provides utility methods to generate random mnemonics and also generate
 * seeds from mnemonics.
 *
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">Mnemonic code
 * for generating deterministic keys</a>
 */
public class MnemonicUtils {

    private static final int SEED_ITERATIONS = 2048;
    private static final int SEED_KEY_SIZE = 512;
    private static List<String> WORD_LIST = null;

    /**
     * The mnemonic must encode entropy in a multiple of 32 bits. With more entropy security is
     * improved but the sentence length increases. We refer to the initial entropy length as ENT.
     * The allowed size of ENT is 128-256 bits.
     * <p>
     * <h3>Mnemonic generation algorithm</h3>
     * Given a randomly generated initial entropy of size ENT, first a checksum is generated by
     * taking the first {@code ENT / 32} bits of its SHA256 hash. This checksum is appended to
     * the end of the initial entropy. Next, these concatenated bits are split into groups of
     * 11 bits, each encoding a number from 0-2047, serving as an index into a wordlist. Finally,
     * we convert these numbers into words and use the joined words as a mnemonic sentence.
     *
     * @param initialEntropy The initial entropy to generate mnemonic from
     * @return The generated mnemonic
     * @throws IllegalArgumentException If the given entropy is invalid
     * @throws IllegalStateException    If the word list has not been loaded
     */
    public static String generateMnemonic(byte[] initialEntropy) {
        if (WORD_LIST == null) {
            WORD_LIST = populateWordList();
        }
        validateEntropy(initialEntropy);

        int ent = initialEntropy.length * 8;
        int checksumLength = ent / 32;

        byte checksum = calculateChecksum(initialEntropy);
        boolean[] bits = convertToBits(initialEntropy, checksum);

        int iterations = (ent + checksumLength) / 11;
        StringBuilder mnemonicBuilder = new StringBuilder();
        for (int i = 0; i < iterations; i++) {
            int index = toInt(nextElevenBits(bits, i));
            mnemonicBuilder.append(WORD_LIST.get(index));

            boolean notLastIteration = i < iterations - 1;
            if (notLastIteration) {
                mnemonicBuilder.append(" ");
            }
        }

        return mnemonicBuilder.toString();
    }

    /**
     * Create entropy from the mnemonic.
     *
     * @param mnemonic The input mnemonic which should be 128-160 bits in length containing
     *                 only valid words
     * @return Byte array representation of the entropy
     */
    public static byte[] generateEntropy(String mnemonic) {
        if (WORD_LIST == null) {
            WORD_LIST = populateWordList();
        }
        if (isMnemonicEmpty(mnemonic)) {
            throw new IllegalArgumentException("Mnemonic is empty");
        }

        String bits = mnemonicToBits(mnemonic);

        // split the binary string into ENT/CS.
        int totalLength = bits.length();
        int ent = (int) Math.floor(totalLength / 33) * 32;
        String entropyBits = bits.substring(0, ent);
        String checksumBits = rightPad(bits.substring(ent), "0", 8);

        byte[] entropy = bitsToBytes(entropyBits);
        validateEntropy(entropy);

        byte newChecksum = calculateChecksum(entropy);
        if (newChecksum != (byte) Integer.parseInt(checksumBits, 2)) {
            throw new IllegalArgumentException("Checksum of mnemonic is invalid");
        }

        return entropy;
    }

    /**
     * To create a binary seed from the mnemonic, we use the PBKDF2 function with a
     * mnemonic sentence (in UTF-8 NFKD) used as the password and the string "mnemonic"
     * + passphrase (again in UTF-8 NFKD) used as the salt. The iteration count is set
     * to 2048 and HMAC-SHA512 is used as the pseudo-random function. The length of the
     * derived key is 512 bits (= 64 bytes).
     *
     * @param mnemonic   The input mnemonic which should be 128-160 bits in length containing
     *                   only valid words
     * @param passphrase The passphrase which will be used as part of salt for PBKDF2
     *                   function
     * @return Byte array representation of the generated seed
     */
    public static byte[] generateSeed(String mnemonic, String passphrase) {
        if (isMnemonicEmpty(mnemonic)) {
            throw new IllegalArgumentException("Mnemonic is required to generate a seed");
        }
        passphrase = passphrase == null ? "" : passphrase;

        String salt = String.format("mnemonic%s", passphrase);
        PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(new SHA512Digest());
        gen.init(mnemonic.getBytes(UTF_8), salt.getBytes(UTF_8), SEED_ITERATIONS);

        return ((KeyParameter) gen.generateDerivedParameters(SEED_KEY_SIZE)).getKey();
    }

    public static boolean validateMnemonic(String mnemonic) {
        try {
            generateEntropy(mnemonic);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean isMnemonicEmpty(String mnemonic) {
        return mnemonic == null || mnemonic.trim().isEmpty();
    }

    private static boolean[] nextElevenBits(boolean[] bits, int i) {
        int from = i * 11;
        int to = from + 11;
        return Arrays.copyOfRange(bits, from, to);
    }

    private static void validateEntropy(byte[] entropy) {
        if (entropy == null) {
            throw new IllegalArgumentException("Entropy is required");
        }

        int ent = entropy.length * 8;
        if (ent < 128 || ent > 256 || ent % 32 != 0) {
            throw new IllegalArgumentException("The allowed size of ENT is 128-256 bits of "
                    + "multiples of 32");
        }
    }

    private static boolean[] convertToBits(byte[] initialEntropy, byte checksum) {
        int ent = initialEntropy.length * 8;
        int checksumLength = ent / 32;
        int totalLength = ent + checksumLength;
        boolean[] bits = new boolean[totalLength];

        for (int i = 0; i < initialEntropy.length; i++) {
            for (int j = 0; j < 8; j++) {
                byte b = initialEntropy[i];
                bits[8 * i + j] = toBit(b, j);
            }
        }

        for (int i = 0; i < checksumLength; i++) {
            bits[ent + i] = toBit(checksum, i);
        }

        return bits;
    }

    private static boolean toBit(byte value, int index) {
        return ((value >>> (7 - index)) & 1) > 0;
    }

    private static int toInt(boolean[] bits) {
        int value = 0;
        for (int i = 0; i < bits.length; i++) {
            boolean isSet = bits[i];
            if (isSet) {
                value += 1 << bits.length - i - 1;
            }
        }

        return value;
    }

    private static String mnemonicToBits(String mnemonic) {
        String[] words = mnemonic.split(" ");

        StringBuilder bits = new StringBuilder();
        for (String word : words) {
            int index = WORD_LIST.indexOf(word);
            if (index == -1) {
                throw new IllegalArgumentException(String.format(
                        "Mnemonic word '%s' should be in the word list", word));
            }
            bits.append(leftPad(Integer.toBinaryString(index), "0", 11));
        }

        return bits.toString();
    }

    private static byte[] bitsToBytes(String bits) {
        byte[] bytes = new byte[(int) Math.ceil(bits.length() / 8f)];
        int index = 0;
        for (int iByte = 0; iByte < bytes.length; iByte++) {
            String byteStr = bits.substring(index, Math.min(index + 8, bits.length()));
            bytes[iByte] = (byte) Integer.parseInt(byteStr, 2);
            index += 8;
        }

        return bytes;
    }

    private static String leftPad(String str, String padString, int length) {
        StringBuilder resultStr = new StringBuilder(str);
        while (resultStr.length() < length) {
            resultStr.insert(0, padString);
        }
        return resultStr.toString();
    }

    private static String rightPad(String str, String padString, int length) {
        StringBuilder resultStr = new StringBuilder(str);
        for (int i = str.length(); i < length; i++) {
            resultStr.append(padString);
        }
        return resultStr.toString();
    }

    private static byte calculateChecksum(byte[] initialEntropy) {
        int ent = initialEntropy.length * 8;
        byte mask = (byte) (0xff << 8 - ent / 32);
        byte[] bytes = Hash.sha256(initialEntropy);

        return (byte) (bytes[0] & mask);
    }

    private static List<String> populateWordList() {
        InputStream inputStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("en-mnemonic-word-list.txt");
        try {
            return readAllLines(inputStream);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> readAllLines(InputStream inputStream) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        List<String> data = new ArrayList<>();
        for (String line; (line = br.readLine()) != null; ) {
            data.add(line);
        }
        return data;
    }
}
