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
package org.opends.server.discovery;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Per-address selection priorities shared by static and replication discovery. */
public final class ServerPriorities {
    private final Map<String, Integer> priorities;

    private ServerPriorities(Map<String, Integer> priorities) {
        this.priorities = priorities;
    }

    /** Parses HOST:PORT=PRIORITY entries, rejecting invalid or duplicate addresses. */
    public static ServerPriorities parse(Collection<String> values) {
        final Map<String, Integer> priorities = new HashMap<>();
        if (values != null) {
            for (String value : values) {
                final int separator = value.lastIndexOf('=');
                if (separator <= 0 || separator == value.length() - 1) {
                    throw new IllegalArgumentException("Invalid server priority: " + value);
                }
                final String address = value.substring(0, separator);
                final String number = value.substring(separator + 1);
                if (!number.matches("[0-9]+")) {
                    throw new IllegalArgumentException("Invalid server priority: " + value);
                }
                final String addressKey = key(address);
                final int priority;
                try {
                    priority = Integer.parseInt(number);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid server priority: " + value, e);
                }
                if (priorities.putIfAbsent(addressKey, priority) != null) {
                    throw new IllegalArgumentException("Duplicate server priority for " + address);
                }
            }
        }
        return new ServerPriorities(priorities);
    }

    /** Rejects priorities for addresses outside a statically configured server group. */
    public void validateStaticServers(Collection<String> primary, Collection<String> secondary) {
        if (priorities.isEmpty()) {
            return;
        }
        final Set<String> addresses = new HashSet<>();
        for (String address : primary) {
            addresses.add(existingKey(address));
        }
        for (String address : secondary) {
            addresses.add(existingKey(address));
        }
        for (String address : priorities.keySet()) {
            if (!addresses.contains(address)) {
                throw new IllegalArgumentException("Server priority refers to an unconfigured server: " + address);
            }
        }
    }

    /** Orders a tier by priority and address, preserving discovery order when no priorities are configured. */
    public List<Partition.Server> order(Collection<Partition.Server> servers) {
        final List<Partition.Server> ordered = new ArrayList<>(servers);
        if (!priorities.isEmpty()) {
            ordered.sort(Comparator.comparingInt((Partition.Server s) ->
                    priorities.getOrDefault(existingKey(s.getHostPort().toString()), 0))
                    .thenComparing(s -> existingKey(s.getHostPort().toString())));
        }
        return ordered;
    }

    private static String key(String address) {
        if (!address.matches("([A-Za-z0-9._-]+|\\[[0-9A-Za-z:.%_-]+\\]):[0-9]+")) {
            throw new IllegalArgumentException("Invalid server address: " + address);
        }
        final int separator = address.lastIndexOf(':');
        final String host = address.substring(0, separator);
        final int port;
        try {
            port = Integer.parseInt(address.substring(separator + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid server port: " + address, e);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid server port: " + address);
        }
        if (host.startsWith("[")) {
            try {
                new URI("ldap://" + address).parseServerAuthority();
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Invalid server address: " + address, e);
            }
        } else if (host.matches("[0-9.]+") && host.contains(".")) {
            final String[] parts = host.split("\\.", -1);
            if (parts.length < 2 || parts.length > 4) {
                throw new IllegalArgumentException("Invalid IPv4 server address: " + address);
            }
            for (int i = 0; i < parts.length; i++) {
                final long max = i == parts.length - 1 ? (1L << (8 * (5 - parts.length))) - 1 : 255;
                try {
                    if (parts[i].isEmpty() || Long.parseLong(parts[i]) > max) {
                        throw new IllegalArgumentException("Invalid IPv4 server address: " + address);
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid IPv4 server address: " + address, e);
                }
            }
        } else {
            final String name = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
            if (name.length() > 253) {
                throw new IllegalArgumentException("Invalid server address: " + address);
            }
            for (String label : name.split("\\.", -1)) {
                if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) {
                    throw new IllegalArgumentException("Invalid server address: " + address);
                }
            }
        }
        return existingKey(address);
    }

    private static String existingKey(String address) {
        final int separator = address.lastIndexOf(':');
        final String host = address.substring(0, separator);
        final int zone = host.startsWith("[") ? host.indexOf('%') : -1;
        final String normalizedHost = zone < 0 ? host.toLowerCase(Locale.ROOT)
                : host.substring(0, zone).toLowerCase(Locale.ROOT) + host.substring(zone);
        try {
            return normalizedHost + ":" + Integer.parseInt(address.substring(separator + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid server port: " + address, e);
        }
    }
}
