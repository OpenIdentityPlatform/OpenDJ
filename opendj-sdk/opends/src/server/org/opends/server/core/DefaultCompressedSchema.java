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
package org.opends.server.core;



import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.messages.Message;
import org.opends.server.api.CompressedSchema;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.types.*;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides a default implementation of a compressed schema manager
 * that will store the schema definitions in a binary file
 * (config/schematokens.dat).
 */
public final class DefaultCompressedSchema
       extends CompressedSchema
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



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



  /**
   * Creates a new instance of this compressed schema manager.
   */
  public DefaultCompressedSchema()
  {
    atDecodeMap = new ConcurrentHashMap<ByteSequence, AttributeType>();
    aoDecodeMap = new ConcurrentHashMap<ByteSequence, Set<String>>();
    ocDecodeMap =
        new ConcurrentHashMap<ByteSequence, Map<ObjectClass, String>>();
    adEncodeMap = new ConcurrentHashMap
      <AttributeType, ConcurrentHashMap<Set<String>, ByteSequence>>();
    ocEncodeMap = new ConcurrentHashMap<Map<ObjectClass, String>,
        ByteSequence>();

    adCounter = new AtomicInteger(1);
    ocCounter = new AtomicInteger(1);

    load();
  }



  /**
   * Loads the compressed schema information from disk.
   */
  private void load()
  {
    FileInputStream inputStream = null;

    try
    {
      // Determine the location of the compressed schema data file.  It should
      // be in the config directory with a name of "schematokens.dat".  If that
      // file doesn't exist, then don't do anything.
      String path = DirectoryServer.getInstanceRoot() + File.separator +
                    CONFIG_DIR_NAME + File.separator +
                    COMPRESSED_SCHEMA_FILE_NAME;
      if (! new File(path).exists())
      {
        return;
      }
      inputStream = new FileInputStream(path);
      ASN1Reader reader = ASN1.getReader(inputStream);


      // The first element in the file should be a sequence of object class
      // sets.  Each object class set will itself be a sequence of octet
      // strings, where the first one is the token and the remaining elements
      // are the names of the associated object classes.
      reader.readStartSequence();
      while(reader.hasNextElement())
      {
        reader.readStartSequence();
        ByteSequence token = reader.readOctetString();

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
      }
      reader.readEndSequence();


      // The second element in the file should be an integer element that holds
      // the value to use to initialize the object class counter.
      ocCounter.set((int)reader.readInteger());


      // The third element in the file should be a sequence of attribute
      // description components.  Each attribute description component will
      // itself be a sequence of octet strings, where the first one is the
      // token, the second is the attribute name, and all remaining elements are
      // the attribute options.
      reader.readStartSequence();
      while(reader.hasNextElement())
      {
        reader.readStartSequence();
        ByteSequence token = reader.readOctetString();
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
      }
      reader.readEndSequence();


      // The fourth element in the file should be an integer element that holds
      // the value to use to initialize the attribute description counter.
      adCounter.set((int)reader.readInteger());
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
        if (inputStream != null)
        {
          inputStream.close();
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
   * @throws  DirectoryException  If a problem occurs while writing the updated
   *                              information.
   */
  private void save()
          throws DirectoryException
  {
    FileOutputStream outputStream = null;
    try
    {
      // Determine the location of the "live" compressed schema data file, and
      // then append ".tmp" to get the name of the temporary file that we will
      // use.
      String path = DirectoryServer.getInstanceRoot() + File.separator +
                    CONFIG_DIR_NAME + File.separator +
                    COMPRESSED_SCHEMA_FILE_NAME;
      String tempPath = path + ".tmp";

      outputStream = new FileOutputStream(tempPath);
      ASN1Writer writer = ASN1.getWriter(outputStream);


      // The first element in the file should be a sequence of object class
      // sets.  Each object class set will itself be a sequence of octet
      // strings, where the first one is the token and the remaining elements
      // are the names of the associated object classes.
      writer.writeStartSequence();
      for (Map.Entry<ByteSequence,Map<ObjectClass,String>> mapEntry :
           ocDecodeMap.entrySet())
      {
        writer.writeStartSequence();
        writer.writeOctetString(mapEntry.getKey());
        Map<ObjectClass,String> ocMap = mapEntry.getValue();

        for (String ocName : ocMap.values())
        {
          writer.writeOctetString(ocName);
        }
        writer.writeEndSequence();
      }
      writer.writeEndSequence();


      // The second element in the file should be an integer element that holds
      // the value to use to initialize the object class counter.
      writer.writeInteger(ocCounter.get());


      // The third element in the file should be a sequence of attribute
      // description components.  Each attribute description component will
      // itself be a sequence of octet strings, where the first one is the
      // token, the second is the attribute name, and all remaining elements are
      // the attribute options.
      writer.writeStartSequence();
      for (ByteSequence token : atDecodeMap.keySet())
      {
        writer.writeStartSequence();
        AttributeType attrType = atDecodeMap.get(token);
        Set<String> options = aoDecodeMap.get(token);

        writer.writeOctetString(token);
        writer.writeOctetString(attrType.getNameOrOID());
        for (String option : options)
        {
          writer.writeOctetString(option);
        }
        writer.writeEndSequence();
      }
      writer.writeEndSequence();


      // The fourth element in the file should be an integer element that holds
      // the value to use to initialize the attribute description counter.
      writer.writeInteger(adCounter.get());


      // Close the writer and swing the temp file into place.
      outputStream.close();
      File liveFile = new File(path);
      File tempFile = new File(tempPath);

      if (liveFile.exists())
      {
        File saveFile = new File(liveFile.getAbsolutePath() + ".save");
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

      Message message = ERR_COMPRESSEDSCHEMA_CANNOT_WRITE_UPDATED_DATA.get(
                             stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }
    finally
    {
      try
      {
        if (outputStream != null)
        {
          outputStream.close();
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

        encodedClasses = ByteString.wrap(encodeInt(setValue));
        ocEncodeMap.put(objectClasses, encodedClasses);
        ocDecodeMap.put(encodedClasses, objectClasses);

        save();
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
      Message message = ERR_COMPRESSEDSCHEMA_UNKNOWN_OC_TOKEN.get(byteArray
          .toByteString().toHex());
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
  public void encodeAttribute(ByteStringBuilder entryBuffer,
                              Attribute attribute) throws DirectoryException
  {
    AttributeType type = attribute.getAttributeType();
    Set<String> options = attribute.getOptions();

    ConcurrentHashMap<Set<String>, ByteSequence> map = adEncodeMap.get(type);
    if (map == null)
    {
      ByteString byteArray;
      synchronized (adEncodeMap)
      {
        map = new ConcurrentHashMap<Set<String>, ByteSequence>(1);

        int intValue = adCounter.getAndIncrement();
        byteArray = ByteString.wrap(encodeInt(intValue));
        map.put(options,byteArray);

        adEncodeMap.put(type, map);
        atDecodeMap.put(byteArray, type);
        aoDecodeMap.put(byteArray, options);
        save();
      }

      encodeAttribute(entryBuffer, byteArray, attribute);
    }
    else
    {
      ByteSequence byteArray = map.get(options);
      if (byteArray == null)
      {
        synchronized (map)
        {
          int intValue = adCounter.getAndIncrement();
          byteArray = ByteString.wrap(encodeInt(intValue));
          map.put(options,byteArray);

          atDecodeMap.put(byteArray, type);
          aoDecodeMap.put(byteArray, options);
          save();
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
      Message message = ERR_COMPRESSEDSCHEMA_UNRECOGNIZED_AD_TOKEN.get(adArray
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
          AttributeValues.create(attrType,valueBytes));
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
}

