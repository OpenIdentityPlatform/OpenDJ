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
 *      Portions copyright 2013 ForgeRock AS.
 */
package org.opends.server.core;



import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.opends.messages.Message;
import org.opends.server.api.CompressedSchema;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.types.ByteString;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;



/**
 * This class provides a default implementation of a compressed schema manager
 * that will store the schema definitions in a binary file
 * (config/schematokens.dat).
 */
public final class DefaultCompressedSchema extends CompressedSchema
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // Synchronizes calls to save.
  private final Object saveLock = new Object();



  /**
   * Creates a new instance of this compressed schema manager.
   */
  public DefaultCompressedSchema()
  {
    load();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  protected void storeAttribute(final byte[] encodedAttribute,
      final String attributeName, final Collection<String> attributeOptions)
      throws DirectoryException
  {
    save();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  protected void storeObjectClasses(final byte[] encodedObjectClasses,
      final Collection<String> objectClassNames) throws DirectoryException
  {
    save();
  }



  /**
   * Loads the compressed schema information from disk.
   */
  private void load()
  {
    FileInputStream inputStream = null;

    try
    {
      // Determine the location of the compressed schema data file. It should
      // be in the config directory with a name of "schematokens.dat". If that
      // file doesn't exist, then don't do anything.
      final String path = DirectoryServer.getInstanceRoot() + File.separator
          + CONFIG_DIR_NAME + File.separator + COMPRESSED_SCHEMA_FILE_NAME;
      if (!new File(path).exists())
      {
        return;
      }
      inputStream = new FileInputStream(path);
      final ASN1Reader reader = ASN1.getReader(inputStream);

      // The first element in the file should be a sequence of object class
      // sets. Each object class set will itself be a sequence of octet
      // strings, where the first one is the token and the remaining elements
      // are the names of the associated object classes.
      reader.readStartSequence();
      while (reader.hasNextElement())
      {
        reader.readStartSequence();
        final byte[] encodedObjectClasses = reader.readOctetString()
            .toByteArray();
        final List<String> objectClassNames = new LinkedList<String>();
        while (reader.hasNextElement())
        {
          objectClassNames.add(reader.readOctetStringAsString());
        }
        reader.readEndSequence();
        loadObjectClasses(encodedObjectClasses, objectClassNames);
      }
      reader.readEndSequence();

      // The second element in the file should be an integer element that holds
      // the value to use to initialize the object class counter.
      reader.readInteger(); // No longer used.

      // The third element in the file should be a sequence of attribute
      // description components. Each attribute description component will
      // itself be a sequence of octet strings, where the first one is the
      // token, the second is the attribute name, and all remaining elements are
      // the attribute options.
      reader.readStartSequence();
      while (reader.hasNextElement())
      {
        reader.readStartSequence();
        final byte[] encodedAttribute = reader.readOctetString().toByteArray();
        final String attributeName = reader.readOctetStringAsString();
        final List<String> attributeOptions = new LinkedList<String>();
        while (reader.hasNextElement())
        {
          attributeOptions.add(reader.readOctetStringAsString());
        }
        reader.readEndSequence();
        loadAttribute(encodedAttribute, attributeName, attributeOptions);
      }
      reader.readEndSequence();

      // The fourth element in the file should be an integer element that holds
      // the value to use to initialize the attribute description counter.
      reader.readInteger(); // No longer used.
    }
    catch (final Exception e)
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
      catch (final Exception e)
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
   * @throws DirectoryException
   *           If a problem occurs while writing the updated information.
   */
  private void save() throws DirectoryException
  {
    synchronized (saveLock)
    {
      FileOutputStream outputStream = null;
      try
      {
        // Determine the location of the "live" compressed schema data file, and
        // then append ".tmp" to get the name of the temporary file that we will
        // use.
        final String path = DirectoryServer.getInstanceRoot() + File.separator
            + CONFIG_DIR_NAME + File.separator + COMPRESSED_SCHEMA_FILE_NAME;
        final String tempPath = path + ".tmp";

        outputStream = new FileOutputStream(tempPath);
        final ASN1Writer writer = ASN1.getWriter(outputStream);

        // The first element in the file should be a sequence of object class
        // sets. Each object class set will itself be a sequence of octet
        // strings, where the first one is the token and the remaining elements
        // are the names of the associated object classes.
        writer.writeStartSequence();
        int ocCounter = 1;
        for (final Entry<byte[], Collection<String>> mapEntry :
            getAllObjectClasses())
        {
          writer.writeStartSequence();
          writer.writeOctetString(ByteString.wrap(mapEntry.getKey()));
          final Collection<String> objectClassNames = mapEntry.getValue();
          for (final String ocName : objectClassNames)
          {
            writer.writeOctetString(ocName);
          }
          writer.writeEndSequence();
          ocCounter++;
        }
        writer.writeEndSequence();

        // The second element in the file should be an integer element that
        // holds the value to use to initialize the object class counter.
        writer.writeInteger(ocCounter); // No longer used.

        // The third element in the file should be a sequence of attribute
        // description components. Each attribute description component will
        // itself be a sequence of octet strings, where the first one is the
        // token, the second is the attribute name, and all remaining elements
        // are the attribute options.
        writer.writeStartSequence();
        int adCounter = 1;
        for (final Entry<byte[], Entry<String, Collection<String>>> mapEntry :
            getAllAttributes())
        {
          writer.writeStartSequence();
          writer.writeOctetString(ByteString.wrap(mapEntry.getKey()));
          writer.writeOctetString(mapEntry.getValue().getKey());
          for (final String option : mapEntry.getValue().getValue())
          {
            writer.writeOctetString(option);
          }
          writer.writeEndSequence();
          adCounter++;
        }
        writer.writeEndSequence();

        // The fourth element in the file should be an integer element that
        // holds the value to use to initialize the attribute description
        // counter.
        writer.writeInteger(adCounter); // No longer used.

        // Close the writer and swing the temp file into place.
        outputStream.close();
        final File liveFile = new File(path);
        final File tempFile = new File(tempPath);

        if (liveFile.exists())
        {
          final File saveFile = new File(liveFile.getAbsolutePath() + ".save");
          if (saveFile.exists())
          {
            saveFile.delete();
          }
          liveFile.renameTo(saveFile);
        }
        tempFile.renameTo(liveFile);
      }
      catch (final Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        final Message message = ERR_COMPRESSEDSCHEMA_CANNOT_WRITE_UPDATED_DATA
            .get(stackTraceToSingleLineString(e));
        throw new DirectoryException(
            DirectoryServer.getServerErrorResultCode(), message, e);
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
        catch (final Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }
    }
  }

}
