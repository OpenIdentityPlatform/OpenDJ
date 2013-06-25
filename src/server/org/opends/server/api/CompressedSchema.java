/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock AS.
 */
package org.opends.server.api;



import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.StaticUtils.toLowerCase;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.opends.messages.Message;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.AttributeValues;
import org.opends.server.types.Attributes;
import org.opends.server.types.ByteSequenceReader;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringBuilder;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ObjectClass;



/**
 * This class provides a utility for interacting with compressed representations
 * of schema elements. The default implementation does not persist encoded
 * attributes and object classes.
 */
@org.opends.server.types.PublicAPI(
    stability = org.opends.server.types.StabilityLevel.UNCOMMITTED,
    mayInstantiate = false,
    mayExtend = true,
    mayInvoke = false)
public class CompressedSchema
{
  // Maps attribute description to ID.
  private final List<Entry<AttributeType, Set<String>>> adDecodeMap;

  // Maps ID to attribute description.
  private final Map<Entry<AttributeType, Set<String>>, Integer> adEncodeMap;

  // The map between encoded representations and object class sets.
  private final List<Map<ObjectClass, String>> ocDecodeMap;

  // The map between object class sets and encoded representations.
  private final Map<Map<ObjectClass, String>, Integer> ocEncodeMap;



  /**
   * Creates a new empty instance of this compressed schema.
   */
  public CompressedSchema()
  {
    adDecodeMap = new CopyOnWriteArrayList<Entry<AttributeType, Set<String>>>();
    ocDecodeMap = new CopyOnWriteArrayList<Map<ObjectClass, String>>();
    adEncodeMap = new ConcurrentHashMap<Entry<AttributeType, Set<String>>,
                                        Integer>();
    ocEncodeMap = new ConcurrentHashMap<Map<ObjectClass, String>, Integer>();
  }



  /**
   * Decodes the contents of the provided array as an attribute at the current
   * position.
   *
   * @param reader
   *          The byte string reader containing the encoded entry.
   * @return The decoded attribute.
   * @throws DirectoryException
   *           If the attribute could not be decoded properly for some reason.
   */
  public final Attribute decodeAttribute(final ByteSequenceReader reader)
      throws DirectoryException
  {
    // First decode the encoded attribute description id.
    final int length = reader.getBERLength();
    final byte[] idBytes = new byte[length];
    reader.get(idBytes);
    final int id = decodeId(idBytes);

    // Look up the attribute description.
    Entry<AttributeType, Set<String>> ad = adDecodeMap.get(id);
    if (ad == null)
    {
      final Message message = ERR_COMPRESSEDSCHEMA_UNRECOGNIZED_AD_TOKEN
          .get(String.valueOf(id));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          message);
    }

    // Before returning the attribute, make sure that the attribute type is not
    // stale.
    AttributeType attrType = ad.getKey();
    Set<String> options = ad.getValue();
    if (attrType.isDirty())
    {
      ad = loadAttribute(idBytes, attrType.getNameOrOID(), options);
      attrType = ad.getKey();
      options = ad.getValue();
    }

    // Determine the number of values for the attribute.
    final int numValues = reader.getBERLength();

    // For the common case of a single value with no options, generate
    // less garbage.
    if (numValues == 1 && options.isEmpty())
    {
      final int valueLength = reader.getBERLength();
      final ByteString valueBytes = reader.getByteSequence(valueLength)
          .toByteString();
      return Attributes.create(attrType,
          AttributeValues.create(attrType, valueBytes));
    }
    else
    {
      // Read the appropriate number of values.
      final AttributeBuilder builder = new AttributeBuilder(attrType);
      builder.setOptions(options);
      builder.setInitialCapacity(numValues);
      for (int i = 0; i < numValues; i++)
      {
        final int valueLength = reader.getBERLength();
        final ByteString valueBytes = reader.getByteSequence(valueLength)
            .toByteString();
        builder.add(AttributeValues.create(attrType, valueBytes));
      }
      return builder.toAttribute();
    }
  }



  /**
   * Decodes an object class set from the provided byte string.
   *
   * @param reader
   *          The byte string reader containing the object class set identifier.
   * @return The decoded object class set.
   * @throws DirectoryException
   *           If the provided byte string reader cannot be decoded as an object
   *           class set.
   */
  public final Map<ObjectClass, String> decodeObjectClasses(
      final ByteSequenceReader reader) throws DirectoryException
  {
    // First decode the encoded object class id.
    final int length = reader.getBERLength();
    final byte[] idBytes = new byte[length];
    reader.get(idBytes);
    final int id = decodeId(idBytes);

    // Look up the object classes.
    final Map<ObjectClass, String> ocMap = ocDecodeMap.get(id);
    if (ocMap != null)
    {
      // Before returning the object classes, make sure that none of them are
      // stale.
      for (final ObjectClass oc : ocMap.keySet())
      {
        if (oc.isDirty())
        {
          // Found at least one object class which is dirty so refresh them.
          return loadObjectClasses(idBytes, ocMap.values());
        }
      }
      return ocMap;
    }
    else
    {
      final Message message = ERR_COMPRESSEDSCHEMA_UNKNOWN_OC_TOKEN.get(String
          .valueOf(id));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          message);
    }
  }



  /**
   * Encodes the information in the provided attribute to a byte array.
   *
   * @param builder
   *          The buffer to encode the attribute to.
   * @param attribute
   *          The attribute to be encoded.
   * @throws DirectoryException
   *           If a problem occurs while attempting to determine the appropriate
   *           identifier.
   */
  public final void encodeAttribute(final ByteStringBuilder builder,
      final Attribute attribute) throws DirectoryException
  {
    // Re-use or allocate a new ID.
    final AttributeType type = attribute.getAttributeType();
    final Set<String> options = attribute.getOptions();
    final Entry<AttributeType, Set<String>> ad =
        new SimpleImmutableEntry<AttributeType, Set<String>>(type, options);

    // Use double checked locking to avoid lazy registration races.
    Integer id = adEncodeMap.get(ad);
    if (id == null)
    {
      synchronized (adEncodeMap)
      {
        id = adEncodeMap.get(ad);
        if (id == null)
        {
          id = adDecodeMap.size();
          adDecodeMap.add(ad);
          adEncodeMap.put(ad, id);
          storeAttribute(encodeId(id), type.getNameOrOID(), options);
        }
      }
    }

    // Encode the attribute.
    final byte[] idBytes = encodeId(id);
    builder.appendBERLength(idBytes.length);
    builder.append(idBytes);
    builder.appendBERLength(attribute.size());
    for (final AttributeValue v : attribute)
    {
      builder.appendBERLength(v.getValue().length());
      builder.append(v.getValue());
    }
  }



  /**
   * Encodes the provided set of object classes to a byte array. If the same set
   * had been previously encoded, then the cached value will be used. Otherwise,
   * a new value will be created.
   *
   * @param builder
   *          The buffer to encode the object classes to.
   * @param objectClasses
   *          The set of object classes for which to retrieve the corresponding
   *          byte array token.
   * @throws DirectoryException
   *           If a problem occurs while attempting to determine the appropriate
   *           identifier.
   */
  public final void encodeObjectClasses(final ByteStringBuilder builder,
      final Map<ObjectClass, String> objectClasses) throws DirectoryException
  {
    // Re-use or allocate a new ID.
    // Use double checked locking to avoid lazy registration races.
    Integer id = ocEncodeMap.get(objectClasses);
    if (id == null)
    {
      synchronized (ocEncodeMap)
      {
        id = ocEncodeMap.get(objectClasses);
        if (id == null)
        {
          id = ocDecodeMap.size();
          ocDecodeMap.add(objectClasses);
          ocEncodeMap.put(objectClasses, id);
          storeObjectClasses(encodeId(id), objectClasses.values());
        }
      }
    }

    // Encode the object classes.
    final byte[] idBytes = encodeId(id);
    builder.appendBERLength(idBytes.length);
    builder.append(idBytes);
  }



  /**
   * Returns a view of the encoded attributes in this compressed schema which
   * can be used for saving the entire content to disk. The iterator returned by
   * this method is not thread safe.
   *
   * @return A view of the encoded attributes in this compressed schema.
   */
  protected final Iterable<Entry<byte[],
                                 Entry<String,
                                       Collection<String>>>> getAllAttributes()
  {
    return new Iterable<Entry<byte[], Entry<String, Collection<String>>>>()
    {

      @Override
      public Iterator<Entry<byte[],
                            Entry<String, Collection<String>>>> iterator()
      {
        return new Iterator<Entry<byte[], Entry<String, Collection<String>>>>()
        {
          private int id = 0;



          @Override
          public boolean hasNext()
          {
            return id < adDecodeMap.size();
          }



          @Override
          public Entry<byte[], Entry<String, Collection<String>>> next()
          {
            final byte[] encodedAttribute = encodeId(id);
            final Entry<AttributeType, Set<String>> ad = adDecodeMap.get(id++);
            return new SimpleImmutableEntry<byte[],
                                            Entry<String, Collection<String>>>(
                encodedAttribute,
                new SimpleImmutableEntry<String, Collection<String>>(ad
                    .getKey().getNameOrOID(), ad.getValue()));
          }



          @Override
          public void remove()
          {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }



  /**
   * Returns a view of the encoded object classes in this compressed schema
   * which can be used for saving the entire content to disk. The iterator
   * returned by this method is not thread safe.
   *
   * @return A view of the encoded object classes in this compressed schema.
   */
  protected final Iterable<Entry<byte[],
                                 Collection<String>>> getAllObjectClasses()
  {
    return new Iterable<Entry<byte[], Collection<String>>>()
    {

      @Override
      public Iterator<Entry<byte[], Collection<String>>> iterator()
      {
        return new Iterator<Map.Entry<byte[], Collection<String>>>()
        {
          private int id = 0;



          @Override
          public boolean hasNext()
          {
            return id < ocDecodeMap.size();
          }



          @Override
          public Entry<byte[], Collection<String>> next()
          {
            final byte[] encodedObjectClasses = encodeId(id);
            final Map<ObjectClass, String> ocMap = ocDecodeMap.get(id++);
            return new SimpleImmutableEntry<byte[], Collection<String>>(
                encodedObjectClasses, ocMap.values());
          }



          @Override
          public void remove()
          {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }



  /**
   * Loads an encoded attribute into this compressed schema. This method may
   * called by implementations during initialization when loading content from
   * disk.
   *
   * @param encodedAttribute
   *          The encoded attribute description.
   * @param attributeName
   *          The user provided attribute type name.
   * @param attributeOptions
   *          The non-null but possibly empty set of attribute options.
   * @return The attribute type description.
   */
  protected final Entry<AttributeType, Set<String>> loadAttribute(
      final byte[] encodedAttribute, final String attributeName,
      final Collection<String> attributeOptions)
  {
    final AttributeType type = DirectoryServer.getAttributeType(
        toLowerCase(attributeName), true);
    final Set<String> options;
    switch (attributeOptions.size())
    {
    case 0:
      options = Collections.emptySet();
      break;
    case 1:
      options = Collections.singleton(attributeOptions.iterator().next());
      break;
    default:
      options = new LinkedHashSet<String>(attributeOptions);
      break;
    }
    final Entry<AttributeType, Set<String>> ad =
        new SimpleImmutableEntry<AttributeType, Set<String>>(type, options);
    final int id = decodeId(encodedAttribute);
    synchronized (adEncodeMap)
    {
      adEncodeMap.put(ad, id);
      if (id < adDecodeMap.size())
      {
        adDecodeMap.set(id, ad);
      }
      else
      {
        // Grow the decode array.
        while (id > adDecodeMap.size())
        {
          adDecodeMap.add(null);
        }
        adDecodeMap.add(ad);
      }
    }
    return ad;
  }



  /**
   * Loads an encoded object class into this compressed schema. This method may
   * called by implementations during initialization when loading content from
   * disk.
   *
   * @param encodedObjectClasses
   *          The encoded object classes.
   * @param objectClassNames
   *          The user provided set of object class names.
   * @return The object class set.
   */
  protected final Map<ObjectClass, String> loadObjectClasses(
      final byte[] encodedObjectClasses,
      final Collection<String> objectClassNames)
  {
    final LinkedHashMap<ObjectClass, String> ocMap =
        new LinkedHashMap<ObjectClass, String>(objectClassNames.size());
    for (final String name : objectClassNames)
    {
      final String lowerName = toLowerCase(name);
      final ObjectClass oc = DirectoryServer.getObjectClass(lowerName, true);
      ocMap.put(oc, name);
    }
    final int id = decodeId(encodedObjectClasses);
    synchronized (ocEncodeMap)
    {
      ocEncodeMap.put(ocMap, id);
      if (id < ocDecodeMap.size())
      {
        ocDecodeMap.set(id, ocMap);
      }
      else
      {
        // Grow the decode array.
        while (id > ocDecodeMap.size())
        {
          ocDecodeMap.add(null);
        }
        ocDecodeMap.add(ocMap);
      }
    }
    return ocMap;
  }



  /**
   * Persists the provided encoded attribute. The default implementation is to
   * do nothing. Calls to this method are synchronized, so implementations can
   * assume that this method is not being called by other threads. Note that
   * this method is not thread-safe with respect to
   * {@link #storeObjectClasses(byte[], Collection)}.
   *
   * @param encodedAttribute
   *          The encoded attribute description.
   * @param attributeName
   *          The user provided attribute type name.
   * @param attributeOptions
   *          The non-null but possibly empty set of attribute options.
   * @throws DirectoryException
   *           If an error occurred while persisting the encoded attribute.
   */
  protected void storeAttribute(final byte[] encodedAttribute,
      final String attributeName, final Collection<String> attributeOptions)
      throws DirectoryException
  {
    // Do nothing by default.
  }



  /**
   * Persists the provided encoded object classes. The default implementation is
   * to do nothing. Calls to this method are synchronized, so implementations
   * can assume that this method is not being called by other threads. Note that
   * this method is not thread-safe with respect to
   * {@link #storeAttribute(byte[], String, Collection)}.
   *
   * @param encodedObjectClasses
   *          The encoded object classes.
   * @param objectClassNames
   *          The user provided set of object class names.
   * @throws DirectoryException
   *           If an error occurred while persisting the encoded object classes.
   */
  protected void storeObjectClasses(final byte[] encodedObjectClasses,
      final Collection<String> objectClassNames) throws DirectoryException
  {
    // Do nothing by default.
  }



  /**
   * Decodes the provided encoded schema element ID.
   *
   * @param idBytes
   *          The encoded schema element ID.
   * @return The schema element ID.
   */
  private int decodeId(final byte[] idBytes)
  {
    int id = 0;
    for (final byte b : idBytes)
    {
      id <<= 8;
      id |= (b & 0xFF);
    }
    return id - 1; // Subtract 1 to compensate for old behavior.
  }



  /**
   * Encodes the provided schema element ID.
   *
   * @param id
   *          The schema element ID.
   * @return The encoded schema element ID.
   */
  private byte[] encodeId(final int id)
  {
    final int value = id + 1; // Add 1 to compensate for old behavior.
    final byte[] idBytes;
    if (value <= 0xFF)
    {
      idBytes = new byte[1];
      idBytes[0] = (byte) (value & 0xFF);
    }
    else if (value <= 0xFFFF)
    {
      idBytes = new byte[2];
      idBytes[0] = (byte) ((value >> 8) & 0xFF);
      idBytes[1] = (byte) (value & 0xFF);
    }
    else if (value <= 0xFFFFFF)
    {
      idBytes = new byte[3];
      idBytes[0] = (byte) ((value >> 16) & 0xFF);
      idBytes[1] = (byte) ((value >> 8) & 0xFF);
      idBytes[2] = (byte) (value & 0xFF);
    }
    else
    {
      idBytes = new byte[4];
      idBytes[0] = (byte) ((value >> 24) & 0xFF);
      idBytes[1] = (byte) ((value >> 16) & 0xFF);
      idBytes[2] = (byte) ((value >> 8) & 0xFF);
      idBytes[3] = (byte) (value & 0xFF);
    }
    return idBytes;
  }
}
