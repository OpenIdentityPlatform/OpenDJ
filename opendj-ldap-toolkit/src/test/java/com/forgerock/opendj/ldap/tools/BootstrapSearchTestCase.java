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
package com.forgerock.opendj.ldap.tools;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.Test;

/** Checks framing and recovery between searches in the Docker bootstrap worker. */
@SuppressWarnings("javadoc")
@Test
public class BootstrapSearchTestCase extends ForgeRockTestCase {
    @Test
    public void failedSearchDoesNotPoisonTheNextRequest() throws Exception {
        String input = request("--invalid-option") + request("--help");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BootstrapSearch.serve(new ByteArrayInputStream(input.getBytes(UTF_8)),
                new PrintStream(output), false);
        String[] lines = output.toString("UTF-8").lines().toArray(String[]::new);
        assertEquals(lines.length, 2);
        assertFalse(lines[0].startsWith("0 "));
        assertTrue(lines[1].startsWith("0 "));
        assertTrue(new String(Base64.getDecoder().decode(lines[1].substring(2)), UTF_8).contains("baseDN"));
    }

    @Test
    public void framingPreservesEmptyUnicodeAndMultilineArguments() {
        String[] args = {"", "cn=тест,dc=example", "(description=one\ntwo)", "a b\tc"};
        assertEquals(BootstrapSearch.decode(request(args).trim()), args);
    }

    @Test
    public void workerRefreshesPositiveAndNegativeDnsAnswers() throws Exception {
        Path hosts = Files.createTempFile("bootstrap-dns", ".hosts");
        Process probe = null;
        try {
            probe = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-Djdk.net.hosts.file=" + hosts, "-cp", System.getProperty("java.class.path"),
                    DnsProbe.class.getName(), hosts.toString()).redirectErrorStream(true).start();
            assertTrue(probe.waitFor(20, TimeUnit.SECONDS), "DNS probe did not finish");
            assertEquals(probe.exitValue(), 0, new String(probe.getInputStream().readAllBytes(), UTF_8));
        } finally {
            if (probe != null) {
                probe.destroyForcibly();
            }
            Files.deleteIfExists(hosts);
        }
    }

    /** Runs in a fresh JVM so the test neither inherits nor alters the suite's DNS policy. */
    public static final class DnsProbe {
        public static void main(String[] args) throws Exception {
            BootstrapSearch.configureDnsCaching();
            Path hosts = Path.of(args[0]);
            Files.writeString(hosts, "127.0.0.1 peer\n");
            requireAddress("peer", "127.0.0.1");
            Files.writeString(hosts, "127.0.0.2 peer\n");
            requireAddress("peer", "127.0.0.2");
            try {
                InetAddress.getByName("new-peer");
                throw new AssertionError("missing peer resolved");
            } catch (UnknownHostException expected) {
                // The next request must not retain this negative result.
            }
            Files.writeString(hosts, "127.0.0.3 new-peer\n");
            requireAddress("new-peer", "127.0.0.3");
        }

        private static void requireAddress(String host, String expected) throws Exception {
            String actual = InetAddress.getByName(host).getHostAddress();
            if (!expected.equals(actual)) {
                throw new AssertionError(host + ": expected " + expected + ", got " + actual);
            }
        }
    }

    private static String request(String... args) {
        return Base64.getEncoder().encodeToString((String.join("\0", args) + "\0").getBytes(UTF_8)) + "\n";
    }
}
