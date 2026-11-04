package ar.edu.um.catalogo.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Emite JWT de prueba con el mismo formato que turnos-service, para verificar
 * que catalogo valida firma, vigencia, emisor, audiencia y roles.
 */
public final class TestTokens {

    public static final String ISSUER = "turnos-service";
    public static final String AUDIENCE = "turnos-app";

    private TestTokens() {}

    /** Token valido de un usuario comun. */
    public static String user() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String keyFile = "test-keys/test-private.pem";
        private String issuer = ISSUER;
        private String audience = AUDIENCE;
        private String authorities = "ROLE_USER";
        private Instant issuedAt = Instant.now();
        private Duration validity = Duration.ofHours(1);

        public Builder signedWithOtherKey() { this.keyFile = "test-keys/other-private.pem"; return this; }
        public Builder issuer(String issuer) { this.issuer = issuer; return this; }
        public Builder audience(String audience) { this.audience = audience; return this; }
        public Builder authorities(String authorities) { this.authorities = authorities; return this; }
        public Builder expired() { this.issuedAt = Instant.now().minus(Duration.ofHours(2)); return this; }

        public String build() {
            RSAPrivateCrtKey privateKey = readPrivateKey(keyFile);
            RSAKey jwk = new RSAKey.Builder(publicFrom(privateKey)).privateKey(privateKey).keyID("test").build();
            NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
            JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject("usuario-test")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(validity))
                .claim("auth", authorities)
                .claim("uid", 1L)
                .build();
            return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
        }
    }

    private static RSAPrivateCrtKey readPrivateKey(String path) {
        try {
            String pem = new ClassPathResource(path).getContentAsString(StandardCharsets.US_ASCII);
            String body = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
            return (RSAPrivateCrtKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
        } catch (IOException | java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAPublicKey publicFrom(RSAPrivateCrtKey key) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(key.getModulus(), key.getPublicExponent()));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
