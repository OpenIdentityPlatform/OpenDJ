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
package org.opends.server.extensions;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.List;

import org.testng.SkipException;
import org.testng.annotations.Test;

/** Tests the stamps a file based key or trust manager provider tells a changed file by. */
@Test(sequential = true)
public class FileStampTestCase extends ExtensionsTestCase
{
  /**
   * A file renamed over another one, the way a renewal agent or a Kubernetes Secret volume
   * replaces it, changes the stamp even where its size and modification time are the same.
   */
  @Test
  public void testRenameWithSameSizeAndTimeChangesStamp() throws Exception
  {
    final Path dir = Files.createTempDirectory("stamp");
    try
    {
      final Path file = dir.resolve("stamped.bin");
      Files.write(file, new byte[] { 1 });
      if (Files.readAttributes(file, BasicFileAttributes.class).fileKey() == null)
      {
        throw new SkipException("no file key on this file system");
      }
      final FileTime time = Files.getLastModifiedTime(file);
      final List<FileStamp> before = FileStamp.of(file.toFile());

      final Path tmp = dir.resolve("stamped.tmp");
      Files.write(tmp, new byte[] { 2 });
      Files.setLastModifiedTime(tmp, time);
      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

      assertThat(Files.size(file)).isEqualTo(1);
      assertThat(Files.getLastModifiedTime(file)).isEqualTo(time);
      assertThat(FileStamp.of(file.toFile())).isNotEqualTo(before);
    }
    finally
    {
      Files.deleteIfExists(dir.resolve("stamped.bin"));
      Files.deleteIfExists(dir.resolve("stamped.tmp"));
      Files.delete(dir);
    }
  }
}
