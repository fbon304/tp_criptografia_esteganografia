package stegobmp.crypto;

/** Error de cifrado o descifrado, informado al usuario como un error de argumentos o datos. */
public final class CryptoException extends IllegalArgumentException {
    public static final String DECRYPTION_FAILED =
            "No se pudo descifrar el contenido oculto: contraseña, algoritmo o modo incorrectos";

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
