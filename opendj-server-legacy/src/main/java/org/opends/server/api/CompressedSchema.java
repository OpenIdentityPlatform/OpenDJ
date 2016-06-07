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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.api;

import static org.opends.messages.CoreMessages.*;

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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.jcip.annotations.GuardedBy;

import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.Schema;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Attributes;
import org.opends.server.types.DirectoryException;

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
  /** Encloses all the encode and decode mappings for attribute and object classes. */
  private static final class Mappings
  {
    /** Maps encoded representation's ID to its attribute description (the List's index is the ID). */
    private final List<AttributeDescription> adDecodeMap = new CopyOnWriteArrayList<>();
    /** Maps attribute description to its encoded representation's ID. */
    private final Map<AttributeDescription, Integer> adEncodeMap;
    /** Maps encoded representation's ID to its object class (the List's index is the ID). */
    private final List<Map<ObjectClass, String>> ocDecodeMap = new CopyOnWriteArrayList<>();
    /** Maps object class to its encoded representation's ID. */
    private final Map<Map<ObjectClass, String>, Integer> ocEncodeMap;

    private Mappings()
    {
      this.adEncodeMap = new ConcurrentHashMap<>();
      this.ocEncodeMap = new ConcurrentHashMap<>();
    }

    private Mappings(int adEncodeMapSize, int ocEncodeMapSize)
    {
      this.adEncodeMap = new ConcurrentHashMap<>(adEncodeMapSize);
      this.ocEncodeMap = new ConcurrentHashMap<>(ocEncodeMapSize);
    }
  }

  private final ServerContext serverContext;
  /** Lock to update the maps. */
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Lock exclusiveLock = lock.writeLock();
  private final Lock sharedLock = lock.readLock();

  /** Schema used to build the compressed information. */
  @GuardedBy("lock")
  private Schema schemaNG;
  @GuardedBy("lock")
  private Mappings mappings = new Mappings();

  /**
   * Creates a new empty instance of this compressed schema.
   *
   * @param serverContext
   *            The server context.
   */
  public CompressedSchema(ServerContext serverContext)
  {
    this.serverContext = serverContext;
  }

  private Mappings getMappings()
  {
    sharedLock.lock();
    try
    {
      return mappings;
    }
    finally
    {
      sharedLock.unlock();
    }
  }

  private Mappings reloadMappingsIfSchemaChanged()
  {
    sharedLock.lock();
    boolean shared = true;
    try
    {
      Schema currentSchema = serverContext.getSchemaNG();
      if (schemaNG != currentSchema)
      {
        sharedLock.unlock();
        exclusiveLock.lock();
        shared = false;

        currentSchema = serverContext.getSchemaNG();
        if (schemaNG != currentSchema)
        {
          // build new maps from existing ones
          Mappings newMappings = new Mappings(mappings.adEncodeMap.size(), mappings.ocEncodeMap.size());
          reloadAttributeTypeMaps(mappings, newMappings);
          reloadObjectClassesMap(mappings, newMappings);

          mappings = newMappings;
          schemaNG = currentSchema;
        }
      }
      return mappings;
    }
    finally
    {
      (shared ? sharedLock : exclusiveLock).unlock();
    }
  }

  /**
   * Reload the attribute types maps. This should be called when schema has changed, because some
   * types may be out dated.
   */
  private void reloadAttributeTypeMaps(Mappings mappings, Mappings newMappings)
  {
    for (Entry<AttributeDescription, Integer> entry : mappings.adEncodeMap.entrySet())
    {
      AttributeDescription ad = entry.getKey();
      Integer id = entry.getValue();
      loadAttributeToMaps(id, ad.getAttributeType().getNameOrOID(), ad.getOptions(), newMappings);
    }
  }

  /**
   * Reload the object classes maps. This should be called when schema has changed, because some
   * classes may be out dated.
   */
  private void reloadObjectClassesMap(Mappings mappings, Mappings newMappings)
  {
    for (Entry<Map<ObjectClass, String>, Integer> entry : mappings.ocEncodeMap.entrySet())
    {
      Map<ObjectClass, String> ocMap = entry.getKey();
      Integer id = entry.getValue();
      loadObjectClassesToMaps(id, ocMap.values(), newMappings, false);
    }
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
    final int adId = decodeId(reader);

    // Before returning the attribute, make sure that the attribute type is not stale.
    final Mappings mappings = reloadMappingsIfSchemaChanged();
    final AttributeDescription ad = mappings.adDecodeMap.get(adId);
    if (ad == null)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_COMPRESSEDSCHEMA_UNRECOGNIZED_AD_TOKEN.get(adId));
    }

    AttributeType attrType = ad.getAttributeType();

    // Determine the number of values for the attribute.
    final int numValues = reader.readBERLength();

    // For the common case of a single value with no options, generate less garbage.
    if (numValues == 1 && !ad.hasOptions())
    {
      return Attributes.create(attrType, readValue(reader));
    }
    else
    {
      // Read the appropriate number of values.
      final AttributeBuilder builder = new AttributeBuilder(attrType);
      builder.setOptions(ad.getOptions());
      for (int i = 0; i < numValues; i++)
      {
        builder.add(readValue(reader));
      }
      return builder.toAttribute();
    }
  }

  private ByteString readValue(final ByteSequenceReader reader)
  {
    return reader.readByteSequence(reader.readBERLength()).toByteString();
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
    final int ocId = decodeId(reader);

    // Before returning the object classes, make sure that none of them are stale.
    final Mappings mappings = reloadMappingsIfSchemaChanged();
    Map<ObjectClass, String> ocMap = mappings.ocDecodeMap.get(ocId);
    if (ocMap == null)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_COMPRESSEDSCHEMA_UNKNOWN_OC_TOKEN.get(ocId));
    }
    return ocMap;
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
    int id = getAttributeId(attribute.getAttributeDescription());

    // Encode the attribute.
    final byte[] idBytes = encodeId(id);
    builder.appendBERLength(idBytes.length);
    builder.appendBytes(idBytes);
    builder.appendBERLength(attribute.size());
    for (final ByteString v : attribute)
    {
      builder.appendBERLength(v.length());
      builder.appendBytes(v);
    }
  }

  private int getAttributeId(final AttributeDescription ad) throws DirectoryException
  {
    // avoid lazy registration races
    boolean shared = true;
    sharedLock.lock();
    try
    {
      Integer id = mappings.adEncodeMap.get(ad);
      if (id != null)
      {
        return id;
      }

      sharedLock.unlock();
      exclusiveLock.lock();
      shared = false;

      id = mappings.adEncodeMap.get(ad);
      if (id == null)
      {
        id = mappings.adDecodeMap.size();
        mappings.adDecodeMap.add(ad);
        mappings.adEncodeMap.put(ad, id);
        storeAttribute(encodeId(id), ad.getAttributeType().getNameOrOID(), ad.getOptions());
      }
      return id;
    }
    finally
    {
      (shared ? sharedLock : exclusiveLock).unlock();
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
    int id = getObjectClassId(objectClasses);

    // Encode the object classes.
    final byte[] idBytes = encodeId(id);
    builder.appendBERLength(idBytes.length);
    builder.appendBytes(idBytes);
  }

  private int getObjectClassId(final Map<ObjectClass, String> objectClasses) throws DirectoryException
  {
    // avoid lazy registration races
    boolean shared = true;
    sharedLock.lock();
    try
    {
      Integer id = mappings.ocEncodeMap.get(objectClasses);
      if (id != null)
      {
        return id;
      }

      sharedLock.unlock();
      exclusiveLock.lock();
      shared = false;

      id = mappings.ocEncodeMap.get(objectClasses);
      if (id == null)
      {
        id = mappings.ocDecodeMap.size();
        mappings.ocDecodeMap.add(objectClasses);
        mappings.ocEncodeMap.put(objectClasses, id);
        storeObjectClasses(encodeId(id), objectClasses.values());
      }
      return id;
    }
    finally
    {
      (shared ? sharedLock : exclusiveLock).unlock();
    }
  }

  /**
   * Returns a view of the encoded attributes in this compressed schema which can be used for saving
   * the entire content to disk.
   * <p>
   * The iterator returned by this method is not thread safe.
   *
   * @return A view of the encoded attributes in this compressed schema.
   */
  protected final Iterable<Entry<byte[], Entry<String, Iterable<String>>>> getAllAttributes()
  {
    return new Iterable<Entry<byte[], Entry<String, Iterable<String>>>>()
    {
      @Override
      public Iterator<Entry<byte[], Entry<String, Iterable<String>>>> iterator()
      {
        return new Iterator<Entry<byte[], Entry<String, Iterable<String>>>>()
        {
          private int id;
          private List<AttributeDescription> adDecodeMap = getMappings().adDecodeMap;

          @Override
          public boolean hasNext()
          {
            return id < adDecodeMap.size();
          }

          @Override
          public Entry<byte[], Entry<String, Iterable<String>>> next()
          {
            final byte[] encodedAttribute = encodeId(id);
            final AttributeDescription ad = adDecodeMap.get(id++);
            return new SimpleImmutableEntry<byte[], Entry<String, Iterable<String>>>(
                encodedAttribute,
                new SimpleImmutableEntry<String, Iterable<String>>(
                    ad.getAttributeType().getNameOrOID(), ad.getOptions()));
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
   * Returns a view of the encoded object classes in this compressed schema which can be used for
   * saving the entire content to disk.
   * <p>
   * The iterator returned by this method is not thread safe.
   *
   * @return A view of the encoded object classes in this compressed schema.
   */
  protected final Iterable<Entry<byte[], Collection<String>>> getAllObjectClasses()
  {
    return new Iterable<Entry<byte[], Collection<String>>>()
    {
      @Override
      public Iterator<Entry<byte[], Collection<String>>> iterator()
      {
        return new Iterator<Map.Entry<byte[], Collection<String>>>()
        {
          private int id;
          private final List<Map<ObjectClass, String>> ocDecodeMap = getMappings().ocDecodeMap;

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
            return new SimpleImmutableEntry<>(encodedObjectClasses, ocMap.values());
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
  protected final AttributeDescription loadAttribute(
      final byte[] encodedAttribute, final String attributeName,
      final Collection<String> attributeOptions)
  {
    final int id = decodeId(encodedAttribute);
    return loadAttributeToMaps(id, attributeName, attributeOptions, getMappings());
  }

  /**
   * Loads an attribute into provided encode and decode maps, given its id, name, and options.
   *
   * @param id
   *          the id computed on the attribute.
   * @param attributeName
   *          The user provided attribute type name.
   * @param attributeOptions
   *          The non-null but possibly empty set of attribute options.
   * @param mappings
   *          attribute description encodeMap and decodeMap maps id to entry
   * @return The attribute type description.
   */
  private AttributeDescription loadAttributeToMaps(final int id, final String attributeName,
      final Iterable<String> attributeOptions, final Mappings mappings)
  {
    final AttributeType type = DirectoryServer.getSchema().getAttributeType(attributeName);
    final Set<String> options = getOptions(attributeOptions);
    final AttributeDescription ad = AttributeDescription.create(type, options);
    exclusiveLock.lock();
    try
    {
      mappings.adEncodeMap.put(ad, id);
      if (id < mappings.adDecodeMap.size())
      {
        mappings.adDecodeMap.set(id, ad);
      }
      else
      {
        // Grow the decode array.
        while (id > mappings.adDecodeMap.size())
        {
          mappings.adDecodeMap.add(null);
        }
        mappings.adDecodeMap.add(ad);
      }
      return ad;
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  private Set<String> getOptions(final Iterable<String> attributeOptions)
  {
    Iterator<String> it = attributeOptions.iterator();
    if (!it.hasNext())
    {
      return Collections.emptySet();
    }
    String firstOption = it.next();
    if (!it.hasNext())
    {
      return Collections.singleton(firstOption);
    }
    LinkedHashSet<String> results = new LinkedHashSet<>();
    results.add(firstOption);
    while (it.hasNext())
    {
      results.add(it.next());
    }
    return results;
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
    final int id = decodeId(encodedObjectClasses);
    return loadObjectClassesToMaps(id, objectClassNames, mappings, true);
  }

  /**
   * Loads a set of object classes into provided encode and decode maps, given the id and set of
   * names.
   *
   * @param id
   *          the id computed on the object classes set.
   * @param objectClassNames
   *          The user provided set of object class names.
   * @param mappings
   *          .ocEncodeMap maps id to entry
   * @param mappings
   *          .ocDecodeMap maps entry to id
   * @param sync
   *          indicates if update of maps should be synchronized
   * @return The object class set.
   */
  private final Map<ObjectClass, String> loadObjectClassesToMaps(int id, final Collection<String> objectClassNames,
      Mappings mappings, boolean sync)
  {
    final LinkedHashMap<ObjectClass, String> ocMap = new LinkedHashMap<>(objectClassNames.size());
    for (final String name : objectClassNames)
    {
      ocMap.put(DirectoryServer.getSchema().getObjectClass(name), name);
    }
    if (sync)
    {
      exclusiveLock.lock();
      try
      {
        updateObjectClassesMaps(id, mappings, ocMap);
      }
      finally
      {
        exclusiveLock.unlock();
      }
    }
    else
    {
      updateObjectClassesMaps(id, mappings, ocMap);
    }
    return ocMap;
  }

  private void updateObjectClassesMaps(int id, Mappings mappings, LinkedHashMap<ObjectClass, String> ocMap)
  {
    mappings.ocEncodeMap.put(ocMap, id);
    if (id < mappings.ocDecodeMap.size())
    {
      mappings.ocDecodeMap.set(id, ocMap);
    }
    else
    {
      // Grow the decode array.
      while (id > mappings.ocDecodeMap.size())
      {
        mappings.ocDecodeMap.add(null);
      }
      mappings.ocDecodeMap.add(ocMap);
    }
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
      final String attributeName, final Iterable<String> attributeOptions)
      throws DirectoryException
  {
    // Do nothing by default.
  }

  /**
   * Persists the provided encoded object classes. The default implementation is
   * to do nothing. Calls to this method are synchronized, so implementations
   * can assume that this method is not being called by other threads. Note that
   * this method is not thread-safe with respect to
   * {@link #storeAttribute(byte[], String, Iterable)}.
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
      id |= b & 0xFF;
    }
    return id - 1; // Subtract 1 to compensate for old behavior.
  }

  private int decodeId(final ByteSequenceReader reader)
  {
    final int length = reader.readBERLength();
    final byte[] idBytes = new byte[length];
    reader.readBytes(idBytes);
    return decodeId(idBytes);
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
