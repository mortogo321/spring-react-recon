package io.github.mortogo321.recon.api.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * The signing key pair for demo tokens.
 *
 * <p>Generated in memory at startup, on purpose: it means the repository contains no private key,
 * and it means tokens do not survive a restart — both correct for a POC. A real deployment does not
 * issue its own tokens at all; it points the resource server at the organisation's JWKS endpoint,
 * which is a one-line configuration change and no code change.
 */
public record JwtKeys(RSAPublicKey publicKey, RSAPrivateKey privateKey) {

    public static JwtKeys generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new JwtKeys((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA is not available in this JVM", e);
        }
    }
}
