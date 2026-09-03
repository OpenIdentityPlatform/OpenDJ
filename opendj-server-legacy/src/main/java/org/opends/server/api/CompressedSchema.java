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
 * Portions Copyright 2024-2026 3A Systems, LLC
 */
package org.opends.server.api;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.StaticUtils.bytesToHexNoSpace;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageDescriptor;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.util.annotations.VisibleForTesting;
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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

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

  /** The most bytes {@link #encodeId(int)} ever writes a schema element ID in. */
  private static final int MAX_ID_BYTES = 4;

  /**
   * The highest ID a definition may be loaded under.
   * <p>
   * The decode maps are indexed by the ID, so an ID read out of a storage is also the size the map
   * has to be padded to, and the padding runs under {@link #exclusiveLock} while a backend opens.
   * Nothing in a token says whether the ID it folds to is one this server allocated or one a
   * corrupt record composed, so the ceiling is what keeps the second from costing the memory of the
   * first: four bytes address two billion slots, and {@link #encodeId(int)} writes four of them for
   * every ID past 16777214, so a truncated or partially written record is enough to name one.
   * <p>
   * An ID is allocated per distinct attribute description - the type together with its options -
   * and per distinct object class set, once and for the life of the storage. A deployment encodes
   * those in the hundreds, and a large one in the thousands, so this leaves a hundredfold headroom
   * over what a compressed schema is asked to hold. Above it a definition is skipped and reported:
   * a definition wrongly skipped is one the decode path reports as an unknown token, which is loud,
   * whereas an ID taken at face value is an open that does not finish.
   * <p>
   * The same ceiling bounds what {@link #allocatable(int)} hands out, and it has to: a token this
   * schema writes but would not take back leaves an entry that cannot be decoded after a restart.
   */
  @VisibleForTesting
  static final int MAX_LOAD_ID = 1 << 20;

  private final ServerContext serverContext;
  /** Lock serializing all mutations (id registration and schema reload). */
  private final ReentrantLock exclusiveLock = new ReentrantLock();

  /**
   * Readers are lock-free: the Mappings internals are concurrent collections,
   * and both references are volatile. decodeAttribute() runs for every
   * attribute of every entry read from a backend, so taking even a read lock
   * here becomes a cross-core hotspot under load. On schema reload the new
   * mappings reference is written before the schema reference, so a reader
   * observing the current schema also observes the mappings rebuilt for it.
   */
  private volatile Schema schema;
  private volatile Mappings mappings = new Mappings();

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
    return mappings;
  }

  private Mappings reloadMappingsIfSchemaChanged()
  {
    // Lock-free fast path: the schema reference must be read before the
    // mappings reference, mirroring the publication order in the slow path.
    if (schema == serverContext.getSchema())
    {
      return mappings;
    }

    exclusiveLock.lock();
    try
    {
      Schema currentSchema = serverContext.getSchema();
      if (schema != currentSchema)
      {
        // build new maps from one stable snapshot of the existing ones
        final Mappings oldMappings = mappings;
        Mappings newMappings = new Mappings(oldMappings.adEncodeMap.size(), oldMappings.ocEncodeMap.size());
        reloadAttributeTypeMaps(oldMappings, newMappings, currentSchema);
        reloadObjectClassesMap(oldMappings, newMappings, currentSchema);

        mappings = newMappings;
        schema = currentSchema;
      }
      return mappings;
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  /**
   * Reload the attribute types maps. This should be called when schema has changed, because some
   * types may be out dated.
   */
  private void reloadAttributeTypeMaps(Mappings mappings, Mappings newMappings, Schema newSchema)
  {
    // Built whole and handed to the decode map in one go: it is a CopyOnWriteArrayList, so
    // appending the elements one at a time copies the whole backing array per element, and this
    // walks every id the compressed schema holds while readers of every backend wait on the lock.
    final int size = mappings.adDecodeMap.size();
    final List<AttributeDescription> reloaded = new ArrayList<>(size);
    for (int id = 0; id < size; id++)
    {
      final AttributeDescription ad = mappings.adDecodeMap.get(id);
      if (ad == null)
      {
        // A decode map can carry a gap: it is padded with null for the ids missing from the
        // compressed schema it was loaded from, and for the ids of the definitions it skipped.
        // Carried over rather than dereferenced, and carried over as a gap - dropping it would
        // shift the ids of the elements after it, and would let the next registration hand out an
        // id an already written entry carries.
        reloaded.add(null);
        continue;
      }
      final AttributeDescription reloadedAd = AttributeDescription.create(
          newSchema.getAttributeType(ad.getAttributeType().getNameOrOID()), getOptions(ad.getOptions()));
      newMappings.adEncodeMap.put(reloadedAd, id);
      reloaded.add(reloadedAd);
    }
    newMappings.adDecodeMap.addAll(reloaded);
  }

  /**
   * Reload the object classes maps. This should be called when schema has changed, because some
   * classes may be out dated.
   */
  private void reloadObjectClassesMap(Mappings mappings, Mappings newMappings, Schema newSchema)
  {
    // Built whole and handed over in one go, and the gaps are carried, as in
    // reloadAttributeTypeMaps().
    final int size = mappings.ocDecodeMap.size();
    final List<Map<ObjectClass, String>> reloaded = new ArrayList<>(size);
    for (int id = 0; id < size; id++)
    {
      final Map<ObjectClass, String> ocMap = mappings.ocDecodeMap.get(id);
      if (ocMap == null)
      {
        reloaded.add(null);
        continue;
      }
      final Map<ObjectClass, String> reloadedOcMap = objectClassMap(newSchema, ocMap.values());
      newMappings.ocEncodeMap.put(reloadedOcMap, id);
      reloaded.add(reloadedOcMap);
    }
    newMappings.ocDecodeMap.addAll(reloaded);
  }

  /** The object class set a compressed schema holds for the provided names, resolved against a schema. */
  private static Map<ObjectClass, String> objectClassMap(final Schema schema, final Collection<String> names)
  {
    final LinkedHashMap<ObjectClass, String> ocMap = new LinkedHashMap<>(names.size());
    for (final String name : names)
    {
      ocMap.put(schema.getObjectClass(name), name);
    }
    return ocMap;
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
    final byte[] adIdBytes = readIdBytes(reader, ERR_COMPRESSEDSCHEMA_UNREADABLE_AD_TOKEN);
    final int adId = decodeId(adIdBytes);

    // Before returning the attribute, make sure that the attribute type is not stale.
    final Mappings mappings = reloadMappingsIfSchemaChanged();
    final AttributeDescription ad = decodeMapGet(mappings.adDecodeMap, adId);
    if (ad == null)
    {
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(),
          ERR_COMPRESSEDSCHEMA_UNRECOGNIZED_AD_TOKEN.get(tokenInMessage(adIdBytes, adId)));
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
   * Returns the element a token addresses, or {@code null} where it addresses none: the token can
   * be outside the range of the decode map, or address one of the slots the map is padded with for
   * the ids missing from the compressed schema it was loaded from. Both are reported to the caller
   * as the unknown token they are, rather than let out of the decode path as an unchecked
   * exception the callers of that path are not written for.
   *
   * @param decodeMap
   *          The decode map to look the token up in.
   * @param id
   *          The decoded token.
   * @return The element registered under the token, or {@code null} if there is none.
   */
  private static <T> T decodeMapGet(final List<T> decodeMap, final int id)
  {
    if (id < 0)
    {
      return null;
    }
    try
    {
      return decodeMap.get(id);
    }
    catch (final IndexOutOfBoundsException e)
    {
      // Caught rather than kept away by a comparison against size(): size() and get() of a
      // CopyOnWriteArrayList read the array separately, so the comparison would not make the
      // lookup safe anyway, and this runs for every attribute of every entry read from a backend -
      // the common path is left with the single read it had.
      //
      // Traced here because the caller turns this into a DirectoryException carrying the token:
      // the generic catch of Entry.decode(), which used to convert this exception, logged the
      // stack, and where the token came from is worth keeping for a corrupt store.
      logger.traceException(e);
      return null;
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
    final byte[] ocIdBytes = readIdBytes(reader, ERR_COMPRESSEDSCHEMA_UNREADABLE_OC_TOKEN);
    final int ocId = decodeId(ocIdBytes);

    // Before returning the object classes, make sure that none of them are stale.
    final Mappings mappings = reloadMappingsIfSchemaChanged();
    Map<ObjectClass, String> ocMap = decodeMapGet(mappings.ocDecodeMap, ocId);
    if (ocMap == null)
    {
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(),
          ERR_COMPRESSEDSCHEMA_UNKNOWN_OC_TOKEN.get(tokenInMessage(ocIdBytes, ocId)));
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
    // Lock-free fast path for already-registered attribute descriptions.
    Integer id = mappings.adEncodeMap.get(ad);
    if (id != null)
    {
      return id;
    }

    // Take the exclusive lock to avoid lazy registration races, and re-read
    // the mappings reference: a schema reload may have replaced it.
    exclusiveLock.lock();
    try
    {
      final Mappings mappings = this.mappings;
      id = mappings.adEncodeMap.get(ad);
      if (id == null)
      {
        id = registerAttribute(mappings, ad);
      }
      return id;
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  /**
   * Registers a new attribute description and returns the id allocated to it. The registration is
   * persisted before it is published, and is withdrawn if it cannot be persisted: an entry must
   * never be written with a token whose definition did not reach the storage, because nothing
   * stores it afterwards and the entry cannot be decoded once the server is restarted.
   * <p>
   * Must be called with the exclusive lock held, which is what makes the id allocated here still
   * the last element of the decode map when it has to be withdrawn. The lock is reentrant, so
   * that holds only while the store stays out of this compressed schema: an implementation of
   * {@link #storeAttribute(byte[], String, Iterable)} must re-enter neither the encode, the load
   * nor the decode path of it, which its own javadoc says as well. That it did stay out is
   * checked by {@link #withdraw(Mappings, List, int, Object)} rather than assumed, since removing
   * an element this registration did not append is worse than the leak it withdraws.
   */
  private int registerAttribute(final Mappings mappings, final AttributeDescription ad) throws DirectoryException
  {
    final int id = allocatable(mappings.adDecodeMap.size());
    // Appended to the decode map first: storeAttribute() is free to persist the whole content of
    // this compressed schema rather than the single element it is handed - DefaultCompressedSchema
    // rewrites its file from getAllAttributes() - so the element being registered has to be part
    // of it by then. The decode map is not what an encode reaches the id through, so nothing can
    // yet write an entry carrying it.
    mappings.adDecodeMap.add(ad);
    boolean registered = false;
    try
    {
      storeAttribute(encodeId(id), ad.getAttributeType().getNameOrOID(), ad.getOptions());
      // Published only once persisted: the encode map is read without the lock, so an id another
      // thread finds there can be carried by an entry a moment later and must never be withdrawn.
      mappings.adEncodeMap.put(ad, id);
      registered = true;
    }
    finally
    {
      if (!registered)
      {
        withdraw(mappings, mappings.adDecodeMap, id, ad);
      }
    }
    return id;
  }

  /**
   * Withdraws the element a failed registration appended, so that the next attempt allocates the
   * id again and stores it. Removed by index, and by the index of the last element: every append
   * is made under the exclusive lock, so this is still the element appended by the registration
   * being withdrawn, and no other id shifts.
   * <p>
   * That the element is still there is checked rather than assumed, because the lock is
   * reentrant: a store re-entering the encode or the load path appends to the same decode map,
   * and one re-entering the decode path replaces the mappings altogether. Neither is allowed by
   * the contract of {@link #storeAttribute(byte[], String, Iterable)}, and neither is withdrawn
   * from here - removing an element this registration did not append shifts the ids of everything
   * after it, and the entries already written carry them. The violation is reported instead, and
   * the element is left where it is: the registration leaks, which is what this method exists to
   * prevent, but no id already handed out starts decoding as something else.
   * <p>
   * Reported rather than thrown, because this runs while the failure of the store is on its way
   * out: that failure is what the caller has to be told.
   *
   * @param mappings
   *          The mappings the registration appended to.
   * @param decodeMap
   *          The decode map of {@code mappings} the element was appended to.
   * @param appendedAt
   *          The index the element was appended at, which is the id allocated to it.
   * @param appended
   *          The element that was appended.
   */
  private void withdraw(final Mappings mappings, final List<?> decodeMap, final int appendedAt,
      final Object appended)
  {
    if (mappings == this.mappings
        && decodeMap.size() == appendedAt + 1
        && decodeMap.get(appendedAt) == appended)
    {
      decodeMap.remove(appendedAt);
      return;
    }
    logger.error(LocalizableMessage.raw(
        "The registration of the compressed schema id %s could not be withdrawn after its store failed, "
            + "because the store re-entered the compressed schema it was called from: the id is now taken "
            + "by a definition that was never persisted. This is a defect of %s, whose store must re-enter "
            + "neither the encode, the load nor the decode path of the compressed schema.",
        appendedAt, getClass().getName()));
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
    // Lock-free fast path for already-registered object class sets.
    Integer id = mappings.ocEncodeMap.get(objectClasses);
    if (id != null)
    {
      return id;
    }

    // Take the exclusive lock to avoid lazy registration races, and re-read
    // the mappings reference: a schema reload may have replaced it.
    exclusiveLock.lock();
    try
    {
      final Mappings mappings = this.mappings;
      id = mappings.ocEncodeMap.get(objectClasses);
      if (id == null)
      {
        id = registerObjectClasses(mappings, objectClasses);
      }
      return id;
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  /**
   * Registers a new object class set and returns the id allocated to it, persisting the
   * registration before publishing it and withdrawing it if it cannot be persisted, exactly as
   * {@link #registerAttribute(Mappings, AttributeDescription)} does.
   * <p>
   * Must be called with the exclusive lock held, and under the same constraint on what
   * {@link #storeObjectClasses(byte[], Collection)} may re-enter.
   */
  private int registerObjectClasses(final Mappings mappings, final Map<ObjectClass, String> objectClasses)
      throws DirectoryException
  {
    final int id = allocatable(mappings.ocDecodeMap.size());
    mappings.ocDecodeMap.add(objectClasses);
    boolean registered = false;
    try
    {
      storeObjectClasses(encodeId(id), objectClasses.values());
      mappings.ocEncodeMap.put(objectClasses, id);
      registered = true;
    }
    finally
    {
      if (!registered)
      {
        withdraw(mappings, mappings.ocDecodeMap, id, objectClasses);
      }
    }
    return id;
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
          private final List<AttributeDescription> adDecodeMap = getMappings().adDecodeMap;

          @Override
          public boolean hasNext()
          {
            // Skips the gaps: a decode map padded with null for the ids missing from the
            // compressed schema it was loaded from is still saved, and the ids around a gap are
            // preserved by the token each element is written with. Looked up through
            // decodeMapGet(), because withdrawing a registration shortens the decode map and a
            // CopyOnWriteArrayList reads its array separately for size() and for get(). In tree
            // this iteration runs under the exclusive lock - the only caller of save() is a store
            // - but the class is extensible and a subclass can reach here from anywhere.
            while (id < adDecodeMap.size())
            {
              if (decodeMapGet(adDecodeMap, id) != null)
              {
                return true;
              }
              id++;
            }
            return false;
          }

          @Override
          public Entry<byte[], Entry<String, Iterable<String>>> next()
          {
            if (!hasNext())
            {
              throw new NoSuchElementException();
            }
            final byte[] encodedAttribute = encodeId(id);
            final AttributeDescription ad = decodeMapGet(adDecodeMap, id++);
            if (ad == null)
            {
              // The decode map was shortened between hasNext() and here.
              throw new NoSuchElementException();
            }
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
            // Skips the gaps, and looks the elements up the same way, as in getAllAttributes().
            while (id < ocDecodeMap.size())
            {
              if (decodeMapGet(ocDecodeMap, id) != null)
              {
                return true;
              }
              id++;
            }
            return false;
          }

          @Override
          public Entry<byte[], Collection<String>> next()
          {
            if (!hasNext())
            {
              throw new NoSuchElementException();
            }
            final byte[] encodedObjectClasses = encodeId(id);
            final Map<ObjectClass, String> ocMap = decodeMapGet(ocDecodeMap, id++);
            if (ocMap == null)
            {
              // The decode map was shortened between hasNext() and here.
              throw new NoSuchElementException();
            }
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
   * @return The attribute type description, or {@code null} if the definition was skipped because
   *         the key it is stored under is not one a compressed schema hands out.
   */
  protected final AttributeDescription loadAttribute(
      final byte[] encodedAttribute, final String attributeName,
      final Collection<String> attributeOptions)
  {
    final int id = decodeId(encodedAttribute);
    if (!isLoadable(encodedAttribute, id))
    {
      logger.error(ERR_COMPRESSEDSCHEMA_UNUSABLE_AD_TOKEN, attributeName,
          tokenInMessage(encodedAttribute, id));
      reserve(getMappings().adDecodeMap, id);
      return null;
    }
    return loadAttributeToMaps(id, attributeName, attributeOptions, getMappings());
  }

  /**
   * Tells whether a definition may be loaded under the key a storage holds it under, which is the
   * one thing the load path knows about that key: {@link #decodeId(byte[])} folds whatever it is
   * handed, so every key reads as an ID and nothing after this can tell one a compressed schema
   * wrote from one a corrupt or truncated record composed. A record failing this is skipped rather
   * than loaded - the ID it names is left with no definition, which the decode path already
   * reports - because the alternative is an open that fails or never finishes for one bad record.
   * <p>
   * The key has to be the one {@link #encodeId(int)} writes for the ID it folds to. A key padded
   * with leading zeros, or longer than the four bytes an ID is ever written in, folds to the ID its
   * canonical key addresses, and loading it would displace the definition that ID belongs to: the
   * decode map is only overwritten there, so entries carrying that token would go on decoding, as
   * another attribute description. The sign is checked on its own because the canonical key of the
   * ID -1 is the all-zero key that folds to it, and an ID is checked against
   * {@link #MAX_LOAD_ID} because the decode map is padded up to it.
   *
   * @param idBytes
   *          The key the definition is stored under.
   * @param id
   *          The ID that key folds to.
   * @return {@code true} if the definition may be loaded under that key.
   */
  private boolean isLoadable(final byte[] idBytes, final int id)
  {
    return id >= 0 && id <= MAX_LOAD_ID && Arrays.equals(idBytes, encodeId(id));
  }

  /**
   * Holds the ID of a definition that was skipped, so that no registration hands it out again.
   * <p>
   * A key mangled from the one it was written under folds to the ID that key addressed, and the
   * entries carrying that ID are still in the backend. Were the ID left out of the decode map, the
   * next registration would take it - a registration takes the size of the map - and those entries
   * would decode as whatever it stored, silently. Held as the gap it is, they are reported as the
   * unknown token they now carry instead, which is what the decode path does with a gap.
   * <p>
   * An ID that no registration can reach is not held: a negative one is no index at all, and one
   * past {@link #MAX_LOAD_ID} is one {@link #registerAttribute} refuses to allocate, so neither can
   * be handed out to begin with and padding a map up to either is the cost this ceiling exists to
   * refuse.
   *
   * @param decodeMap
   *          The decode map the skipped definition would have gone into.
   * @param id
   *          The ID its key folds to.
   */
  /**
   * Returns the ID a registration is about to allocate, having checked that a load would take it
   * back. An ID past {@link #MAX_LOAD_ID} is refused rather than handed out: an entry written under
   * a token the next open skips cannot be decoded once the server is restarted, which is the same
   * reason a registration whose store failed is withdrawn rather than published.
   * <p>
   * Only reachable where a stored definition named an ID near the ceiling - no registration walks
   * there on its own, since every ID it hands out is the size of a map it grows one element at a
   * time - so this reports a storage to be exported and imported again rather than a limit a
   * running server is expected to meet.
   *
   * @param id
   *          The ID the registration would allocate.
   * @return That ID.
   * @throws DirectoryException
   *           If no load would take a definition back under it.
   */
  private static int allocatable(final int id) throws DirectoryException
  {
    if (id > MAX_LOAD_ID)
    {
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(),
          ERR_COMPRESSEDSCHEMA_NO_TOKEN_LEFT.get(id, MAX_LOAD_ID));
    }
    return id;
  }

  private <T> void reserve(final List<T> decodeMap, final int id)
  {
    if (id < 0 || id > MAX_LOAD_ID)
    {
      return;
    }
    exclusiveLock.lock();
    try
    {
      padTo(decodeMap, id + 1);
    }
    finally
    {
      exclusiveLock.unlock();
    }
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
    final AttributeType type = serverContext.getSchema().getAttributeType(attributeName);
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
        // Grow the decode array, in one pass rather than a slot at a time.
        padTo(mappings.adDecodeMap, id);
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
   * @return The object class set, or {@code null} if the definition was skipped because the key it
   *         is stored under is not one a compressed schema hands out.
   */
  protected final Map<ObjectClass, String> loadObjectClasses(
      final byte[] encodedObjectClasses,
      final Collection<String> objectClassNames)
  {
    final int id = decodeId(encodedObjectClasses);
    if (!isLoadable(encodedObjectClasses, id))
    {
      logger.error(ERR_COMPRESSEDSCHEMA_UNUSABLE_OC_TOKEN, objectClassNames,
          tokenInMessage(encodedObjectClasses, id));
      reserve(getMappings().ocDecodeMap, id);
      return null;
    }
    return loadObjectClassesToMaps(id, objectClassNames, mappings);
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
  private Map<ObjectClass, String> loadObjectClassesToMaps(int id, final Collection<String> objectClassNames,
      Mappings mappings)
  {
    final Map<ObjectClass, String> ocMap = objectClassMap(serverContext.getSchema(), objectClassNames);
    exclusiveLock.lock();
    try
    {
      updateObjectClassesMaps(id, mappings, ocMap);
    }
    finally
    {
      exclusiveLock.unlock();
    }
    return ocMap;
  }

  private void updateObjectClassesMaps(int id, Mappings mappings, Map<ObjectClass, String> ocMap)
  {
    mappings.ocEncodeMap.put(ocMap, id);
    if (id < mappings.ocDecodeMap.size())
    {
      mappings.ocDecodeMap.set(id, ocMap);
    }
    else
    {
      // Grow the decode array, in one pass rather than a slot at a time.
      padTo(mappings.ocDecodeMap, id);
      mappings.ocDecodeMap.add(ocMap);
    }
  }

  /**
   * Pads a decode map with the slots of the IDs it holds no definition for, up to the provided
   * size, and leaves a map already that long alone.
   * <p>
   * In one pass, because a decode map is a {@link CopyOnWriteArrayList}: appending the slots one at
   * a time copies the whole backing array per slot, so padding a map to an ID costs the square of
   * it. That is paid under {@link #exclusiveLock}, and for the pluggable backends inside the write
   * transaction the root container opens in, where nothing says what the open is waiting for - and
   * the IDs are read out of a storage, so how far a map is padded is not this server's to choose.
   *
   * @param decodeMap
   *          The decode map to pad.
   * @param size
   *          The size to pad it to.
   */
  private static <T> void padTo(final List<T> decodeMap, final int size)
  {
    final int missing = size - decodeMap.size();
    if (missing > 0)
    {
      decodeMap.addAll(Collections.<T> nCopies(missing, null));
    }
  }

  /**
   * Persists the provided encoded attribute. The default implementation is to
   * do nothing. Calls to this method are synchronized, so implementations can
   * assume that this method is not being called by other threads. Note that
   * this method is not thread-safe with respect to
   * {@link #storeObjectClasses(byte[], Collection)}.
   * <p>
   * Called with the exclusive lock of this compressed schema held, and that lock is reentrant, so
   * an implementation must re-enter neither the encode, the load nor the decode path of the
   * compressed schema it belongs to. The registration being persisted has already been appended
   * to the decode map - so that an implementation persisting the whole content rather than the
   * element it is handed, as {@code DefaultCompressedSchema} does, has it - and is withdrawn from
   * there if this method throws. Encoding or loading appends to the same decode map, and the
   * withdrawal would take back whatever was appended last; decoding rebuilds the mappings when the
   * schema has changed, and the withdrawal would then have to take the element out of a map that
   * has already been replaced. Neither is withdrawn: the violation is reported and the
   * registration is left behind, holding an id no definition was persisted under.
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
   * <p>
   * Called with the exclusive lock of this compressed schema held, and that lock is reentrant, so
   * an implementation must re-enter neither the encode, the load nor the decode path of the
   * compressed schema it belongs to. The registration being persisted has already been appended
   * to the decode map - so that an implementation persisting the whole content rather than the
   * element it is handed, as {@code DefaultCompressedSchema} does, has it - and is withdrawn from
   * there if this method throws. Encoding or loading appends to the same decode map, and the
   * withdrawal would take back whatever was appended last; decoding rebuilds the mappings when the
   * schema has changed, and the withdrawal would then have to take the element out of a map that
   * has already been replaced. Neither is withdrawn: the violation is reported and the
   * registration is left behind, holding an id no definition was persisted under.
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

  /**
   * Reads the encoded schema element ID at the current position, reporting a record the ID cannot
   * be read from rather than letting the read out of the decode path as an unchecked exception -
   * for the same reason the lookup the ID feeds does not: the callers of a {@code @PublicAPI}
   * decode path are written for {@link DirectoryException}.
   *
   * @param reader
   *          The byte string reader positioned on an encoded schema element ID.
   * @param unreadableToken
   *          The message reporting a token this decode path cannot read.
   * @return The encoded schema element ID, as the storage holds it.
   * @throws DirectoryException
   *           If the record holds no readable schema element ID at the current position.
   */
  private static byte[] readIdBytes(final ByteSequenceReader reader,
      final LocalizableMessageDescriptor.Arg1<Object> unreadableToken) throws DirectoryException
  {
    final int length;
    try
    {
      length = reader.readBERLength();
    }
    catch (final IndexOutOfBoundsException e)
    {
      // Both of the conditions readBERLength() reports this way: the record ends inside the
      // length itself, and a length header naming more than the four bytes a length is written in.
      throw unreadable(unreadableToken,
          "the record ends inside the length of the token, or that length names more than four bytes", e);
    }
    if (length < 0 || length > MAX_ID_BYTES)
    {
      // The length is composed from up to four bytes unsigned, so a corrupt record can name a
      // negative count - 0xFFFFFFFF - or more bytes than an id is ever written in. The upper
      // bound is what encodeId() emits, and it is not only a sanity check: decodeId() folds
      // whatever it is handed, so a token padded with leading zeros decodes to the id its
      // canonical token addresses, and a record carrying one would read as a live definition
      // instead of being reported. Checked before the array is allocated, too - a length of
      // 0x7FFFFFFF is a two gigabyte allocation no reader of a corrupt record should attempt.
      throw unreadable(unreadableToken, "the token names " + (length & 0xFFFFFFFFL)
          + " bytes, and an id is never encoded in more than " + MAX_ID_BYTES, null);
    }
    if (length > reader.remaining())
    {
      throw unreadable(unreadableToken,
          "the token names " + length + " bytes and the record holds " + reader.remaining(), null);
    }
    final byte[] idBytes = new byte[length];
    reader.readBytes(idBytes);
    return idBytes;
  }

  /**
   * Returns the exception reporting a token this decode path cannot read, tracing what the read
   * raised where it raised anything: the callers of this path convert an exception of their own
   * into a message, and where the record went wrong is worth keeping for a corrupt store.
   */
  private static DirectoryException unreadable(
      final LocalizableMessageDescriptor.Arg1<Object> unreadableToken, final String reason,
      final RuntimeException cause)
  {
    if (cause != null)
    {
      logger.traceException(cause);
    }
    return new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(),
        unreadableToken.get(reason), cause);
  }

  /**
   * Names a token in a message as the storage holds it - the key a definition is written under -
   * together with the id it decodes to. The id on its own is one less than what was read, so a
   * token that no definition was ever written under is reported as a value appearing nowhere in
   * the stored data: an all-zero token reads as the id -1.
   *
   * @param idBytes
   *          The encoded schema element ID, as it was read.
   * @param id
   *          The schema element ID it decoded to.
   * @return The token as a message should name it.
   */
  private static String tokenInMessage(final byte[] idBytes, final int id)
  {
    return "0x" + bytesToHexNoSpace(idBytes) + " (id " + id + ")";
  }

  /**
   * Encodes the provided schema element ID, in as few bytes as it fits in - which is what
   * {@link #isLoadable(byte[], int)} holds a stored key to, so nothing else may encode one.
   *
   * @param id
   *          The schema element ID.
   * @return The encoded schema element ID.
   */
  @VisibleForTesting
  static byte[] encodeId(final int id)
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
