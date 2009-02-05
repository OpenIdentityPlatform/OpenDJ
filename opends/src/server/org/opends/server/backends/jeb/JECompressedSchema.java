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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;



import static org.opends.messages.JebMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.IOException;

import org.opends.messages.Message;
import org.opends.server.api.CompressedSchema;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.*;
import org.opends.server.types.*;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;



/**
 * This class provides a compressed schema implementation whose definitions are
 * stored in a Berkeley DB JE database.
 */
public final class JECompressedSchema
       extends CompressedSchema
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * The name of the database used to store compressed attribute description
   * definitions.
   */
  public static final String DB_NAME_AD = "compressed_attributes";



  /**
   * The name of the database used to store compressed object class set
   * definitions.
   */
  public static final String DB_NAME_OC = "compressed_object_classes";



  // The counter used for attribute descriptions.
  private AtomicInteger adCounter;

  // The counter used for object class sets.
  private AtomicInteger ocCounter;

  // The map between encoded representations and attribute types.
  private ConcurrentHashMap<ByteSequence,AttributeType> atDecodeMap;

  // The map between encoded representations and attribute options.
  private ConcurrentHashMap<ByteSequence,Set<String>> aoDecodeMap;

  // The map between encoded representations and object class sets.
  private ConcurrentHashMap<ByteSequence,Map<ObjectClass,String>> ocDecodeMap;

  // The map between attribute descriptions and their encoded
  // representations.
  private final ConcurrentHashMap<AttributeType,
                ConcurrentHashMap<Set<String>, ByteSequence>> adEncodeMap;

  // The map between object class sets and encoded representations.
  private final ConcurrentHashMap<Map<ObjectClass,String>,
      ByteSequence> ocEncodeMap;

  // The compressed attribute description schema database.
  private Database adDatabase;

  // The compresesd object class set schema database.
  private Database ocDatabase;

  // The environment in which the databases are held.
  private Environment environment;

  private final ByteStringBuilder storeWriterBuffer;
  private final ASN1Writer storeWriter;



  /**
   * Creates a new instance of this JE compressed schema manager.
   *
   * @param  environment  A reference to the database environment in which the
   *                      databases will be held.
   *
   * @throws  DatabaseException  If a problem occurs while loading the
   *                             compressed schema definitions from the
   *                             database.
   */
  public JECompressedSchema(Environment environment)
         throws DatabaseException
  {
    this.environment = environment;

    atDecodeMap = new ConcurrentHashMap<ByteSequence,AttributeType>();
    aoDecodeMap = new ConcurrentHashMap<ByteSequence,Set<String>>();
    ocDecodeMap = new ConcurrentHashMap<ByteSequence,Map<ObjectClass,String>>();
    adEncodeMap =
         new ConcurrentHashMap<AttributeType,
                  ConcurrentHashMap<Set<String>, ByteSequence>>();
    ocEncodeMap = new ConcurrentHashMap<Map<ObjectClass,String>,
        ByteSequence>();

    adCounter = new AtomicInteger(1);
    ocCounter = new AtomicInteger(1);

    storeWriterBuffer = new ByteStringBuilder();
    storeWriter = ASN1.getWriter(storeWriterBuffer);

    load();
  }



  /**
   * Loads the compressed schema information from the database.
   *
   * @throws  DatabaseException  If a problem occurs while loading the
   *                             definitions from the database.
   */
  private void load()
          throws DatabaseException
  {
    DatabaseConfig dbConfig = new DatabaseConfig();

    if(environment.getConfig().getReadOnly())
    {
      dbConfig.setReadOnly(true);
      dbConfig.setAllowCreate(false);
      dbConfig.setTransactional(false);
    }
    else if(!environment.getConfig().getTransactional())
    {
      dbConfig.setAllowCreate(true);
      dbConfig.setTransactional(false);
      dbConfig.setDeferredWrite(true);
    }
    else
    {
      dbConfig.setAllowCreate(true);
      dbConfig.setTransactional(true);
    }

    adDatabase = environment.openDatabase(null, DB_NAME_AD, dbConfig);
    ocDatabase = environment.openDatabase(null, DB_NAME_OC, dbConfig);

    // Cursor through the object class database and load the object class set
    // definitions.  At the same time, figure out the highest token value and
    // initialize the object class counter to one greater than that.
    Cursor ocCursor = ocDatabase.openCursor(null, null);
    int highestToken = 0;

    try
    {
      DatabaseEntry keyEntry   = new DatabaseEntry();
      DatabaseEntry valueEntry = new DatabaseEntry();
      OperationStatus status = ocCursor.getFirst(keyEntry, valueEntry,
                                                 LockMode.READ_UNCOMMITTED);
      while (status == OperationStatus.SUCCESS)
      {
        byte[] tokenBytes = keyEntry.getData();
        ByteString token = ByteString.wrap(tokenBytes);
        highestToken = Math.max(highestToken, decodeInt(tokenBytes));

        ASN1Reader reader =
            ASN1.getReader(valueEntry.getData());
        reader.readStartSequence();
        LinkedHashMap<ObjectClass,String> ocMap =
             new LinkedHashMap<ObjectClass,String>();
        while(reader.hasNextElement())
        {
          String ocName = reader.readOctetStringAsString();
          String lowerName = toLowerCase(ocName);
          ObjectClass oc = DirectoryServer.getObjectClass(lowerName, true);
          ocMap.put(oc, ocName);
        }
        reader.readEndSequence();

        ocEncodeMap.put(ocMap, token);
        ocDecodeMap.put(token, ocMap);

        status = ocCursor.getNext(keyEntry, valueEntry,
                                  LockMode.READ_UNCOMMITTED);
      }
    }
    catch (ASN1Exception ae)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ae);
      }

      Message m =
           ERR_JEB_COMPSCHEMA_CANNOT_DECODE_OC_TOKEN.get(ae.getMessage());
      throw new DatabaseException(m.toString(), ae);
    }
    finally
    {
      ocCursor.close();
    }

    ocCounter.set(highestToken+1);


    // Cursor through the attribute description database and load the attribute
    // set definitions.
    Cursor adCursor = adDatabase.openCursor(null, null);
    highestToken = 0;

    try
    {
      DatabaseEntry keyEntry   = new DatabaseEntry();
      DatabaseEntry valueEntry = new DatabaseEntry();
      OperationStatus status = adCursor.getFirst(keyEntry, valueEntry,
                                                 LockMode.READ_UNCOMMITTED);
      while (status == OperationStatus.SUCCESS)
      {
        byte[] tokenBytes = keyEntry.getData();
        ByteString token = ByteString.wrap(tokenBytes);
        highestToken = Math.max(highestToken, decodeInt(tokenBytes));

        ASN1Reader reader =
            ASN1.getReader(valueEntry.getData());
        reader.readStartSequence();
        String attrName = reader.readOctetStringAsString();
        String lowerName = toLowerCase(attrName);
        AttributeType attrType =
            DirectoryServer.getAttributeType(lowerName, true);

        LinkedHashSet<String> options =
            new LinkedHashSet<String>();
        while(reader.hasNextElement())
        {
          options.add(reader.readOctetStringAsString());
        }
        reader.readEndSequence();

        atDecodeMap.put(token, attrType);
        aoDecodeMap.put(token, options);

        ConcurrentHashMap<Set<String>, ByteSequence> map = adEncodeMap
            .get(attrType);
        if (map == null)
        {
          map = new ConcurrentHashMap<Set<String>, ByteSequence>(1);
          map.put(options, token);
          adEncodeMap.put(attrType, map);
        }
        else
        {
          map.put(options, token);
        }

        status = adCursor.getNext(keyEntry, valueEntry,
                                  LockMode.READ_UNCOMMITTED);
      }
    }
    catch (ASN1Exception ae)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ae);
      }

      Message m =
           ERR_JEB_COMPSCHEMA_CANNOT_DECODE_AD_TOKEN.get(ae.getMessage());
      throw new DatabaseException(m.toString(), ae);
    }
    finally
    {
      adCursor.close();
    }

    adCounter.set(highestToken+1);
  }



  /**
   * Closes the databases and releases any resources held by this compressed
   * schema manager.
   */
  public void close()
  {
    try
    {
      adDatabase.sync();
    } catch (Exception e) {}

    try
    {
      adDatabase.close();
    } catch (Exception e) {}

    try
    {
      ocDatabase.sync();
    } catch (Exception e) {}

    try
    {
      ocDatabase.close();
    } catch (Exception e) {}

    adDatabase  = null;
    ocDatabase  = null;
    environment = null;
    atDecodeMap = null;
    aoDecodeMap = null;
    ocDecodeMap = null;
    //adEncodeMap = null;
    //ocEncodeMap = null;
    adCounter   = null;
    ocCounter   = null;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void encodeObjectClasses(ByteStringBuilder entryBuffer,
                                  Map<ObjectClass,String> objectClasses)
         throws DirectoryException
  {
    ByteSequence encodedClasses = ocEncodeMap.get(objectClasses);
    if (encodedClasses == null)
    {
      synchronized (ocEncodeMap)
      {
        int setValue = ocCounter.getAndIncrement();
        byte[] tokenArray = encodeInt(setValue);
        encodedClasses = ByteString.wrap(tokenArray);

        storeObjectClass(tokenArray, objectClasses);
        ocEncodeMap.put(objectClasses, encodedClasses);
        ocDecodeMap.put(encodedClasses, objectClasses);
      }
    }

    entryBuffer.appendBERLength(encodedClasses.length());
    encodedClasses.copyTo(entryBuffer);
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public Map<ObjectClass,String> decodeObjectClasses(
      ByteSequenceReader entryBufferReader) throws DirectoryException
  {
    int tokenLength = entryBufferReader.getBERLength();
    ByteSequence byteArray = entryBufferReader.getByteSequence(tokenLength);
    Map<ObjectClass,String> ocMap = ocDecodeMap.get(byteArray);
    if (ocMap == null)
    {
      Message message = ERR_JEB_COMPSCHEMA_UNKNOWN_OC_TOKEN.get(byteArray
          .toByteString().toHex());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          message);
    }
    else
    {
      return ocMap;
    }
  }

  private void storeObjectClass(byte[] token,
                                Map<ObjectClass,String> objectClasses)
      throws DirectoryException
  {
    synchronized(storeWriter)
    {
      try
      {
        storeWriterBuffer.clear();
        storeWriter.writeStartSequence();
        for (String ocName : objectClasses.values())
        {
          storeWriter.writeOctetString(ocName);
        }
        storeWriter.writeEndSequence();
        store(ocDatabase, token, storeWriterBuffer);
      }
      catch(IOException ioe)
      {
        // TODO: Shouldn't happen but should log a message
      }
    }
  }

  private void storeAttribute(byte[] token,
                              AttributeType attrType, Set<String> options)
      throws DirectoryException
  {
    synchronized(storeWriter)
    {
      try
      {
        storeWriterBuffer.clear();
        storeWriter.writeStartSequence();
        storeWriter.writeOctetString(attrType.getNameOrOID());
        for (String option : options)
        {
          storeWriter.writeOctetString(option);
        }
        storeWriter.writeEndSequence();
        store(adDatabase, token, storeWriterBuffer);
      }
      catch(IOException ioe)
      {
        // TODO: Shouldn't happen but should log a message
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public void encodeAttribute(ByteStringBuilder entryBuffer,
                              Attribute attribute) throws DirectoryException
  {
    AttributeType type = attribute.getAttributeType();
    Set<String> options = attribute.getOptions();

    ConcurrentHashMap<Set<String>, ByteSequence> map = adEncodeMap.get(type);
    if (map == null)
    {
      byte[] tokenArray;
      ByteString byteString;
      synchronized (adEncodeMap)
      {
        map = new ConcurrentHashMap<Set<String>, ByteSequence>(1);

        int intValue = adCounter.getAndIncrement();
        tokenArray = encodeInt(intValue);
        byteString = ByteString.wrap(tokenArray);
        map.put(options,byteString);

        storeAttribute(tokenArray, type, options);
        adEncodeMap.put(type, map);
        atDecodeMap.put(byteString, type);
        aoDecodeMap.put(byteString, options);
      }

      encodeAttribute(entryBuffer, byteString, attribute);
    }
    else
    {
      ByteSequence byteArray = map.get(options);
      if (byteArray == null)
      {
        byte[] tokenArray;
        synchronized (map)
        {
          int intValue = adCounter.getAndIncrement();
          tokenArray = encodeInt(intValue);
          byteArray = ByteString.wrap(tokenArray);
          map.put(options,byteArray);

          storeAttribute(tokenArray, type, options);
          atDecodeMap.put(byteArray, type);
          aoDecodeMap.put(byteArray, options);
        }
      }

      encodeAttribute(entryBuffer, byteArray, attribute);
    }
  }



  /**
   * Encodes the information in the provided attribute to a byte
   * array.
   *
   * @param  buffer     The byte buffer to encode the attribute into.
   * @param  adArray    The byte array that is a placeholder for the
   *                    attribute type and set of options.
   * @param  attribute  The attribute to be encoded.
   */
  private void encodeAttribute(ByteStringBuilder buffer, ByteSequence adArray,
                                 Attribute attribute)
  {
    // Write the length of the adArray followed by the adArray.
    buffer.appendBERLength(adArray.length());
    adArray.copyTo(buffer);

    // Write the number of attributes
    buffer.appendBERLength(attribute.size());

    // Write the attribute values as length / value pairs
    for(AttributeValue v : attribute)
    {
      buffer.appendBERLength(v.getValue().length());
      buffer.append(v.getValue());
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public Attribute decodeAttribute(ByteSequenceReader entryBufferReader)
         throws DirectoryException
  {
    // Figure out how many bytes are in the token that is the placeholder for
    // the attribute description.
    int adArrayLength = entryBufferReader.getBERLength();


    // Get the attribute description token and make sure it resolves to an
    // attribute type and option set.
    ByteSequence adArray = entryBufferReader.getByteSequence(adArrayLength);

    AttributeType attrType = atDecodeMap.get(adArray);
    Set<String> options = aoDecodeMap.get(adArray);
    if ((attrType == null) || (options == null))
    {
      Message message = ERR_JEB_COMPSCHEMA_UNRECOGNIZED_AD_TOKEN.get(adArray
          .toByteString().toHex());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          message);
    }


    // Determine the number of values for the attribute.
    int numValues = entryBufferReader.getBERLength();


    // For the common case of a single value with no options, generate
    // less garbage.
    if (numValues == 1 && options.isEmpty())
    {
      int valueLength = entryBufferReader.getBERLength();

      ByteString valueBytes =
          entryBufferReader.getByteSequence(valueLength).toByteString();
      return Attributes.create(attrType,
          AttributeValues.create(attrType, valueBytes));
    }
    else
    {
      // Read the appropriate number of values.
      AttributeBuilder builder = new AttributeBuilder(attrType);
      builder.setOptions(options);
      builder.setInitialCapacity(numValues);
      for (int i = 0; i < numValues; i++)
      {
        int valueLength = entryBufferReader.getBERLength();

        ByteString valueBytes =
            entryBufferReader.getByteSequence(valueLength).toByteString();
        builder.add(AttributeValues.create(attrType,
            valueBytes));
      }

      return builder.toAttribute();
    }
  }

  /**
   * Stores the provided key-value pair in the specified database container.
   *
   * @param  database    The database in which to store the information.
   * @param  keyBytes    The byte array containing the key to store.
   * @param  valueBytes  The byte array containing the value to store.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to store
   *                              the data.
   */
  private void store(Database database, byte[] keyBytes,
                     ByteStringBuilder valueBytes)
          throws DirectoryException
  {
    boolean successful = false;
    DatabaseEntry keyEntry   = new DatabaseEntry(keyBytes);
    DatabaseEntry valueEntry = new DatabaseEntry(valueBytes.getBackingArray(),
        0, valueBytes.length());

    for (int i=0; i < 3; i++)
    {
      try
      {
        OperationStatus status = database.putNoOverwrite(null, keyEntry,
                                                         valueEntry);
        if (status == OperationStatus.SUCCESS)
        {
          successful = true;
          break;
        }
        else
        {
          Message m = ERR_JEB_COMPSCHEMA_CANNOT_STORE_STATUS.get(
                           status.toString());
          throw new DirectoryException(
                         DirectoryServer.getServerErrorResultCode(), m);
        }
      }
      catch (DeadlockException de)
      {
        continue;
      }
      catch (DatabaseException de)
      {
        Message m = ERR_JEB_COMPSCHEMA_CANNOT_STORE_EX.get(de.getMessage());
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     m, de);
      }
    }

    if (! successful)
    {
      Message m = ERR_JEB_COMPSCHEMA_CANNOT_STORE_MULTIPLE_FAILURES.get();
      throw new DirectoryException(
                     DirectoryServer.getServerErrorResultCode(), m);
    }
  }




  /**
   * Encodes the provided int value to a byte array.
   *
   * @param  intValue  The int value to be encoded.
   *
   * @return  The byte array containing the encoded int value.
   */
  private byte[] encodeInt(int intValue)
  {
    byte[] array;
    if (intValue <= 0xFF)
    {
      array = new byte[1];
      array[0] = (byte) (intValue & 0xFF);
    }
    else if (intValue <= 0xFFFF)
    {
      array = new byte[2];
      array[0] = (byte) ((intValue >> 8)  & 0xFF);
      array[1] = (byte) (intValue & 0xFF);
    }
    else if (intValue <= 0xFFFFFF)
    {
      array = new byte[3];
      array[0] = (byte) ((intValue >> 16) & 0xFF);
      array[1] = (byte) ((intValue >> 8)  & 0xFF);
      array[2] = (byte) (intValue & 0xFF);
    }
    else
    {
      array = new byte[4];
      array[0] = (byte) ((intValue >> 24) & 0xFF);
      array[1] = (byte) ((intValue >> 16) & 0xFF);
      array[2] = (byte) ((intValue >> 8)  & 0xFF);
      array[3] = (byte) (intValue & 0xFF);
    }

    return array;
  }



  /**
   * Decodes the contents of the provided byte array as an int.
   *
   * @param  byteArray  The byte array containing the data to decode.
   *
   * @return  The decoded int value.
   */
  private int decodeInt(byte[] byteArray)
  {
    int intValue = 0;

    for (byte b : byteArray)
    {
      intValue <<= 8;
      intValue |= (b & 0xFF);
    }

    return intValue;
  }
}

