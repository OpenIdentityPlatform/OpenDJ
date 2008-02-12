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



import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

import org.opends.messages.Message;
import org.opends.server.api.CompressedSchema;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteArray;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ObjectClass;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.messages.JebMessages.*;
import static org.opends.server.util.StaticUtils.*;



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
  private ConcurrentHashMap<ByteArray,AttributeType> atDecodeMap;

  // The map between encoded representations and attribute options.
  private ConcurrentHashMap<ByteArray,LinkedHashSet<String>> aoDecodeMap;

  // The map between encoded representations and object class sets.
  private ConcurrentHashMap<ByteArray,Map<ObjectClass,String>> ocDecodeMap;

  // The map between attribute descriptions and their encoded
  // representations.
  private ConcurrentHashMap<AttributeType,
               ConcurrentHashMap<LinkedHashSet<String>,ByteArray>> adEncodeMap;

  // The map between object class sets and encoded representations.
  private ConcurrentHashMap<Map<ObjectClass,String>,ByteArray> ocEncodeMap;

  // The compressed attribute description schema database.
  private Database adDatabase;

  // The compresesd object class set schema database.
  private Database ocDatabase;

  // The environment in which the databases are held.
  private Environment environment;



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

    atDecodeMap = new ConcurrentHashMap<ByteArray,AttributeType>();
    aoDecodeMap = new ConcurrentHashMap<ByteArray,LinkedHashSet<String>>();
    ocDecodeMap = new ConcurrentHashMap<ByteArray,Map<ObjectClass,String>>();
    adEncodeMap =
         new ConcurrentHashMap<AttributeType,
                  ConcurrentHashMap<LinkedHashSet<String>,ByteArray>>();
    ocEncodeMap = new ConcurrentHashMap<Map<ObjectClass,String>,ByteArray>();

    adCounter = new AtomicInteger(1);
    ocCounter = new AtomicInteger(1);

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
        ByteArray token = new ByteArray(keyEntry.getData());
        highestToken = Math.max(highestToken, decodeInt(token.array()));

        ArrayList<ASN1Element> elements =
             ASN1Sequence.decodeAsSequence(valueEntry.getData()).elements();
        LinkedHashMap<ObjectClass,String> ocMap =
             new LinkedHashMap<ObjectClass,String>(elements.size());
        for (int i=0; i < elements.size(); i++)
        {
          ASN1OctetString os = elements.get(i).decodeAsOctetString();
          String ocName = os.stringValue();
          String lowerName = toLowerCase(ocName);
          ObjectClass oc = DirectoryServer.getObjectClass(lowerName, true);
          ocMap.put(oc, ocName);
        }

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
        ByteArray token = new ByteArray(keyEntry.getData());
        highestToken = Math.max(highestToken, decodeInt(token.array()));

        ArrayList<ASN1Element> elements =
             ASN1Sequence.decodeAsSequence(valueEntry.getData()).elements();

        ASN1OctetString os = elements.get(0).decodeAsOctetString();
        String attrName = os.stringValue();
        String lowerName = toLowerCase(attrName);
        AttributeType attrType =
             DirectoryServer.getAttributeType(lowerName, true);

        LinkedHashSet<String> options =
             new LinkedHashSet<String>(elements.size()-1);
        for (int i=1; i < elements.size(); i++)
        {
          os = elements.get(i).decodeAsOctetString();
          options.add(os.stringValue());
        }

        atDecodeMap.put(token, attrType);
        aoDecodeMap.put(token, options);

        ConcurrentHashMap<LinkedHashSet<String>,ByteArray> map =
             adEncodeMap.get(attrType);
        if (map == null)
        {
          map = new ConcurrentHashMap<LinkedHashSet<String>,ByteArray>(1);
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
    adEncodeMap = null;
    ocEncodeMap = null;
    adCounter   = null;
    ocCounter   = null;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public byte[] encodeObjectClasses(Map<ObjectClass,String> objectClasses)
         throws DirectoryException
  {
    ByteArray encodedClasses = ocEncodeMap.get(objectClasses);
    if (encodedClasses == null)
    {
      synchronized (ocEncodeMap)
      {
        int setValue = ocCounter.getAndIncrement();
        byte[] tokenArray = encodeInt(setValue);

        ArrayList<ASN1Element> elements =
             new ArrayList<ASN1Element>(objectClasses.size());
        for (String ocName : objectClasses.values())
        {
          elements.add(new ASN1OctetString(ocName));
        }

        byte[] encodedOCs = new ASN1Sequence(elements).encode();
        store(ocDatabase, tokenArray, encodedOCs);

        encodedClasses = new ByteArray(tokenArray);
        ocEncodeMap.put(objectClasses, encodedClasses);
        ocDecodeMap.put(encodedClasses, objectClasses);

        return tokenArray;
      }
    }
    else
    {
      return encodedClasses.array();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public Map<ObjectClass,String> decodeObjectClasses(
                                      byte[] encodedObjectClasses)
         throws DirectoryException
  {
    ByteArray byteArray = new ByteArray(encodedObjectClasses);
    Map<ObjectClass,String> ocMap = ocDecodeMap.get(byteArray);
    if (ocMap == null)
    {
      Message message = ERR_JEB_COMPSCHEMA_UNKNOWN_OC_TOKEN.get(
                             bytesToHex(encodedObjectClasses));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }
    else
    {
      return ocMap;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public byte[] encodeAttribute(Attribute attribute)
         throws DirectoryException
  {
    AttributeType type = attribute.getAttributeType();
    LinkedHashSet<String> options = attribute.getOptions();

    ConcurrentHashMap<LinkedHashSet<String>,ByteArray> map =
         adEncodeMap.get(type);
    if (map == null)
    {
      byte[] tokenArray;
      synchronized (adEncodeMap)
      {
        map = new ConcurrentHashMap<LinkedHashSet<String>,ByteArray>(1);

        int intValue = adCounter.getAndIncrement();
        tokenArray = encodeInt(intValue);
        ByteArray byteArray = new ByteArray(tokenArray);
        map.put(options,byteArray);

        ArrayList<ASN1Element> elements =
             new ArrayList<ASN1Element>(options.size()+1);
        elements.add(new ASN1OctetString(attribute.getName()));
        for (String option : options)
        {
          elements.add(new ASN1OctetString(option));
        }
        byte[] encodedValue = new ASN1Sequence(elements).encode();
        store(adDatabase, tokenArray, encodedValue);

        adEncodeMap.put(type, map);
        atDecodeMap.put(byteArray, type);
        aoDecodeMap.put(byteArray, options);
      }

      return encodeAttribute(tokenArray, attribute);
    }
    else
    {
      ByteArray byteArray = map.get(options);
      if (byteArray == null)
      {
        byte[] tokenArray;
        synchronized (map)
        {
          int intValue = adCounter.getAndIncrement();
          tokenArray = encodeInt(intValue);
          byteArray = new ByteArray(tokenArray);
          map.put(options,byteArray);

          ArrayList<ASN1Element> elements =
               new ArrayList<ASN1Element>(options.size()+1);
          elements.add(new ASN1OctetString(attribute.getName()));
          for (String option : options)
          {
            elements.add(new ASN1OctetString(option));
          }
          byte[] encodedValue = new ASN1Sequence(elements).encode();
          store(adDatabase, tokenArray, encodedValue);

          atDecodeMap.put(byteArray, type);
          aoDecodeMap.put(byteArray, options);
        }

        return encodeAttribute(tokenArray, attribute);
      }
      else
      {
        return encodeAttribute(byteArray.array(), attribute);
      }
    }
  }



  /**
   * Encodes the information in the provided attribute to a byte
   * array.
   *
   * @param  adArray    The byte array that is a placeholder for the
   *                    attribute type and set of options.
   * @param  attribute  The attribute to be encoded.
   *
   * @return  An encoded representation of the provided attribute.
   */
  private byte[] encodeAttribute(byte[] adArray, Attribute attribute)
  {
    LinkedHashSet<AttributeValue> values = attribute.getValues();
    int totalValuesLength = 0;
    byte[][] subArrays = new  byte[values.size()*2][];
    int pos = 0;
    for (AttributeValue v : values)
    {
      byte[] vBytes = v.getValueBytes();
      byte[] lBytes = ASN1Element.encodeLength(vBytes.length);

      subArrays[pos++] = lBytes;
      subArrays[pos++] = vBytes;

      totalValuesLength += lBytes.length + vBytes.length;
    }

    byte[] adArrayLength = ASN1Element.encodeLength(adArray.length);
    byte[] countBytes = ASN1Element.encodeLength(values.size());
    int totalLength = adArrayLength.length + adArray.length +
                      countBytes.length + totalValuesLength;
    byte[] array = new byte[totalLength];

    System.arraycopy(adArrayLength, 0, array, 0,
                     adArrayLength.length);
    pos = adArrayLength.length;
    System.arraycopy(adArray, 0, array, pos, adArray.length);
    pos += adArray.length;
    System.arraycopy(countBytes, 0, array, pos, countBytes.length);
    pos += countBytes.length;

    for (int i=0; i < subArrays.length; i++)
    {
      System.arraycopy(subArrays[i], 0, array, pos,
                       subArrays[i].length);
      pos += subArrays[i].length;
    }

    return array;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public Attribute decodeAttribute(byte[] encodedEntry, int startPos,
                                   int length)
         throws DirectoryException
  {
    // Figure out how many bytes are in the token that is the placeholder for
    // the attribute description.
    int pos = startPos;
    int adArrayLength = encodedEntry[pos] & 0x7F;
    if (adArrayLength != encodedEntry[pos++])
    {
      int numLengthBytes = adArrayLength;
      adArrayLength = 0;
      for (int i=0; i < numLengthBytes; i++, pos++)
      {
        adArrayLength = (adArrayLength << 8) | (encodedEntry[pos] & 0xFF);
      }
    }


    // Get the attribute description token and make sure it resolves to an
    // attribute type and option set.
    ByteArray adArray = new ByteArray(new byte[adArrayLength]);
    System.arraycopy(encodedEntry, pos, adArray.array(), 0, adArrayLength);
    pos += adArrayLength;
    AttributeType attrType = atDecodeMap.get(adArray);
    LinkedHashSet<String> options = aoDecodeMap.get(adArray);
    if ((attrType == null) || (options == null))
    {
      Message message = ERR_JEB_COMPSCHEMA_UNRECOGNIZED_AD_TOKEN.get(
                             bytesToHex(adArray.array()));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }


    // Determine the number of values for the attribute.
    int numValues = encodedEntry[pos] & 0x7F;
    if (numValues != encodedEntry[pos++])
    {
      int numValuesBytes = numValues;
      numValues = 0;
      for (int i=0; i < numValuesBytes; i++, pos++)
      {
        numValues = (numValues << 8) | (encodedEntry[pos] & 0xFF);
      }
    }


    // Read the appropriate number of values.
    LinkedHashSet<AttributeValue> values =
         new LinkedHashSet<AttributeValue>(numValues);
    for (int i=0; i < numValues; i++)
    {
      int valueLength = encodedEntry[pos] & 0x7F;
      if (valueLength != encodedEntry[pos++])
      {
        int valueLengthBytes = valueLength;
        valueLength = 0;
        for (int j=0; j < valueLengthBytes; j++, pos++)
        {
          valueLength = (valueLength << 8) | (encodedEntry[pos] & 0xFF);
        }
      }

      byte[] valueBytes = new byte[valueLength];
      System.arraycopy(encodedEntry, pos, valueBytes, 0, valueLength);
      pos += valueLength;
      values.add(new AttributeValue(attrType, new ASN1OctetString(valueBytes)));
    }

    return new Attribute(attrType, attrType.getPrimaryName(), options, values);
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
  private void store(Database database, byte[] keyBytes, byte[] valueBytes)
          throws DirectoryException
  {
    boolean successful = false;
    DatabaseEntry keyEntry   = new DatabaseEntry(keyBytes);
    DatabaseEntry valueEntry = new DatabaseEntry(valueBytes);

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

