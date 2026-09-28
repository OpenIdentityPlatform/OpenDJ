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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What a file looked like when it was read: enough to tell, without reading it again, that it
 * has been rewritten or replaced since. A file replaced by a rename, the way certificate
 * renewal agents and Kubernetes update a mounted Secret, is a different file even when its
 * size and modification time happen to match, which is why the file key is kept as well.
 * Symbolic links are followed, so the stamp of a link describes the file it points to.
 */
final class FileStamp
{
  private final long lastModified;
  private final long size;
  private final Object fileKey;

  private FileStamp(long lastModified, long size, Object fileKey)
  {
    this.lastModified = lastModified;
    this.size = size;
    this.fileKey = fileKey;
  }

  /**
   * Returns the stamps of the provided files, in the same order. Every file that is missing or
   * cannot be looked at gets the same stamp.
   *
   * @param files
   *          The files to stamp; {@code null} elements are skipped.
   * @return The stamps of the files.
   */
  static List<FileStamp> of(File... files)
  {
    final List<FileStamp> stamps = new ArrayList<>(files.length);
    for (File file : files)
    {
      if (file != null)
      {
        stamps.add(of(file));
      }
    }
    return stamps;
  }

  private static FileStamp of(File file)
  {
    try
    {
      final BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
      return new FileStamp(attributes.lastModifiedTime().toMillis(), attributes.size(), attributes.fileKey());
    }
    catch (IOException | SecurityException e)
    {
      return new FileStamp(-1, -1, null);
    }
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }
    if (!(o instanceof FileStamp))
    {
      return false;
    }
    final FileStamp other = (FileStamp) o;
    return lastModified == other.lastModified && size == other.size && Objects.equals(fileKey, other.fileKey);
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(lastModified, size, fileKey);
  }
}
