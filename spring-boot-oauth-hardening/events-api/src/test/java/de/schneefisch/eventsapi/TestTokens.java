package de.schneefisch.eventsapi;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

/**
 * Signs test tokens with a local RSA key and serves the matching JWK Set over HTTP. The tests run
 * through the real JwtDecoder that Spring Boot builds from {@code jwk-set-uri}: no Keycloak needed.
 */
final class TestTokens {

    static final String ISSUER = "http://localhost:8080/realms/demo";

    private static final RSAKey KEY;

    private static final HttpServer JWKS_SERVER;

    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("test-key").keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256).generate();
            byte[] jwks = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            JWKS_SERVER = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            JWKS_SERVER.createContext("/certs", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                exchange.getResponseBody().write(jwks);
                exchange.close();
            });
            JWKS_SERVER.start();
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private TestTokens() {
    }

    static String jwkSetUri() {
        return "http://localhost:" + JWKS_SERVER.getAddress().getPort() + "/certs";
    }

    /** An access token like the one Keycloak issues to demo-cli for alice with scope "openid events:read". */
    static Builder accessToken() {
        long now = Instant.now().getEpochSecond();
        return new Builder()
                .claim("iss", ISSUER)
                .claim("sub", "a1b2c3d4-alice")
                .claim("typ", "Bearer")
                .claim("azp", "demo-cli")
                .claim("aud", List.of("events-api"))
                .claim("scope", "openid events:read email profile")
                .claim("preferred_username", "alice")
                .claim("iat", now)
                .claim("exp", now + 300);
    }

    /** An ID token from the same login: same issuer and key, but typ "ID" and aud = the client. */
    static Builder idToken() {
        return accessToken()
                .claim("typ", "ID")
                .claim("aud", "demo-cli")
                .without("scope");
    }

    static Map<String, Object> clientRoles(String clientId, String... roles) {
        return Map.of(clientId, Map.of("roles", List.of(roles)));
    }

    static final class Builder {

        private final Map<String, Object> claims = new LinkedHashMap<>();

        Builder claim(String name, Object value) {
            this.claims.put(name, value);
            return this;
        }

        Builder without(String name) {
            this.claims.remove(name);
            return this;
        }

        /** RS256 with the key from the JWK Set, header "typ": "JWT" like Keycloak. */
        String signed() {
            try {
                JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID())
                        .type(JOSEObjectType.JWT).build();
                SignedJWT jwt = new SignedJWT(header, claimsSet());
                jwt.sign(new RSASSASigner(KEY));
                return jwt.serialize();
            }
            catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        /** The classic attack: "alg": "none" and no signature at all. */
        String unsigned() {
            return new PlainJWT(claimsSet()).serialize();
        }

        /** Algorithm confusion: HS256, using the public RSA key (known to everyone) as the HMAC secret. */
        String signedWithPublicKeyAsHmacSecret() {
            try {
                JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(KEY.getKeyID())
                        .type(JOSEObjectType.JWT).build();
                SignedJWT jwt = new SignedJWT(header, claimsSet());
                jwt.sign(new MACSigner(KEY.toRSAPublicKey().getEncoded()));
                return jwt.serialize();
            }
            catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        private JWTClaimsSet claimsSet() {
            try {
                return JWTClaimsSet.parse(this.claims);
            }
            catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
