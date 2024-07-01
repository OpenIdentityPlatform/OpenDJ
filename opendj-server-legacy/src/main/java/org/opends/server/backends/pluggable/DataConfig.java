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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import org.forgerock.util.Reject;
import org.opends.server.api.CompressedSchema;
import org.opends.server.crypto.CryptoSuite;
import org.opends.server.types.EntryEncodeConfig;

/**
 * Configuration class to indicate desired compression and cryptographic options
 * for the data stored in the tree.
 */
final class DataConfig
{
  /**
   * Builder for a DataConfig with all compression/encryption options.
   */
  static final class Builder
  {
    private boolean compressed;
    private boolean encrypted;
    private boolean compactEncoding;
    private CompressedSchema compressedSchema;
    private CryptoSuite cryptoSuite;

    Builder()
    {
      // Nothing to do.
    }

    public Builder encode(boolean enabled)
    {
      this.compactEncoding = enabled;
      return this;
    }

    public Builder compress(boolean enabled)
    {
      this.compressed = enabled;
      return this;
    }

    public Builder encrypt(boolean enabled)
    {
      this.encrypted = enabled;
      return this;
    }

    public Builder schema(CompressedSchema schema)
    {
      this.compressedSchema = schema;
      return this;
    }

    public Builder cryptoSuite(CryptoSuite cs)
    {
      this.cryptoSuite = cs;
      return this;
    }

    public DataConfig build()
    {
      return new DataConfig(this);
    }
  }
  /** Indicates whether data should be compressed before writing to the storage. */
  private final boolean compressed;

  /** The configuration to use when encoding entries in the tree. */
  private final EntryEncodeConfig encodeConfig;

  private final boolean encrypted;

  private final CryptoSuite cryptoSuite;
  /**
   * Construct a new DataConfig object with the specified settings.
   *
   * @param builder the builder with the configuration
   */
  private DataConfig(Builder builder)
  {
    this.compressed = builder.compressed;
    this.encrypted = builder.encrypted;
    this.cryptoSuite = builder.cryptoSuite;

    if (builder.compressedSchema == null)
    {
      Reject.ifTrue(builder.compactEncoding);
      this.encodeConfig = new EntryEncodeConfig(false, builder.compactEncoding, false);
    }
    else
    {
      this.encodeConfig = new EntryEncodeConfig(false, builder.compactEncoding, builder.compactEncoding,
          builder.compressedSchema);
    }
  }

  boolean isCompressed()
  {
    return compressed;
  }

  boolean isEncrypted()
  {
    return encrypted;
  }

  EntryEncodeConfig getEntryEncodeConfig()
  {
    return encodeConfig;
  }

  CryptoSuite getCryptoSuite()
  {
    return cryptoSuite;
  }

  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("DataConfig(compressed=");
    builder.append(compressed);
    builder.append(", encrypted=");
    builder.append(encrypted);
    builder.append(", ");
    if (encrypted)
    {
      builder.append(cryptoSuite.toString());
      builder.append(", ");
    }
    encodeConfig.toString(builder);
    builder.append(")");
    return builder.toString();
  }
}
