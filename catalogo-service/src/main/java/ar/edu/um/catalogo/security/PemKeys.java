package ar.edu.um.catalogo.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.core.io.Resource;

/**
 * Lectura de la clave publica RSA (X.509, "BEGIN PUBLIC KEY") con la que se validan los JWT.
 */
public final class PemKeys {

    private PemKeys() {}

    public static RSAPublicKey readPublicKey(Resource resource) {
        byte[] der = decodePem(resource, "PUBLIC KEY");
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Clave publica RSA invalida: " + resource.getDescription(), e);
        }
    }

    private static byte[] decodePem(Resource resource, String type) {
        String pem;
        try (InputStream in = resource.getInputStream()) {
            pem = new String(in.readAllBytes(), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer la clave: " + resource.getDescription(), e);
        }
        String begin = "-----BEGIN " + type + "-----";
        String end = "-----END " + type + "-----";
        int start = pem.indexOf(begin);
        int stop = pem.indexOf(end);
        if (start < 0 || stop < 0) {
            throw new IllegalStateException("Formato PEM esperado (" + type + "): " + resource.getDescription());
        }
        String body = pem.substring(start + begin.length(), stop).replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
