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
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.opendj.rest2ldap.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.opendj.rest2ldap.authz.Authorization.newFileAccessTokenResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.forgerock.http.oauth2.AccessTokenException;
import org.forgerock.http.oauth2.AccessTokenInfo;
import org.forgerock.http.oauth2.AccessTokenResolver;
import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * The file resolver treats the bearer token as the name of a file inside the configured token directory. The
 * token comes verbatim from the {@code Authorization} header of an unauthenticated request, so a token which is
 * not a plain file name must never select a file elsewhere on the file system (GHSA-c5gp-3768-jc59).
 */
@Test
@SuppressWarnings("javadoc")
public class FileAccessTokenResolverTest extends ForgeRockTestCase {

    private static final String VALID_TOKEN_JSON =
            "{ \"scope\": [ \"read\" ], \"expireTime\": " + (System.currentTimeMillis() + 3_600_000L) + " }";

    private Path root;
    private Path tokenDir;
    private Path outsideToken;
    private AccessTokenResolver resolver;

    @BeforeMethod
    public void setUp() throws Exception {
        root = Files.createTempDirectory("file-access-token-resolver");
        tokenDir = Files.createDirectory(root.resolve("tokens"));
        writeToken(tokenDir.resolve("valid-token"));
        // A valid, unexpired token file one level above the token directory.
        outsideToken = writeToken(root.resolve("outside-token"));
        resolver = newFileAccessTokenResolver(tokenDir.toString());
    }

    @AfterMethod
    public void tearDown() throws Exception {
        Files.walk(root).sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }

    @Test
    public void resolvesTokenFileByName() throws Exception {
        final AccessTokenInfo token = resolver.resolve(null, "valid-token").getOrThrow();
        assertThat(token.getToken()).isEqualTo("valid-token");
        assertThat(token.getScopes()).containsExactly("read");
    }

    @Test
    public void resolvesTokenNameContainingDots() throws Exception {
        writeToken(tokenDir.resolve("header.payload..signature"));
        final AccessTokenInfo token = resolver.resolve(null, "header.payload..signature").getOrThrow();
        assertThat(token.getScopes()).containsExactly("read");
    }

    @Test(expectedExceptions = AccessTokenException.class)
    public void rejectsTokenEscapingTheDirectoryWithParentReferences() throws Exception {
        assertThat(outsideToken).exists();
        resolver.resolve(null, "../outside-token").getOrThrow();
    }

    @Test(expectedExceptions = AccessTokenException.class)
    public void rejectsTokenEscapingTheDirectoryThroughAChildFirst() throws Exception {
        resolver.resolve(null, "valid-token/../../outside-token").getOrThrow();
    }

    @Test(expectedExceptions = AccessTokenException.class)
    public void rejectsAbsoluteTokenPath() throws Exception {
        resolver.resolve(null, outsideToken.toAbsolutePath().toString()).getOrThrow();
    }

    @Test(expectedExceptions = AccessTokenException.class)
    public void rejectsTokenNamingAFileInASubdirectory() throws Exception {
        writeToken(Files.createDirectory(tokenDir.resolve("sub")).resolve("valid-token"));
        resolver.resolve(null, "sub/valid-token").getOrThrow();
    }

    @Test(expectedExceptions = AccessTokenException.class)
    public void rejectsTokenWithBackslashSeparator() throws Exception {
        // Fails closed on every platform, whether or not the platform treats the backslash as a separator.
        resolver.resolve(null, "..\\outside-token").getOrThrow();
    }

    @Test(expectedExceptions = AccessTokenException.class)
    public void rejectsTokenNamingTheDirectoryItself() throws Exception {
        resolver.resolve(null, ".").getOrThrow();
    }

    @Test(expectedExceptions = AccessTokenException.class)
    public void rejectsEmptyToken() throws Exception {
        resolver.resolve(null, "").getOrThrow();
    }

    @Test(expectedExceptions = AccessTokenException.class)
    public void rejectsUnknownTokenFile() throws Exception {
        resolver.resolve(null, "unknown-token").getOrThrow();
    }

    private static Path writeToken(final Path file) throws IOException {
        return Files.write(file, VALID_TOKEN_JSON.getBytes(StandardCharsets.UTF_8));
    }
}
