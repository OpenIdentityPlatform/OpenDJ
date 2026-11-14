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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.util.Options;
import org.opends.server.types.HostPort;

/** An immutable snapshot of the replicated naming contexts and their LDAP servers. */
final class ReplicationTopology {
    static final class Replica {
        final DN base;
        final HostPort address;
        final int group;
        final Options options;

        Replica(DN base, HostPort address, int group) {
            this(base, address, group, Options.defaultOptions());
        }

        Replica(DN base, HostPort address, int group, Options options) {
            this.base = base;
            this.address = address;
            this.group = group;
            this.options = options;
        }
    }

    private final Map<DN, Partition> partitions;
    private final Partition empty;
    private final String signature;

    private ReplicationTopology(String name, Map<DN, Partition> partitions, String signature) {
        this.partitions = Collections.unmodifiableMap(partitions);
        this.empty = new Partition(name, Collections.emptyList(), Collections.emptyList());
        this.signature = signature;
    }

    static ReplicationTopology create(String name, Integer primaryGroup, Collection<Replica> replicas) {
        final Map<DN, List<Partition.Server>> primary = new LinkedHashMap<>();
        final Map<DN, List<Partition.Server>> secondary = new LinkedHashMap<>();
        for (Replica replica : replicas) {
            final boolean preferred = primaryGroup == null || primaryGroup == replica.group;
            final Map<DN, List<Partition.Server>> target = preferred ? primary : secondary;
            target.computeIfAbsent(replica.base, unused -> new ArrayList<>())
                    .add(new Partition.Server(replica.address, replica.options));
        }
        final Map<DN, Partition> result = new LinkedHashMap<>();
        for (DN base : primary.keySet()) {
            result.put(base, new Partition(name, primary.get(base),
                    secondary.getOrDefault(base, Collections.emptyList())));
        }
        for (DN base : secondary.keySet()) {
            result.putIfAbsent(base, new Partition(name, Collections.emptyList(), secondary.get(base)));
        }
        final List<String> identities = new ArrayList<>();
        for (Replica replica : replicas) {
            identities.add(replica.base.toString() + "|" + replica.address + "|" + replica.group);
        }
        Collections.sort(identities);
        return new ReplicationTopology(name, result, primaryGroup + ":" + identities);
    }

    static Set<HostPort> connectedServers(Set<HostPort> roots, Map<HostPort, Set<HostPort>> graph) {
        final Set<HostPort> reachable = new HashSet<>(roots);
        boolean changed;
        do {
            changed = false;
            for (Map.Entry<HostPort, Set<HostPort>> entry : graph.entrySet()) {
                if (reachable.contains(entry.getKey()) || !Collections.disjoint(reachable, entry.getValue())) {
                    changed |= reachable.add(entry.getKey());
                    changed |= reachable.addAll(entry.getValue());
                }
            }
        } while (changed);
        return reachable;
    }

    Partition partitionFor(DN target) {
        DN best = null;
        for (DN base : partitions.keySet()) {
            if (target != null && target.isSubordinateOrEqualTo(base) && (best == null || base.size() > best.size())) {
                best = base;
            }
        }
        return best == null ? empty : partitions.get(best);
    }

    boolean hasSameServers(ReplicationTopology other) {
        return signature.equals(other.signature);
    }

}
