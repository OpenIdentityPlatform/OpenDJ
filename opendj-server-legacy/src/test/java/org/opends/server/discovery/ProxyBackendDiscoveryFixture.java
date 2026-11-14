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
import java.util.List;

import org.forgerock.util.Options;
import org.opends.server.types.HostPort;

/** Creates the ordered discovery fixture used by proxy backend tests. */
public final class ProxyBackendDiscoveryFixture {
    private ProxyBackendDiscoveryFixture() { }

    public static StaticServiceDiscoveryMechanism mechanism(List<Integer> primaryPorts, List<Integer> secondaryPorts) {
        final List<Partition.Server> primaries = new ArrayList<>();
        final List<Partition.Server> secondaries = new ArrayList<>();
        for (int port : primaryPorts) {
            primaries.add(new Partition.Server(HostPort.valueOf("127.0.0.1:" + port, 389), Options.defaultOptions()));
        }
        for (int port : secondaryPorts) {
            secondaries.add(new Partition.Server(HostPort.valueOf("127.0.0.1:" + port, 389), Options.defaultOptions()));
        }
        final StaticServiceDiscoveryMechanism mechanism = new StaticServiceDiscoveryMechanism();
        mechanism.partition = new Partition("priority-test", primaries, secondaries);
        return mechanism;
    }
}
