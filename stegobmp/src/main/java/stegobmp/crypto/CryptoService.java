package stegobmp.crypto;

import stegobmp.cli.CliArguments.CipherAlgorithm;
import stegobmp.cli.CliArguments.CipherMode;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Cifrado compatible con {@code openssl enc -<alg> -pbkdf2 -iter 10000 -md sha256 -S 0000000000000000}.
 * La clave y el IV se derivan con PBKDF2-HMAC-SHA256 desde la contraseña con salt fijo de 8 bytes en cero.
 */
public final class CryptoService {
    private static final String KEY_DERIVATION = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 10_000;
    private static final byte[] SALT = new byte[8];

    public byte[] encrypt(byte[] plaintext, CipherAlgorithm algorithm, CipherMode mode, String password) {
        Objects.requireNonNull(plaintext, "El contenido a cifrar no puede ser null");
        try {
            return cipher(Cipher.ENCRYPT_MODE, algorithm, mode, password).doFinal(plaintext);
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("No se pudo cifrar el payload: " + exception.getMessage(), exception);
        }
    }

    public byte[] decrypt(byte[] ciphertext, CipherAlgorithm algorithm, CipherMode mode, String password) {
        Objects.requireNonNull(ciphertext, "El contenido a descifrar no puede ser null");
        try {
            return cipher(Cipher.DECRYPT_MODE, algorithm, mode, password).doFinal(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new CryptoException(CryptoException.DECRYPTION_FAILED, exception);
        }
    }

    private Cipher cipher(int operation, CipherAlgorithm algorithm, CipherMode mode, String password)
            throws GeneralSecurityException {
        Objects.requireNonNull(algorithm, "El algoritmo de cifrado no puede ser null");
        Objects.requireNonNull(mode, "El modo de cifrado no puede ser null");
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("No se puede cifrar ni descifrar sin contraseña");
        }

        Spec spec = Spec.of(algorithm);
        byte[] derived = deriveKeyAndIv(password, spec.keyLength() + spec.ivLength());
        SecretKeySpec key = new SecretKeySpec(Arrays.copyOfRange(derived, 0, spec.keyLength()), spec.name());
        IvParameterSpec iv = new IvParameterSpec(
                Arrays.copyOfRange(derived, spec.keyLength(), derived.length));

        Cipher cipher = Cipher.getInstance(spec.name() + "/" + transformation(mode));
        if (mode == CipherMode.ECB) {
            cipher.init(operation, key);
        } else {
            cipher.init(operation, key, iv);
        }
        return cipher;
    }

    private static byte[] deriveKeyAndIv(String password, int length) throws GeneralSecurityException {
        PBEKeySpec keySpec = new PBEKeySpec(password.toCharArray(), SALT, ITERATIONS, length * Byte.SIZE);
        try {
            return SecretKeyFactory.getInstance(KEY_DERIVATION).generateSecret(keySpec).getEncoded();
        } finally {
            keySpec.clearPassword();
        }
    }

    /** Equivalencias OpenSSL/JCE: cfb → CFB de 8 bits; ofb → OFB del tamaño de bloque (128 bits en AES). */
    private static String transformation(CipherMode mode) {
        return switch (mode) {
            case ECB -> "ECB/PKCS5Padding";
            case CBC -> "CBC/PKCS5Padding";
            case CFB -> "CFB8/NoPadding";
            case OFB -> "OFB/NoPadding";
        };
    }

    private record Spec(String name, int keyLength, int ivLength) {
        static Spec of(CipherAlgorithm algorithm) {
            return switch (algorithm) {
                case AES128 -> new Spec("AES", 16, 16);
                case AES192 -> new Spec("AES", 24, 16);
                case AES256 -> new Spec("AES", 32, 16);
                case TRIPLE_DES -> new Spec("DESede", 24, 8);
            };
        }
    }
}
