package io.neow3j.crypto.transaction;

import io.neow3j.crypto.Hash;
import io.neow3j.io.BinaryReader;
import io.neow3j.io.BinaryWriter;
import io.neow3j.io.NeoSerializable;
import io.neow3j.utils.ArrayUtils;
import io.neow3j.utils.Numeric;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static io.neow3j.constants.NeoConstants.MAX_PUBLIC_KEYS_PER_MULTISIG_ACCOUNT;
import static io.neow3j.constants.OpCode.CHECKMULTISIG;
import static io.neow3j.constants.OpCode.CHECKSIG;
import static io.neow3j.constants.OpCode.PUSHBYTES33;

public class RawVerificationScript extends NeoSerializable {

    private byte[] script;
    private String scriptHash;

    public RawVerificationScript() {
        script = new byte[0];
    }

    public RawVerificationScript(byte[] script) {
        this.script = script;
    }

    public static RawVerificationScript fromPublicKey(BigInteger publicKey) {
        byte[] script = ArrayUtils.concatenate(
                new byte[]{PUSHBYTES33.getValue()},
                publicKey.toByteArray(),
                new byte[]{CHECKSIG.getValue()});

        return new RawVerificationScript(script);
    }

    public static RawVerificationScript fromPublicKeys(int signingThreshold, List<BigInteger> publicKeys) {
        if (signingThreshold < 2 || signingThreshold > publicKeys.size()
                || publicKeys.size() > MAX_PUBLIC_KEYS_PER_MULTISIG_ACCOUNT) {
            throw new IllegalArgumentException();
        }
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
            BinaryWriter w = new BinaryWriter(byteStream);
            w.pushInteger(signingThreshold);
            for (BigInteger key : publicKeys) {
                w.pushData(key.toByteArray());
            }
            w.pushInteger(publicKeys.size());
            w.writeByte(CHECKMULTISIG.getValue());
            return new RawVerificationScript(byteStream.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Got an IOException without doing IO.");
        }
    }

    public byte[] getScript() {
        return script;
    }

    public String getScriptHash() {
        if (scriptHash == null) {
            // TODO Claude 02.07.19: Does it need to be reversed?
//            scriptHash = Numeric.toHexStringNoPrefix(ArrayUtils.reverseArray(
//                    Hash.sha256AndThenRipemd160(script)));
            scriptHash = Numeric.toHexStringNoPrefix(
                    Hash.sha256AndThenRipemd160(script));
        }
        return scriptHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RawVerificationScript)) return false;
        RawVerificationScript that = (RawVerificationScript) o;
        return Arrays.equals(getScript(), that.getScript());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getScript());
    }

    @Override
    public String toString() {
        return "VerificationScript{" +
                "script=" + Numeric.toHexStringNoPrefix(script) + '}';
    }

    @Override
    public void deserialize(BinaryReader reader) throws IOException {
        script = reader.readVarBytes();
    }

    @Override
    public void serialize(BinaryWriter writer) throws IOException {
        writer.writeVarBytes(script);
    }

}
