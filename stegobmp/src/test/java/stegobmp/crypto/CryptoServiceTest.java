package stegobmp.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import stegobmp.cli.CliArguments.CipherAlgorithm;
import stegobmp.cli.CliArguments.CipherMode;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceTest {
    private static final String PASSWORD = "oculto";
    private static final byte[] PLAINTEXT = "Texto de prueba stegobmp".getBytes(StandardCharsets.US_ASCII);
    private final CryptoService service = new CryptoService();

    /**
     * Vectores generados con:
     * {@code openssl enc -<cifrado> -pbkdf2 -iter 10000 -md sha256 -S 0000000000000000 -pass pass:oculto}
     * (sin la cabecera "Salted__"). El modo cfb de la consigna equivale a cfb8 en OpenSSL.
     */
    @ParameterizedTest(name = "{0} {1} coincide con OpenSSL")
    @CsvSource({
            "AES128, ECB, 0c973c9bfd87b23c024f5a00799ccb2b82d208d2166b0666d83dd22d885a204e",
            "AES128, CBC, 016cd9030ca56b42e0b67654c64437729998e805c4abb06e29eb2c83c3ab542d",
            "AES128, CFB, 4be09ffddd0dca68654621df97dee0f65ef224c66357aeb8",
            "AES128, OFB, 4b2909d8896700e1caa8d70d1e913e1cb86c632cb1395e5a",
            "AES192, CBC, 063e3ce1c7aa6c7390e7b48c35eb25da46b56f668c8ecc8ceb9f921eb854473a",
            "AES256, CBC, 5805242a990e2325c2656e6387593af0641b22b4e0013db358aad69dba8df342",
            "AES256, CFB, 77447193d938b891daf8e7a6eea72dd3730370b36b93b33d",
            "TRIPLE_DES, ECB, ff661e241c5dc81d82ce4c5b2d8ea665271b97de9ab4e98277c7c06546f88e89",
            "TRIPLE_DES, CBC, dc9c835f7750be5763914153e354dc277a9aafae97b887abd583ace9680d391a",
            "TRIPLE_DES, CFB, 40509fc2c18373cc0c8764ad8ac39f051dac04ec67a64b75",
            "TRIPLE_DES, OFB, 40066e2c55095941d79a821362e80c2689bba8565f2bab55"
    })
    void matchesOpenSslOutput(CipherAlgorithm algorithm, CipherMode mode, String expectedHex) {
        byte[] expected = HexFormat.of().parseHex(expectedHex);
        assertArrayEquals(expected, service.encrypt(PLAINTEXT, algorithm, mode, PASSWORD));
        assertArrayEquals(PLAINTEXT, service.decrypt(expected, algorithm, mode, PASSWORD));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("allCombinations")
    void roundTripsEveryAlgorithmAndModeWithAnyLength(CipherAlgorithm algorithm, CipherMode mode) {
        for (int length : new int[]{0, 1, 7, 8, 15, 16, 17, 423}) {
            byte[] plain = new byte[length];
            Arrays.fill(plain, (byte) 0xA5);
            byte[] ciphertext = service.encrypt(plain, algorithm, mode, PASSWORD);
            if (mode == CipherMode.ECB || mode == CipherMode.CBC) {
                int block = algorithm == CipherAlgorithm.TRIPLE_DES ? 8 : 16;
                assertEquals((length / block + 1) * block, ciphertext.length, "padding PKCS5");
            } else {
                assertEquals(length, ciphertext.length, "los modos de flujo no agregan padding");
            }
            assertArrayEquals(plain, service.decrypt(ciphertext, algorithm, mode, PASSWORD));
        }
    }

    @Test
    void wrongPasswordWithPaddingFailsWithClearMessage() {
        byte[] ciphertext = service.encrypt(PLAINTEXT, CipherAlgorithm.TRIPLE_DES, CipherMode.CBC, PASSWORD);
        CryptoException exception = assertThrows(CryptoException.class,
                () -> service.decrypt(ciphertext, CipherAlgorithm.TRIPLE_DES, CipherMode.CBC, "otra"));
        assertEquals(CryptoException.DECRYPTION_FAILED, exception.getMessage());
    }

    @Test
    void rejectsMissingPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> service.encrypt(PLAINTEXT, CipherAlgorithm.AES128, CipherMode.CBC, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.encrypt(PLAINTEXT, CipherAlgorithm.AES128, CipherMode.CBC, ""));
    }

    static Stream<Arguments> allCombinations() {
        return Arrays.stream(CipherAlgorithm.values())
                .flatMap(algorithm -> Arrays.stream(CipherMode.values()).map(mode -> Arguments.of(algorithm, mode)));
    }
}
