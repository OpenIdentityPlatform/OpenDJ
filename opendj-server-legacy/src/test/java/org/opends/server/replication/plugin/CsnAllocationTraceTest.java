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
package org.opends.server.replication.plugin;

import static org.testng.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "replication" }, sequential = true)
public class CsnAllocationTraceTest extends DirectoryServerTestCase
{
  @Test
  public void recordsBothKindsAndClosesWithAChecksum() throws Exception
  {
    Path dir = Files.createTempDirectory("csn-trace-test");
    CsnAllocationTrace trace = new CsnAllocationTrace(dir, "token0");
    CSNGenerator generator = new CSNGenerator(14800, 1);
    String domain = CsnAllocationTrace.encodeDomain("dc=example");
    CSN offline = trace.allocate(generator, domain, 'O');
    CSN local = trace.allocate(generator, domain, 'L');
    trace.finish();
    List<String> lines = Files.readAllLines(trace.path(), StandardCharsets.US_ASCII);
    assertEquals(lines.size(), 4);
    assertEquals(lines.get(0), "START 1 token0");
    assertEquals(lines.get(1), "O " + domain + " " + offline);
    assertEquals(lines.get(2), "L " + domain + " " + local);
    CRC32 crc = new CRC32();
    crc.update((lines.get(1) + "\n" + lines.get(2) + "\n").getBytes(StandardCharsets.US_ASCII));
    assertEquals(lines.get(3), String.format("END 2 %08x", crc.getValue()));
  }

  @Test
  public void anAllocationAfterShutdownInvalidatesTheTrace() throws Exception
  {
    CsnAllocationTrace trace = new CsnAllocationTrace(Files.createTempDirectory("csn-trace-test"), "token0");
    trace.finish();
    trace.allocate(new CSNGenerator(42, 1), CsnAllocationTrace.encodeDomain("dc=example"), 'L');
    String data = Files.readString(trace.path(), StandardCharsets.US_ASCII);
    assertTrue(data.endsWith("AFTER-END\n"));
  }

  @Test
  public void aRestartTokenOverridesOnlyItsOwnContainerToken() throws Exception
  {
    Path file = Files.createTempDirectory("csn-token-test").resolve("token");
    assertEquals(CsnAllocationTrace.resolveToken("first", file.toString()), "first");
    Files.writeString(file, "second\n", StandardCharsets.US_ASCII);
    assertEquals(CsnAllocationTrace.resolveToken("first", file.toString()), "second");
    assertEquals(CsnAllocationTrace.resolveToken("third", null), "third");
  }
}
