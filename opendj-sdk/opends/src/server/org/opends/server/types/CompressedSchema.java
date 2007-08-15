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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;
import org.opends.messages.Message;



import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Integer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.asn1.ASN1Writer;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides a utility for interacting with compressed
 * representations of schema elements.
 */
public class CompressedSchema
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * The singleton instance that will be used to perform the mapping.
   */
  private static CompressedSchema instance = new CompressedSchema();



  // The counter used for attribute descriptions.
  private AtomicInteger adCounter;

  // The counter used for object class sets.
  private AtomicInteger ocCounter;

  // The map between encoded representations and attribute types.
  private ConcurrentHashMap<ByteArray,AttributeType> atDecodeMap;

  // The map between encoded representations and attribute options.
  private ConcurrentHashMap<ByteArray,
               LinkedHashSet<String>> aoDecodeMap;

  // The map between encoded representations and object class sets.
  private ConcurrentHashMap<ByteArray,
               Map<ObjectClass,String>> ocDecodeMap;

  // The map between attribute descriptions and their encoded
  // representations.
  private ConcurrentHashMap<AttributeType,
               ConcurrentHashMap<LinkedHashSet<String>,ByteArray>>
                    adEncodeMap;

  // The map between object class sets and encoded representations.
  private ConcurrentHashMap<Map<ObjectClass,String>,
               ByteArray> ocEncodeMap;



  /**
   * Creates a new instance of this compressed schema manager.
   */
  private CompressedSchema()
  {
    atDecodeMap = new ConcurrentHashMap<ByteArray,AttributeType>();
    aoDecodeMap = new ConcurrentHashMap<ByteArray,
                           LinkedHashSet<String>>();
    ocDecodeMap = new ConcurrentHashMap<ByteArray,
                           Map<ObjectClass,String>>();
    adEncodeMap = new ConcurrentHashMap<AttributeType,
                           ConcurrentHashMap<LinkedHashSet<String>,
                                ByteArray>>();
    ocEncodeMap = new ConcurrentHashMap<Map<ObjectClass,String>,
                           ByteArray>();

    adCounter = new AtomicInteger(1);
    ocCounter = new AtomicInteger(1);

    load();
  }



  /**
   * Loads the compressed schema information from disk.
   */
  private void load()
  {
    ASN1Reader reader = null;

    try
    {
      // Determine the location of the compressed schema data file
      // It should be in the config directory with a name of
      // "schematokens.dat".  If that file doesn't exist, then don't
      // do anything.
      String path = DirectoryServer.getServerRoot() + File.separator +
                    CONFIG_DIR_NAME + File.separator +
                    COMPRESSED_SCHEMA_FILE_NAME;
      if (! new File(path).exists())
      {
        return;
      }
      FileInputStream inputStream = new FileInputStream(path);
      reader = new ASN1Reader(inputStream);


      // The first element in the file should be a sequence of object
      // class sets.  Each object class set will itself be a sequence
      // of octet strings, where the first one is the token and the
      // remaining elements are the names of the associated object
      // classes.
      ASN1Sequence ocSequence =
           reader.readElement().decodeAsSequence();
      for (ASN1Element element : ocSequence.elements())
      {
        ArrayList<ASN1Element> elements =
             element.decodeAsSequence().elements();
        ASN1OctetString os = elements.get(0).decodeAsOctetString();
        ByteArray token = new ByteArray(os.value());

        LinkedHashMap<ObjectClass,String> ocMap =
             new LinkedHashMap<ObjectClass,String>(elements.size()-1);
        for (int i=1; i < elements.size(); i++)
        {
          os = elements.get(i).decodeAsOctetString();
          String ocName = os.stringValue();
          String lowerName = toLowerCase(ocName);
          ObjectClass oc =
               DirectoryServer.getObjectClass(lowerName, true);
          ocMap.put(oc, ocName);
        }

        ocEncodeMap.put(ocMap, token);
        ocDecodeMap.put(token, ocMap);
      }


      // The second element in the file should be an integer element
      // that holds the value to use to initialize the object class
      // counter.
      ASN1Element counterElement = reader.readElement();
      ocCounter.set(counterElement.decodeAsInteger().intValue());


      // The third element in the file should be a sequence of
      // attribute description components.  Each attribute description
      // component will itself be a sequence of octet strings, where
      // the first one is the token, the second is the attribute name,
      // and all remaining elements are the attribute options.
      ASN1Sequence adSequence =
           reader.readElement().decodeAsSequence();
      for (ASN1Element element : adSequence.elements())
      {
        ArrayList<ASN1Element> elements =
             element.decodeAsSequence().elements();
        ASN1OctetString os = elements. get(0).decodeAsOctetString();
        ByteArray token = new ByteArray(os.value());

        os = elements.get(1).decodeAsOctetString();
        String attrName = os.stringValue();
        String lowerName = toLowerCase(attrName);
        AttributeType attrType =
             DirectoryServer.getAttributeType(lowerName, true);

        LinkedHashSet<String> options =
             new LinkedHashSet<String>(elements.size()-2);
        for (int i=2; i < elements.size(); i++)
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
          map = new ConcurrentHashMap<LinkedHashSet<String>,
                         ByteArray>(1);
          map.put(options, token);
          adEncodeMap.put(attrType, map);
        }
        else
        {
          map.put(options, token);
        }
      }


      // The fourth element in the file should be an integer element
      // that holds the value to use to initialize the attribute
      // description counter.
      counterElement = reader.readElement();
      adCounter.set(counterElement.decodeAsInteger().intValue());
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      // FIXME -- Should we do something else here?
      throw new RuntimeException(e);
    }
    finally
    {
      try
      {
        if (reader != null)
        {
          reader.close();
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
  }



  /**
   * Writes the compressed schema information to disk.
   *
   * @throws  DirectoryException  If a problem occurs while writing
   *                              the updated information.
   */
  private void save()
          throws DirectoryException
  {
    ASN1Writer writer = null;
    try
    {
      // Determine the location of the "live" compressed schema data
      // file, and then append ".tmp" to get the name of the temporary
      // file that we will use.
      String path = DirectoryServer.getServerRoot() + File.separator +
                    CONFIG_DIR_NAME + File.separator +
                    COMPRESSED_SCHEMA_FILE_NAME;
      String tempPath = path + ".tmp";

      FileOutputStream outputStream = new FileOutputStream(tempPath);
      writer = new ASN1Writer(outputStream);


      // The first element in the file should be a sequence of object
      // class sets.  Each object class set will itself be a sequence
      // of octet strings, where the first one is the token and the
      // remaining elements are the names of the associated object
      // classes.
      ArrayList<ASN1Element> ocElements =
           new ArrayList<ASN1Element>(ocDecodeMap.size());
      for (Map.Entry<ByteArray,Map<ObjectClass,String>> mapEntry :
           ocDecodeMap.entrySet())
      {
        ByteArray token = mapEntry.getKey();
        Map<ObjectClass,String> ocMap = mapEntry.getValue();

        ArrayList<ASN1Element> elements =
             new ArrayList<ASN1Element>(ocMap.size()+1);
        elements.add(new ASN1OctetString(token.array()));

        for (String ocName : ocMap.values())
        {
          elements.add(new ASN1OctetString(ocName));
        }

        ocElements.add(new ASN1Sequence(elements));
      }
      writer.writeElement(new ASN1Sequence(ocElements));


      // The second element in the file should be an integer element
      // that holds the value to use to initialize the object class
      // counter.
      writer.writeElement(new ASN1Integer(ocCounter.get()));


      // The third element in the file should be a sequence of
      // attribute description components.  Each attribute description
      // component will itself be a sequence of octet strings, where
      // the first one is the token, the second is the attribute name,
      // and all remaining elements are the attribute options.
      ArrayList<ASN1Element> adElements =
           new ArrayList<ASN1Element>(atDecodeMap.size());
      for (ByteArray token : atDecodeMap.keySet())
      {
        AttributeType attrType = atDecodeMap.get(token);
        LinkedHashSet<String> options = aoDecodeMap.get(token);

        ArrayList<ASN1Element> elements =
             new ArrayList<ASN1Element>(options.size()+2);
        elements.add(new ASN1OctetString(token.array()));
        elements.add(new ASN1OctetString(attrType.getNameOrOID()));
        for (String option : options)
        {
          elements.add(new ASN1OctetString(option));
        }

        adElements.add(new ASN1Sequence(elements));
      }
      writer.writeElement(new ASN1Sequence(adElements));


      // The fourth element in the file should be an integer element
      // that holds the value to use to initialize the attribute
      // description counter.
      writer.writeElement(new ASN1Integer(adCounter.get()));


      // Close the writer and swing the temp file into place.
      writer.close();
      File liveFile = new File(path);
      File tempFile = new File(tempPath);

      if (liveFile.exists())
      {
        File saveFile =
                  new File(liveFile.getAbsolutePath() + ".save");
        if (saveFile.exists())
        {
          saveFile.delete();
        }
        liveFile.renameTo(saveFile);
      }
      tempFile.renameTo(liveFile);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
        ERR_COMPRESSEDSCHEMA_CANNOT_WRITE_UPDATED_DATA.
            get(stackTraceToSingleLineString(e));
      throw new DirectoryException(
                     DirectoryServer.getServerErrorResultCode(),
                     message, e);
    }
    finally
    {
      try
      {
        if (writer != null)
        {
          writer.close();
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
  }



  /**
   * Encodes the provided set of object classes to a byte array.  If
   * the same set had been previously encoded, then the cached value
   * will be used.  Otherwise, a new value will be created.
   *
   * @param  objectClasses  The set of object classes for which to
   *                        retrieve the corresponding byte array
   *                        token.
   *
   * @return  A byte array containing the identifier assigned to the
   *          object class set.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to determine the appropriate
   *                              identifier.
   */
  public static byte[]
         encodeObjectClasses(Map<ObjectClass,String> objectClasses)
         throws DirectoryException
  {
    ByteArray encodedClasses =
                    instance.ocEncodeMap.get(objectClasses);
    if (encodedClasses == null)
    {
      synchronized (instance.ocEncodeMap)
      {
        int setValue = instance.ocCounter.getAndIncrement();
        byte[] array = encodeInt(setValue);

        encodedClasses = new ByteArray(array);
        instance.ocEncodeMap.put(objectClasses, encodedClasses);
        instance.ocDecodeMap.put(encodedClasses, objectClasses);

        instance.save();
        return array;
      }
    }
    else
    {
      return encodedClasses.array();
    }
  }



  /**
   * Decodes an object class set from the provided byte array.
   *
   * @param  encodedObjectClasses  The byte array containing the
   *                               object class set identifier.
   *
   * @return  The decoded object class set.
   *
   * @throws  DirectoryException  If the provided byte array cannot be
   *                              decoded as an object class set.
   */
  public static Map<ObjectClass,String>
         decodeObjectClasses(byte[] encodedObjectClasses)
         throws DirectoryException
  {
    ByteArray byteArray = new ByteArray(encodedObjectClasses);
    Map<ObjectClass,String> ocMap =
         instance.ocDecodeMap.get(byteArray);
    if (ocMap == null)
    {
      Message message = ERR_COMPRESSEDSCHEMA_UNKNOWN_OC_TOKEN.get(
          bytesToHex(encodedObjectClasses));
      throw new DirectoryException(
                     DirectoryServer.getServerErrorResultCode(),
                     message);
    }
    else
    {
      return ocMap;
    }
  }



  /**
   * Encodes the information in the provided attribute to a byte
   * array.
   *
   * @param  attribute  The attribute to be encoded.
   *
   * @return  An encoded representation of the provided attribute.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to determine the appropriate
   *                              identifier.
   */
  public static byte[] encodeAttribute(Attribute attribute)
         throws DirectoryException
  {
    AttributeType type = attribute.getAttributeType();
    LinkedHashSet<String> options = attribute.getOptions();

    ConcurrentHashMap<LinkedHashSet<String>,ByteArray> map =
         instance.adEncodeMap.get(type);
    if (map == null)
    {
      byte[] array;
      synchronized (instance.adEncodeMap)
      {
        map = new ConcurrentHashMap<LinkedHashSet<String>,
                       ByteArray>(1);

        int intValue = instance.adCounter.getAndIncrement();
        array = encodeInt(intValue);
        ByteArray byteArray = new ByteArray(array);
        map.put(options,byteArray);

        instance.adEncodeMap.put(type, map);
        instance.atDecodeMap.put(byteArray, type);
        instance.aoDecodeMap.put(byteArray, options);
        instance.save();
      }

      return encodeAttribute(array, attribute);
    }
    else
    {
      ByteArray byteArray = map.get(options);
      if (byteArray == null)
      {
        byte[] array;
        synchronized (map)
        {
          int intValue = instance.adCounter.getAndIncrement();
          array = encodeInt(intValue);
          byteArray = new ByteArray(array);
          map.put(options,byteArray);

          instance.atDecodeMap.put(byteArray, type);
          instance.aoDecodeMap.put(byteArray, options);
          instance.save();
        }

        return encodeAttribute(array, attribute);
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
  private static byte[] encodeAttribute(byte[] adArray,
                                        Attribute attribute)
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
   * Decodes the contents of the provided array as an attribute.
   *
   * @param  encodedEntry  The byte array containing the encoded
   *                       entry.
   * @param  startPos      The position within the array of the first
   *                       byte for the attribute to decode.
   * @param  length        The number of bytes contained in the
   *                       encoded attribute.
   *
   * @return  The decoded attribute.
   *
   * @throws  DirectoryException  If the attribute could not be
   *                              decoded properly for some reason.
   */
  public static Attribute decodeAttribute(byte[] encodedEntry,
                                          int startPos, int length)
         throws DirectoryException
  {
    // Figure out how many bytes are in the token that is the
    // placeholder for the attribute description.
    int pos = startPos;
    int adArrayLength = encodedEntry[pos] & 0x7F;
    if (adArrayLength != encodedEntry[pos++])
    {
      int numLengthBytes = adArrayLength;
      adArrayLength = 0;
      for (int i=0; i < numLengthBytes; i++, pos++)
      {
        adArrayLength =
             (adArrayLength << 8) | (encodedEntry[pos] & 0xFF);
      }
    }


    // Get the attribute description token and make sure it resolves
    // to an attribute type and option set.
    ByteArray adArray = new ByteArray(new byte[adArrayLength]);
    System.arraycopy(encodedEntry, pos, adArray.array(), 0,
                     adArrayLength);
    pos += adArrayLength;
    AttributeType attrType = instance.atDecodeMap.get(adArray);
    LinkedHashSet<String> options = instance.aoDecodeMap.get(adArray);
    if ((attrType == null) || (options == null))
    {
      Message message = ERR_COMPRESSEDSCHEMA_UNRECOGNIZED_AD_TOKEN.
          get(bytesToHex(adArray.array()));
      throw new DirectoryException(
                     DirectoryServer.getServerErrorResultCode(),
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
          valueLength =
               (valueLength << 8) | (encodedEntry[pos] & 0xFF);
        }
      }

      byte[] valueBytes = new byte[valueLength];
      System.arraycopy(encodedEntry, pos, valueBytes, 0, valueLength);
      pos += valueLength;
      values.add(new AttributeValue(attrType,
                                    new ASN1OctetString(valueBytes)));
    }

    return new Attribute(attrType, attrType.getPrimaryName(), options,
                         values);
  }



  /**
   * Encodes the provided int value to a byte array.
   *
   * @param  intValue  The int value to be encoded.
   *
   * @return  The byte array containing the encoded int value.
   */
  private static byte[] encodeInt(int intValue)
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
}

