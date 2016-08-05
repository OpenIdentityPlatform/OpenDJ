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
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.util.CollectionUtils.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.server.EntityTagVirtualAttributeCfg;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.VirtualAttributeRule;
import org.opends.server.util.StaticUtils;

/**
 * This class implements a virtual attribute provider which ensures that all
 * entries contain an "entity tag" or "Etag" as defined in section 3.11 of RFC
 * 2616.
 * <p>
 * The entity tag may be used by clients, in conjunction with the assertion
 * control, for optimistic concurrency control, as a way to help prevent
 * simultaneous updates of an entry from conflicting with each other.
 */
public final class EntityTagVirtualAttributeProvider extends
    VirtualAttributeProvider<EntityTagVirtualAttributeCfg> implements
    ConfigurationChangeListener<EntityTagVirtualAttributeCfg>
{
  private static final Comparator<Attribute> ATTRIBUTE_COMPARATOR =
      new Comparator<Attribute>()
  {
    @Override
    public int compare(final Attribute a1, final Attribute a2)
    {
      return a1.getAttributeDescription().compareTo(a2.getAttributeDescription());
    }
  };

  /** Current configuration. */
  private volatile EntityTagVirtualAttributeCfg config;

  /** Default constructor invoked by reflection. */
  public EntityTagVirtualAttributeProvider()
  {
    // Initialization performed by initializeVirtualAttributeProvider.
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
      final EntityTagVirtualAttributeCfg configuration)
  {
    this.config = configuration;
    return new ConfigChangeResult();
  }

  @Override
  public ConditionResult approximatelyEqualTo(final Entry entry,
      final VirtualAttributeRule rule, final ByteString value)
  {
    // ETags cannot be used in approximate matching.
    return ConditionResult.UNDEFINED;
  }

  @Override
  public void finalizeVirtualAttributeProvider()
  {
    config.removeEntityTagChangeListener(this);
  }

  @Override
  public Attribute getValues(final Entry entry, final VirtualAttributeRule rule)
  {
    // Save reference to current configuration in case it changes.
    final EntityTagVirtualAttributeCfg cfg = config;

    // Determine which checksum algorithm to use.
    final Checksum checksummer;
    switch (cfg.getChecksumAlgorithm())
    {
    case CRC_32:
      checksummer = new CRC32();
      break;
    default: // ADLER_32
      checksummer = new Adler32();
      break;
    }

    final ByteString etag = checksumEntry(cfg, checksummer, entry);
    return Attributes.create(rule.getAttributeType(), etag);
  }

  @Override
  public ConditionResult greaterThanOrEqualTo(final Entry entry,
      final VirtualAttributeRule rule, final ByteString value)
  {
    // ETags cannot be used in ordering matching.
    return ConditionResult.UNDEFINED;
  }

  @Override
  public boolean hasValue(final Entry entry, final VirtualAttributeRule rule)
  {
    // ETag is always present.
    return true;
  }

  @Override
  public void initializeVirtualAttributeProvider(
      final EntityTagVirtualAttributeCfg configuration) throws ConfigException,
      InitializationException
  {
    this.config = configuration;
    configuration.addEntityTagChangeListener(this);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
      final EntityTagVirtualAttributeCfg configuration,
      final List<LocalizableMessage> unacceptableReasons)
  {
    // The new configuration should always be acceptable.
    return true;
  }

  @Override
  public boolean isMultiValued()
  {
    // ETag is always single-valued.
    return false;
  }

  @Override
  public boolean isSearchable(final VirtualAttributeRule rule,
                              final SearchOperation searchOperation,
                              final boolean isPreIndexed)
  {
    // ETags cannot be searched since there is no way to determine which entry
    // is associated with a particular ETag.
    return false;
  }

  @Override
  public ConditionResult lessThanOrEqualTo(final Entry entry,
      final VirtualAttributeRule rule, final ByteString value)
  {
    // ETags cannot be used in ordering matching.
    return ConditionResult.UNDEFINED;
  }

  @Override
  public ConditionResult matchesSubstring(final Entry entry,
      final VirtualAttributeRule rule, final ByteString subInitial,
      final List<ByteString> subAny, final ByteString subFinal)
  {
    // ETags cannot be used in substring matching.
    return ConditionResult.UNDEFINED;
  }

  @Override
  public void processSearch(final VirtualAttributeRule rule,
      final SearchOperation searchOperation)
  {
    final LocalizableMessage message = ERR_ETAG_VATTR_NOT_SEARCHABLE.get(rule
        .getAttributeType().getNameOrOID());
    searchOperation.appendErrorMessage(message);
    searchOperation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
  }

  private void checksumAttribute(final EntityTagVirtualAttributeCfg cfg,
      final Checksum checksummer, final Attribute attribute)
  {
    // Object class may be null.
    if (attribute == null)
    {
      return;
    }

    // Ignore other virtual attributes include this one.
    if (attribute.isVirtual())
    {
      return;
    }

    // Ignore excluded attributes.
    if (cfg.getExcludedAttribute().contains(attribute.getAttributeDescription().getAttributeType()))
    {
      return;
    }

    // Checksum the attribute description.
    final byte[] bytes = StaticUtils.getBytes(attribute.getAttributeDescription().toString());
    checksummer.update(bytes, 0, bytes.length);

    // Checksum the attribute values. The value order may vary between
    // replicas so we need to make sure that we always process them in the
    // same order. Note that we don't need to normalize the values since we want
    // to detect any kind of updates even if they are not semantically
    // significant. In any case, normalization can be expensive and should be
    // avoided if possible.
    final int size = attribute.size();
    switch (size)
    {
    case 0:
      // It's surprising to have an empty attribute, but if we do then there's
      // nothing to do.
      break;
    case 1:
      // Avoid sorting single valued attributes.
      checksumValue(checksummer, attribute.iterator().next());
      break;
    default:
      // Multi-valued attributes need sorting.
      final ByteString[] values = new ByteString[size];
      int i = 0;
      for (final ByteString av : attribute)
      {
        values[i++] = av;
      }
      Arrays.sort(values);
      for (final ByteString value : values)
      {
        checksumValue(checksummer, value);
      }
      break;
    }
  }

  private ByteString checksumEntry(final EntityTagVirtualAttributeCfg cfg,
      final Checksum checksummer, final Entry entry)
  {
    // Checksum the object classes since these are not included in the entry's
    // attributes.
    checksumAttribute(cfg, checksummer, entry.getObjectClassAttribute());

    // The attribute order may vary between replicas so we need to make sure
    // that we always process them in the same order.
    final List<Attribute> attributes = collect(entry.getAllAttributes(), new ArrayList<Attribute>());
    Collections.sort(attributes, ATTRIBUTE_COMPARATOR);
    for (final Attribute attribute : attributes)
    {
      checksumAttribute(cfg, checksummer, attribute);
    }

    // Convert the checksum value to a hex string.
    long checksum = checksummer.getValue();
    final byte[] bytes = new byte[16];
    int j = 15;
    for (int i = 7; i >= 0; i--)
    {
      final byte b = (byte) (checksum & 0xFF);

      final byte l = (byte) (b & 0x0F);
      bytes[j--] = (byte) (l < 10 ? l + 48 : l + 87);

      final byte h = (byte) ((b & 0xF0) >>> 4);
      bytes[j--] = (byte) (h < 10 ? h + 48 : h + 87);

      checksum >>>= 8;
    }
    return ByteString.wrap(bytes);
  }

  private void checksumValue(final Checksum checksummer, final ByteString value)
  {
    final int size = value.length();
    for (int i = 0; i < size; i++)
    {
      checksummer.update(value.byteAt(i) & 0xFF);
    }
  }
}
