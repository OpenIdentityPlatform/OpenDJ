/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import org.forgerock.util.Reject;
import org.opends.server.api.CompressedSchema;
import org.opends.server.types.EntryEncodeConfig;

/**
 * Configuration class to indicate desired compression and cryptographic options
 * for the data stored in the tree.
 */
final class DataConfig
{
  /** Indicates whether data should be compressed before writing to the storage. */
  private final boolean compressed;

  /** The configuration to use when encoding entries in the tree. */
  private final EntryEncodeConfig encodeConfig;

  /**
   * Construct a new DataConfig object with the specified settings.
   *
   * @param compressed true if data should be compressed, false if not.
   * @param compactEncoding true if data should be encoded in compact form,
   * false if not.
   * @param compressedSchema the compressed schema manager to use.  It must not
   * be {@code null} if compactEncoding is {@code true}.
   */
  DataConfig(boolean compressed, boolean compactEncoding, CompressedSchema compressedSchema)
  {
    this.compressed = compressed;

    if (compressedSchema == null)
    {
      Reject.ifTrue(compactEncoding);
      this.encodeConfig = new EntryEncodeConfig(false, compactEncoding, false);
    }
    else
    {
      this.encodeConfig =
          new EntryEncodeConfig(false, compactEncoding, compactEncoding, compressedSchema);
    }
  }

  /**
   * Determine whether data should be compressed before writing to the tree.
   * @return true if data should be compressed, false if not.
   */
  boolean isCompressed()
  {
    return compressed;
  }

  /**
   * Get the EntryEncodeConfig object in use by this configuration.
   * @return the EntryEncodeConfig object in use by this configuration.
   */
  EntryEncodeConfig getEntryEncodeConfig()
  {
    return encodeConfig;
  }

  /**
   * Get a string representation of this object.
   * @return A string representation of this object.
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("DataConfig(compressed=");
    builder.append(compressed);
    builder.append(", ");
    encodeConfig.toString(builder);
    builder.append(")");
    return builder.toString();
  }
}
