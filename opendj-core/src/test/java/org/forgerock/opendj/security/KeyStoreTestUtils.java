/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.security;

import static org.forgerock.opendj.ldap.ByteString.valueOfBase64;
import static org.forgerock.opendj.ldap.Connections.newInternalConnectionFactory;
import static org.forgerock.opendj.ldap.Functions.byteStringToCertificate;
import static org.forgerock.opendj.security.KeyStoreParameters.GLOBAL_PASSWORD;
import static org.forgerock.opendj.security.OpenDJProvider.newLDAPKeyStore;
import static org.forgerock.opendj.security.OpenDJProvider.newClearTextPasswordFactory;
import static org.forgerock.opendj.security.OpenDJProviderSchema.SCHEMA;
import static org.forgerock.util.Options.defaultOptions;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.MemoryBackend;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.util.Options;

@SuppressWarnings("javadoc")
final class KeyStoreTestUtils {
    static final DN KEYSTORE_DN = DN.valueOf("ou=key store,dc=example,dc=com");
    static final String TEST_ALIAS = "test";
    static final DN TEST_DN = KEYSTORE_DN.child("ds-keystore-alias", TEST_ALIAS);
    private static final String TRUSTED_CERTIFICATE_B64 =
            "MIIENjCCAx6gAwIBAgIDCYLsMA0GCSqGSIb3DQEBCwUAMEcxCzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1HZW9UcnVzdCBJbmMuMSAwHgYDVQ"
                    + "QDExdSYXBpZFNTTCBTSEEyNTYgQ0EgLSBHMzAeFw0xNjAxMTExNjU3NDFaFw0xNzAxMTIyMzU2MTlaMBoxGDAWBgNVBAMMDy"
                    + "ouZm9yZ2Vyb2NrLm9yZzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAL4349tGBV/t73Dnggfu++adiLCvRd8tm0"
                    + "mWP0l2G6x3lw/oKfwq9qOp57XLmkpVPLhzbaNWL80G9pIPH+db/I8o2+1kwFl/DIcLE/IqVNgCc9ZHEG9Hi0FFPYW18Zi5Sz"
                    + "UaimmTxNGYmKJ/rmUgbX5g34YZ3Pcc8zS+YOCeWFvDa+YKXXHdX1LzDfSzWii6ZYD1xHY4/DFwcg6x9FkNs653U0NJEf/xyb"
                    + "/fvsMbqSwosgLhJ9XBCCxgtOHSjJRKbDajypoFYJfFEuywLiSgx2pfqDl47J6lUKm905nDVQss5uzDgkUAd3VGwc1Ee1+617"
                    + "R6qJ5QYTTKX9YhzTxnv70CAwEAAaOCAVYwggFSMB8GA1UdIwQYMBaAFMOc8/zTRgg0u85Gf6B8W/PiCMtZMFcGCCsGAQUFBw"
                    + "EBBEswSTAfBggrBgEFBQcwAYYTaHR0cDovL2d2LnN5bWNkLmNvbTAmBggrBgEFBQcwAoYaaHR0cDovL2d2LnN5bWNiLmNvbS"
                    + "9ndi5jcnQwDgYDVR0PAQH/BAQDAgWgMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjApBgNVHREEIjAggg8qLmZvcm"
                    + "dlcm9jay5vcmeCDWZvcmdlcm9jay5vcmcwKwYDVR0fBCQwIjAgoB6gHIYaaHR0cDovL2d2LnN5bWNiLmNvbS9ndi5jcmwwDA"
                    + "YDVR0TAQH/BAIwADBBBgNVHSAEOjA4MDYGBmeBDAECATAsMCoGCCsGAQUFBwIBFh5odHRwczovL3d3dy5yYXBpZHNzbC5jb2"
                    + "0vbGVnYWwwDQYJKoZIhvcNAQELBQADggEBAH+gL/akHpj8uRC8KyyNY2NX34OiAskNPr2Z2UhTkYXCWm5B2V0bQaZwF/AbrV"
                    + "Z/EwCSnQYoDg5WrGS6SWhvRAVjJ33EG7jUE4C7q9nyYH8NKzvfdz7w50heRCB5lPpD0gg01VzLSJ7cAY1eP9fhTjFxckDjVp"
                    + "8M/t6cmp3kWpRgamww2SVizoKZtRALdR9Re7acR2EHnzBT1l1R7oNNcyW7jqPzneDZEr/ZWQhVWOAljpgxnGFDO+HAxtiltU"
                    + "E2j4IOwsU7zHsPlZgfYOfyCp/+1QVuIiXLSD9+YWH92wSKi/7z/d4hD8jG8lCUkpmQXkbEw6jMwsRN4bpmyM2c4Gc=";
    private static final String PUBLIC_KEY_CERTIFICATE_B64 =
            "MIIDQDCCAiigAwIBAgIEelaEuDANBgkqhkiG9w0BAQsFADBBMQswCQYDVQQGEwJGUjEVMBMGA1UEChMMRXhhbXBsZSBDb3Jw"
                    + "MRswGQYDVQQDExJvcGVuZGouZXhhbXBsZS5jb20wHhcNMTYwOTA1MTU1MDM3WhcNMTYxMjA0MTU1MDM3WjBBMQswCQYDVQQG"
                    + "EwJGUjEVMBMGA1UEChMMRXhhbXBsZSBDb3JwMRswGQYDVQQDExJvcGVuZGouZXhhbXBsZS5jb20wggEiMA0GCSqGSIb3DQEB"
                    + "AQUAA4IBDwAwggEKAoIBAQDh4tTZu1vNvAgDEXpGEvzkl3r4ayGfX7jSqWpjDtSyfbfIW71MiIQ90O64g5hzArHWhOWrgbCA"
                    + "BXHIk9Ad7wn87bWLIoagHQCUQ89QrDKMntvAea66B4RLKJRilNIm07b+mGjEx3FJb2NfoCA2UmLVBKEvYpNHrxv5c//tet+M"
                    + "Vbs7AL74t5ALCTeK99h2m2dmYvraAc7zbneKBdBK+7eIhdjZzrT1ElN8HfCQ4PzZD0cglue8M6V0R993BC8L0h00IHaHKzTM"
                    + "IKEMWUI9ailHON4fYI61BuNcRYyyKUQ1pojadEQ5bqEJ1zf51D6D6dusVKA52EAC+KPa0oHvdxlnAgMBAAGjQDA+MB0GA1Ud"
                    + "EQQWMBSCEm9wZW5kai5leGFtcGxlLmNvbTAdBgNVHQ4EFgQUKtFrvmqMm5M6esHCMR/bI7l0jqMwDQYJKoZIhvcNAQELBQAD"
                    + "ggEBAIw51ZDT3g1V51wgDIKGrtUC1yPxLmBqXg5lUWI8RJurwMnokGWGvDMemLw2gAIgQxrRKsOcPaIxYbjLY+Y5+I2Zof19"
                    + "eXJTRqqo/m4qNRpbzdzEdGv7lcH4zzL1YbLCLgoWyLgC2GFkJRas3pkdEplA6nf/fc5k4DJrMnV/oYjdvs0PH2gsW9drPkck"
                    + "lLTToWEijzyFEnry/O5EpsCuJdjN422rxVRJd1Qr/mLX5yt2kW83oMT3PRNREB+ZcHfVT0NvZ8KqYmMEpuODPx+XUuojuQsN"
                    + "bjlWXd4PSX8jqqvT3uLNWotKLQbDZV6Lp4f1Uf1qXukD2j9o8XKF+6ww03M=";
    static final Certificate TRUSTED_CERTIFICATE =
            byteStringToCertificate().apply(valueOfBase64(TRUSTED_CERTIFICATE_B64));
    static final Certificate PUBLIC_KEY_CERTIFICATE =
            byteStringToCertificate().apply(valueOfBase64(PUBLIC_KEY_CERTIFICATE_B64));
    static final Certificate[] CERTIFICATE_CHAIN = new Certificate[] { PUBLIC_KEY_CERTIFICATE };
    static final String PRIVATE_KEY_ENCODED_B64 =
            "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDh4tTZu1vNvAgDEXpGEvzkl3r4ayGfX7jSqWpjDtSyfbfI"
                    + "W71MiIQ90O64g5hzArHWhOWrgbCABXHIk9Ad7wn87bWLIoagHQCUQ89QrDKMntvAea66B4RLKJRilNIm07b+mGjEx3FJb2Nf"
                    + "oCA2UmLVBKEvYpNHrxv5c//tet+MVbs7AL74t5ALCTeK99h2m2dmYvraAc7zbneKBdBK+7eIhdjZzrT1ElN8HfCQ4PzZD0cg"
                    + "lue8M6V0R993BC8L0h00IHaHKzTMIKEMWUI9ailHON4fYI61BuNcRYyyKUQ1pojadEQ5bqEJ1zf51D6D6dusVKA52EAC+KPa"
                    + "0oHvdxlnAgMBAAECggEBALEYOpJdvtLkiU+Gg1uvBVBeps1eiKS/0lJu+nahKQarY8wUiKwZF7yzMoW8vmflA/JQjRPSgMNO"
                    + "AXAk2vSs9SK0ZzGnJu8e7dZP95ii+Jqg7V7Qx7kXrZOTRAqp7Lz+HakranBkgR/20W0mSDruiofBsnFJEnkQA5mmZU8Vl3AY"
                    + "SYwKP785N/nO7vZMMOkSrs5BYwDeYHIwncxXlaUBBCHf1I0tAHMe3SlqpPHpjrFiv2IofjGTGWEe6KDiUMWGNOkTLHgxb9rZ"
                    + "TRh2p3vuMfOfkzd0Tgj6DOUVb1SOI8g4nFwfphwU008eV86OV7nxIbc+DXfQYpZ/850Rra/lCmECgYEA+AiIsw8QkkJuws+G"
                    + "oYWuVMH+m/qbWami+g1CDaR8zPTSAMdqNLamrnXkN3HtiMn+qjxBh5f7zw4eu5TA/jyFBTcOvh1gsYJNTGKPrzxyHF3k3BV/"
                    + "SGplIdzos+f7JSLNLZmSFSpikoAhy46sZKw30XjG74BHmeQmcAmLsx6a8lECgYEA6SQwv+i196e3DlfPBuUHuB9Z91FFUZi7"
                    + "A3Q3ziB56xXxOZO+F1L565Ve0z2j1jeVnmrBANkXnJUVWK2g+dCi8/gvocQ3hGr/WbJVXD6T57GkcwtApR1qkQxRVycxqYR0"
                    + "mYjb6jGOMJZ8OeC0UWYDWBlgjmv65C01IdItTJz/6jcCgYEAkk5mZEjkm4G4WA2V+r0iIjj0eQmQjYk0647agbWfMD7RiUgX"
                    + "69Q56fr8jYAUf3W3VK+Kb/NEw9QuaLPMS6tjQ7pAZgBqQwr7ka0p2FItdXIlR3UeyZaI5TqrwUN7r2Ih6V4G/5kq4APY63vT"
                    + "UOcNXfCCWFAw7CPaUIgw8Y2CFKECgYAEEOyEvFNIIXWw21kx/paW4H0aMiGqXaaNVd6PSsO1lOljHq+HCpxvPmir+Hw+BTQn"
                    + "0ibRk/e0dGkt5cFT+g6NgLub76ckORWBA/o3JKRBuzhqBT04Y/3yz6svgPB9y2CZOOjU+c5IDKfX/pJGhSfzxmWHtlxm1F8D"
                    + "2v2NQ4O3GwKBgASHtjgsEed1s+K6EU2S2dRqzrAyrokUZp5dKoYPPE2l4AIb0IGODD3GO3uZ6xAYg7RZkemtm2tLH4sTF6QU"
                    + "tRGputLLoAPp3qTWVCKW668hdNApC/WtqRwY68KOQlhoMgWQWLgX3Lu1gGqJHu89B3UmswvgNCZ7hA49P33jR2wh";
    static final PrivateKey PRIVATE_KEY;

    static {
        final KeySpec spec = new PKCS8EncodedKeySpec(valueOfBase64(PRIVATE_KEY_ENCODED_B64).toByteArray());
        try {
            PRIVATE_KEY = KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static final char[] KEYSTORE_PASSWORD = "changeit".toCharArray();
    static final char[] KEY_PASSWORD = "changeit".toCharArray();

    static MemoryBackend createKeyStoreMemoryBackend() {
        try (LDIFEntryReader reader = new LDIFEntryReader("dn: " + KEYSTORE_DN,
                                                          "objectClass: top",
                                                          "objectClass: organizationalUnit",
                                                          "ou: key store").setSchema(SCHEMA)) {
            return new MemoryBackend(SCHEMA, reader).enableVirtualAttributes(true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static KeyStore createKeyStore(final MemoryBackend backend) {
        final ConnectionFactory factory = newInternalConnectionFactory(backend);
        final Options options = defaultOptions().set(GLOBAL_PASSWORD, newClearTextPasswordFactory(KEYSTORE_PASSWORD));
        return newLDAPKeyStore(factory, KEYSTORE_DN, options);
    }

    static SecretKey createSecretKey() throws Exception {
        final SecureRandom secureRandom = new SecureRandom();
        final byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        final SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        return factory.generateSecret(new PBEKeySpec("password".toCharArray(), salt, 65536, 128));
    }

    private KeyStoreTestUtils() {
        // Prevent instantiation.
    }
}
