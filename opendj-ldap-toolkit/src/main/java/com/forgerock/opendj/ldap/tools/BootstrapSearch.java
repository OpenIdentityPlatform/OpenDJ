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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.Security;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Internal worker for Docker bootstrap reads. Each request runs a fresh LDAPSearch, with
 * a fresh connection and argument parser, while sharing the JVM startup and TLS providers.
 * Requests are base64 encoded NUL-separated arguments; responses are a status and base64
 * encoded stdout on one line. The private pipes are owned by the joining container user.
 */
public final class BootstrapSearch {
    private BootstrapSearch() {
        // Utility class.
    }

    /**
     * Runs searches until the parent closes its request pipe.
     * @param args unused
     * @throws IOException if a pipe cannot be read or written
     */
    public static void main(String[] args) throws IOException {
        configureDnsCaching();
        PrintStream protocol = System.out;
        // Libraries must not interleave their diagnostics with protocol frames.
        System.setOut(System.err);
        serve(System.in, protocol, "1".equals(System.getenv("JOIN_TIMING")));
    }

    static void configureDnsCaching() {
        // A recreated container keeps its hostname, not its address. Match the fresh
        // DNS view of separate ldapsearch JVMs, including names which were absent.
        Security.setProperty("networkaddress.cache.ttl", "0");
        Security.setProperty("networkaddress.cache.negative.ttl", "0");
        Security.setProperty("networkaddress.cache.stale.ttl", "0");
    }

    static String[] decode(String request) {
        String decoded = new String(Base64.getDecoder().decode(request), UTF_8);
        if (!decoded.endsWith("\0")) {
            throw new IllegalArgumentException("Unterminated bootstrap search request");
        }
        return decoded.substring(0, decoded.length() - 1).split("\0", -1);
    }

    static void serve(InputStream input, PrintStream output, boolean timing) throws IOException {
        BufferedReader requests = new BufferedReader(new InputStreamReader(input, UTF_8));
        String request;
        while ((request = requests.readLine()) != null) {
            String[] args = decode(request);
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            long start = System.nanoTime();
            int status;
            try (PrintStream stdout = new PrintStream(result, false, "UTF-8");
                 PrintStream stderr = new PrintStream(OutputStream.nullOutputStream())) {
                status = LDAPSearch.run(stdout, stderr, args);
            }
            if (timing) {
                System.err.println("join-search event=result milliseconds="
                        + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + " status=" + status);
            }
            output.println(status + " " + Base64.getEncoder().encodeToString(result.toByteArray()));
            output.flush();
            if (output.checkError()) {
                throw new IOException("Bootstrap search response pipe closed");
            }
        }
    }
}
