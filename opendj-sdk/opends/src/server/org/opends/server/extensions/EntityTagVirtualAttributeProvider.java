/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2012 ForgeRock AS.
 */
package org.opends.server.extensions;



import static org.opends.messages.ExtensionMessages.*;

import java.util.*;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.EntityTagVirtualAttributeCfg;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.*;
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
    /**
     * {@inheritDoc}
     */
    public int compare(final Attribute a1, final Attribute a2)
    {
      return a1.getNameWithOptions().compareTo(a2.getNameWithOptions());
    }
  };

  // Current configuration.
  private volatile EntityTagVirtualAttributeCfg config;



  /**
   * Default constructor invoked by reflection.
   */
  public EntityTagVirtualAttributeProvider()
  {
    // Initialization performed by initializeVirtualAttributeProvider.
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      final EntityTagVirtualAttributeCfg configuration)
  {
    this.config = configuration;
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ConditionResult approximatelyEqualTo(final Entry entry,
      final VirtualAttributeRule rule, final AttributeValue value)
  {
    // ETags cannot be used in approximate matching.
    return ConditionResult.UNDEFINED;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizeVirtualAttributeProvider()
  {
    config.removeEntityTagChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public Set<AttributeValue> getValues(final Entry entry,
      final VirtualAttributeRule rule)
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
    final AttributeValue value = AttributeValues.create(etag, etag);
    return Collections.singleton(value);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ConditionResult greaterThanOrEqualTo(final Entry entry,
      final VirtualAttributeRule rule, final AttributeValue value)
  {
    // ETags cannot be used in ordering matching.
    return ConditionResult.UNDEFINED;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean hasValue(final Entry entry, final VirtualAttributeRule rule)
  {
    // ETag is always present.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeVirtualAttributeProvider(
      final EntityTagVirtualAttributeCfg configuration) throws ConfigException,
      InitializationException
  {
    this.config = configuration;
    configuration.addEntityTagChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      final EntityTagVirtualAttributeCfg configuration,
      final List<Message> unacceptableReasons)
  {
    // The new configuration should always be acceptable.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isMultiValued()
  {
    // ETag is always single-valued.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isSearchable(final VirtualAttributeRule rule,
                              final SearchOperation searchOperation,
                              final boolean isPreIndexed)
  {
    // ETags cannot be searched since there is no way to determine which entry
    // is associated with a particular ETag.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ConditionResult lessThanOrEqualTo(final Entry entry,
      final VirtualAttributeRule rule, final AttributeValue value)
  {
    // ETags cannot be used in ordering matching.
    return ConditionResult.UNDEFINED;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ConditionResult matchesSubstring(final Entry entry,
      final VirtualAttributeRule rule, final ByteString subInitial,
      final List<ByteString> subAny, final ByteString subFinal)
  {
    // ETags cannot be used in substring matching.
    return ConditionResult.UNDEFINED;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void processSearch(final VirtualAttributeRule rule,
      final SearchOperation searchOperation)
  {
    final Message message = ERR_ETAG_VATTR_NOT_SEARCHABLE.get(rule
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
    if (cfg.getExcludedAttribute().contains(attribute.getAttributeType()))
    {
      return;
    }

    // Checksum the attribute description.
    final String atd = attribute.getNameWithOptions();
    final byte[] bytes = StaticUtils.getBytes(atd);
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
      checksumValue(checksummer, attribute.iterator().next().getValue());
      break;
    default:
      // Multi-valued attributes need sorting.
      final ByteString[] values = new ByteString[size];
      int i = 0;
      for (final AttributeValue av : attribute)
      {
        values[i++] = av.getValue();
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
    final List<Attribute> attributes = entry.getAttributes();
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
