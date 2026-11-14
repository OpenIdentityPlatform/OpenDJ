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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.CRC32;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.service.ReplicationDomain;

/** Opt-in, test-only evidence of every locally allocated CSN, including failed operations. */
final class CsnAllocationTrace
{
  private static final String DIRECTORY = "org.opends.server.replication.csnTraceDirectory";
  private static final String TOKEN = "org.opends.server.replication.csnTraceToken";
  private static final String TOKEN_FILE = "org.opends.server.replication.csnTraceTokenFile";
  private static final CsnAllocationTrace ACTIVE = start();

  private final Path path;
  private final OutputStream output;
  private final CRC32 checksum = new CRC32();
  private long count;
  private boolean finished;
  private boolean failed;

  private static CsnAllocationTrace start()
  {
    String directory = System.getProperty(DIRECTORY);
    if (directory == null)
    {
      return null;
    }
    try
    {
      String token = resolveToken(System.getProperty(TOKEN, ""), System.getProperty(TOKEN_FILE));
      CsnAllocationTrace trace = new CsnAllocationTrace(Paths.get(directory), token);
      // DirectoryServer runs shutdown hooks concurrently. Its listener runs after the work
      // queue, replication and shutdown plugins have stopped, including offline messages.
      // A crash, or a shutdown that never reaches this point, leaves no valid footer.
      DirectoryServer.registerShutdownListener(new ServerShutdownListener()
      {
        @Override
        public String getShutdownListenerName()
        {
          return "CSN allocation trace";
        }

        @Override
        public void processServerShutdown(LocalizableMessage reason)
        {
          trace.finish();
        }
      });
      return trace;
    }
    catch (IOException e)
    {
      throw new IllegalStateException("Cannot start the requested CSN allocation trace", e);
    }
  }

  static String resolveToken(String defaultToken, String tokenFile) throws IOException
  {
    if (tokenFile == null || Files.notExists(Paths.get(tokenFile)))
    {
      return defaultToken;
    }
    return Files.readString(Paths.get(tokenFile), StandardCharsets.US_ASCII).trim();
  }

  CsnAllocationTrace(Path directory, String token) throws IOException
  {
    if (!token.matches("[a-zA-Z0-9_-]+"))
    {
      throw new IllegalArgumentException("A CSN trace requires a generation token");
    }
    Files.createDirectories(directory);
    path = Files.createTempFile(directory, "trace-", ".txt");
    output = new BufferedOutputStream(Files.newOutputStream(path), 65536);
    output.write(("START 1 " + token + "\n").getBytes(StandardCharsets.US_ASCII));
    output.flush();
  }

  static String domain(ReplicationDomain domain)
  {
    return ACTIVE != null ? encodeDomain(domain.getBaseDN().toString()) : null;
  }

  static String encodeDomain(String name)
  {
    return Base64.getEncoder().encodeToString(name.getBytes(StandardCharsets.UTF_8));
  }

  static CSN next(CSNGenerator generator, String domain, char kind)
  {
    return ACTIVE != null ? ACTIVE.allocate(generator, domain, kind) : generator.newCSN();
  }

  synchronized CSN allocate(CSNGenerator generator, String domain, char kind)
  {
    try
    {
      // Mark before allocating, so even a thread stopped immediately afterwards cannot
      // make a completed trace conceal an allocation made during the shutdown hooks.
      if (finished)
      {
        output.write("AFTER-END\n".getBytes(StandardCharsets.US_ASCII));
        output.flush();
        return generator.newCSN();
      }
      if (failed)
      {
        throw new IllegalStateException("The CSN allocation trace has failed");
      }
      CSN csn = generator.newCSN();
      byte[] line = (kind + " " + domain + " " + csn + "\n").getBytes(StandardCharsets.US_ASCII);
      output.write(line);
      checksum.update(line);
      count++;
      if (count % 4096 == 0)
      {
        output.flush();
      }
      return csn;
    }
    catch (IOException e)
    {
      failed = true;
      throw new IllegalStateException("Cannot write the CSN allocation trace", e);
    }
  }

  synchronized void finish()
  {
    if (finished)
    {
      return;
    }
    finished = true;
    if (failed)
    {
      return;
    }
    try
    {
      output.write(String.format(Locale.ROOT, "END %d %08x\n", count, checksum.getValue())
          .getBytes(StandardCharsets.US_ASCII));
      output.flush();
    }
    catch (IOException e)
    {
      // A missing or partial footer makes the trace invalid to its consumer.
      System.err.println("Cannot finish the CSN allocation trace: " + e);
    }
  }

  Path path()
  {
    return path;
  }
}
