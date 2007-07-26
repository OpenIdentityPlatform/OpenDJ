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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.opends.server.tools.makeldif.MakeLDIFInputStream;
import org.opends.server.tools.makeldif.TemplateFile;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.UtilityMessages.*;



/**
 * This class defines a data structure for holding configuration
 * information to use when performing an LDIF import.
 */
public class LDIFImportConfig
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  /**
   * The default buffer size that will be used when reading LDIF data.
   */
  private static final int DEFAULT_BUFFER_SIZE = 8192;



  // Indicates whether to append to the existing data set rather than
  // replacing it.
  private boolean appendToExistingData;

  // Indicates whether to include the objectclasses in the entries
  // read from the import.
  private boolean includeObjectClasses;

  // Indicates whether to invoke LDIF import plugins whenever an entry
  // is read.
  private boolean invokeImportPlugins;

  // Indicates whether the import is compressed.
  private boolean isCompressed;

  // Indicates whether the import is encrypted.
  private boolean isEncrypted;

  // Indicates whether to clear all base DNs in a backend.
  private boolean clearBackend;

  // Indicates whether to replace existing entries when appending
  // data.
  private boolean replaceExistingEntries;

  // Indicates whether to perform schema validation on the entries
  // read.
  private boolean validateSchema;

  // The buffered reader from which the LDIF data should be read.
  private BufferedReader reader;

  // The buffered writer to which rejected entries should be written.
  private BufferedWriter rejectWriter;

  // The buffered writer to which rejected entries should be written.
  private BufferedWriter skipWriter;

  // The input stream to use to read the data to import.
  private InputStream ldifInputStream;

  // The buffer size to use when reading data from the LDIF file.
  private int bufferSize;

  // The iterator used to read through the set of LDIF files.
  private Iterator<String> ldifFileIterator;

  // The set of base DNs to exclude from the import.
  private List<DN> excludeBranches;

  // The set of base DNs to include from the import.
  private List<DN> includeBranches;

  // The set of search filters for entries to exclude from the import.
  private List<SearchFilter> excludeFilters;

  // The set of search filters for entries to include in the import.
  private List<SearchFilter> includeFilters;

  // The set of LDIF files to be imported.
  private List<String> ldifFiles;

  // The set of attribute types that should be excluded from the
  // import.
  private Set<AttributeType> excludeAttributes;

  // The set of attribute types that should be included in the import.
  private Set<AttributeType> includeAttributes;

  // Indicates whether all the user attributes should be included.
  private boolean includeAllUserAttrs;

  //Indicates whether all the operational attributes should be
  // included.
  private boolean includeAllOpAttrs;

  //Indicates whether all the user attributes should be excluded.
  private boolean excludeAllUserAttrs;

  //Indicates whether all the operational attributes should be
  // excluded.
  private boolean excludeAllOpAttrs;



  /**
   * Creates a new LDIF import configuration that will read from the
   * specified LDIF file.
   *
   * @param  ldifFile  The path to the LDIF file with the data to
   *                   import.
   */
  public LDIFImportConfig(String ldifFile)
  {
    ldifFiles = new ArrayList<String>(1);
    ldifFiles.add(ldifFile);
    ldifFileIterator = ldifFiles.iterator();

    ldifInputStream        = null;
    bufferSize             = DEFAULT_BUFFER_SIZE;
    excludeBranches        = new ArrayList<DN>();
    includeBranches        = new ArrayList<DN>();
    excludeFilters         = new ArrayList<SearchFilter>();
    includeFilters         = new ArrayList<SearchFilter>();
    appendToExistingData   = false;
    replaceExistingEntries = false;
    includeObjectClasses   = true;
    invokeImportPlugins    = false;
    isCompressed           = false;
    isEncrypted            = false;
    clearBackend           = false;
    validateSchema         = true;
    reader                 = null;
    rejectWriter           = null;
    skipWriter             = null;
    excludeAttributes      = new HashSet<AttributeType>();
    includeAttributes      = new HashSet<AttributeType>();
    includeAllUserAttrs    = false;
    includeAllOpAttrs      = false;
    excludeAllUserAttrs    = false;
    excludeAllOpAttrs      = false;

  }



  /**
   * Creates a new LDIF import configuration that will read from the
   * specified LDIF files.  The files will be imported in the order
   * they are specified in the provided list.
   *
   * @param  ldifFiles  The paths to the LDIF files with the data to
   *                    import.
   */
  public LDIFImportConfig(List<String> ldifFiles)
  {
    this.ldifFiles = ldifFiles;
    ldifFileIterator = ldifFiles.iterator();

    ldifInputStream        = null;
    bufferSize             = DEFAULT_BUFFER_SIZE;
    excludeBranches        = new ArrayList<DN>();
    includeBranches        = new ArrayList<DN>();
    excludeFilters         = new ArrayList<SearchFilter>();
    includeFilters         = new ArrayList<SearchFilter>();
    appendToExistingData   = false;
    replaceExistingEntries = false;
    includeObjectClasses   = true;
    invokeImportPlugins    = false;
    isCompressed           = false;
    isEncrypted            = false;
    validateSchema         = true;
    reader                 = null;
    rejectWriter           = null;
    skipWriter             = null;
    excludeAttributes      = new HashSet<AttributeType>();
    includeAttributes      = new HashSet<AttributeType>();
    includeAllUserAttrs    = false;
    includeAllOpAttrs      = false;
    excludeAllUserAttrs    = false;
    excludeAllOpAttrs      = false;

  }



  /**
   * Creates a new LDIF import configuration that will read from the
   * provided input stream.
   *
   * @param  ldifInputStream  The input stream from which to read the
   *                          LDIF data.
   */
  public LDIFImportConfig(InputStream ldifInputStream)
  {
    this.ldifInputStream   = ldifInputStream;
    bufferSize             = DEFAULT_BUFFER_SIZE;
    ldifFiles              = null;
    ldifFileIterator       = null;

    excludeBranches        = new ArrayList<DN>();
    includeBranches        = new ArrayList<DN>();
    excludeFilters         = new ArrayList<SearchFilter>();
    includeFilters         = new ArrayList<SearchFilter>();
    appendToExistingData   = false;
    replaceExistingEntries = false;
    includeObjectClasses   = true;
    invokeImportPlugins    = false;
    isCompressed           = false;
    isEncrypted            = false;
    reader                 = null;
    rejectWriter           = null;
    skipWriter             = null;
    excludeAttributes      = new HashSet<AttributeType>();
    includeAttributes      = new HashSet<AttributeType>();
    includeAllUserAttrs    = false;
    includeAllOpAttrs      = false;
    excludeAllUserAttrs    = false;
    excludeAllOpAttrs      = false;

  }

  /**
   * Creates a new LDIF import configuration that will read from the
   * provided reader.
   *
   * @param  ldifInputReader  The input stream from which to read the
   *                          LDIF data.
   */
  public LDIFImportConfig(Reader ldifInputReader)
  {
    ldifInputStream        = null;
    bufferSize             = DEFAULT_BUFFER_SIZE;
    ldifFiles              = null;
    ldifFileIterator       = null;

    excludeBranches        = new ArrayList<DN>();
    includeBranches        = new ArrayList<DN>();
    excludeFilters         = new ArrayList<SearchFilter>();
    includeFilters         = new ArrayList<SearchFilter>();
    appendToExistingData   = false;
    replaceExistingEntries = false;
    includeObjectClasses   = true;
    invokeImportPlugins    = false;
    isCompressed           = false;
    isEncrypted            = false;
    reader                 = getBufferedReader(ldifInputReader);
    rejectWriter           = null;
    skipWriter             = null;
    excludeAttributes      = new HashSet<AttributeType>();
    includeAttributes      = new HashSet<AttributeType>();
    includeAllUserAttrs    = false;
    includeAllOpAttrs      = false;
    excludeAllUserAttrs    = false;
    excludeAllOpAttrs      = false;

  }

  /**
   * Wrap reader in a BufferedReader if necessary.
   *
   * @param reader the reader to buffer
   * @return reader as a BufferedReader
   */
  private BufferedReader getBufferedReader(Reader reader) {
    if (reader instanceof BufferedReader) {
      return (BufferedReader)reader;
    } else {
      return new BufferedReader(reader);
    }
  }

  /**
   * Creates a new LDIF import configuration that will generate
   * entries using the given MakeLDIF template file rather than
   * reading them from an existing LDIF file.
   *
   * @param  templateFile  The template file to use to generate the
   *                       entries.
   */
  public LDIFImportConfig(TemplateFile templateFile)
  {
    this(new MakeLDIFInputStream(templateFile));


  }



  /**
   * Retrieves the reader that should be used to read the LDIF data.
   * Note that if the LDIF file is compressed and/or encrypted, then
   * that must be indicated before this method is called for the first
   * time.
   *
   * @return  The reader that should be used to read the LDIF data.
   *
   * @throws  IOException  If a problem occurs while obtaining the
   *                       reader.
   */
  public BufferedReader getReader()
         throws IOException
  {
    if (reader == null)
    {
      InputStream inputStream;
      if (ldifInputStream == null)
      {
        inputStream = ldifInputStream =
             new FileInputStream(ldifFileIterator.next());
      }
      else
      {
        inputStream = ldifInputStream;
      }

      if (isEncrypted)
      {
        // FIXME -- Add support for encryption with a cipher input
        //          stream.
      }

      if (isCompressed)
      {
        inputStream = new GZIPInputStream(inputStream);
      }

      reader = new BufferedReader(new InputStreamReader(inputStream),
                                  bufferSize);
    }

    return reader;
  }



  /**
   * Retrieves the LDIF reader configured to read from the next LDIF
   * file in the list.
   *
   * @return  The reader that should be used to read the LDIF data, or
   *          <CODE>null</CODE> if there are no more files to read.
   *
   * @throws  IOException  If a problem occurs while obtaining the
   *                       reader.
   */
  public BufferedReader nextReader()
         throws IOException
  {
    if ((ldifFileIterator == null) || (! ldifFileIterator.hasNext()))
    {
      return null;
    }
    else
    {
      reader.close();

      InputStream inputStream = ldifInputStream =
           new FileInputStream(ldifFileIterator.next());

      if (isEncrypted)
      {
        // FIXME -- Add support for encryption with a cipher input
        //          stream.
      }

      if (isCompressed)
      {
        inputStream = new GZIPInputStream(inputStream);
      }

      reader = new BufferedReader(new InputStreamReader(inputStream),
                                  bufferSize);
      return reader;
    }
  }



  /**
   * Retrieves the writer that should be used to write entries that
   * are rejected rather than imported for some reason.
   *
   * @return  The reject writer, or <CODE>null</CODE> if none is to be
   *          used.
   */
  public BufferedWriter getRejectWriter()
  {
    return rejectWriter;
  }

  /**
   * Retrieves the writer that should be used to write entries that
   * are skipped because they don't match the criteri.
   *
   * @return  The skip writer, or <CODE>null</CODE> if none is to be
   *          used.
   */
  public BufferedWriter getSkipWriter()
  {
    return skipWriter;
  }

  /**
   * Indicates that rejected entries should be written to the
   * specified file.  Note that this applies only to entries that are
   * rejected because they are invalid (e.g., are malformed or don't
   * conform to schema requirements), and not to entries that are
   * rejected because they matched exclude criteria.
   *
   * @param  rejectFile            The path to the file to which
   *                               reject information should be
   *                               written.
   * @param  existingFileBehavior  Indicates how to treat an existing
   *                               file.
   *
   * @throws  IOException  If a problem occurs while opening the
   *                       reject file for writing.
   */
  public void writeRejectedEntries(String rejectFile,
                   ExistingFileBehavior existingFileBehavior)
         throws IOException
  {
    if (rejectFile == null)
    {
      if (rejectWriter != null)
      {
        try
        {
          rejectWriter.close();
        } catch (Exception e) {}

        rejectWriter = null;
      }

      return;
    }

    switch (existingFileBehavior)
    {
      case APPEND:
        rejectWriter =
             new BufferedWriter(new FileWriter(rejectFile, true));
        break;
      case OVERWRITE:
        rejectWriter =
             new BufferedWriter(new FileWriter(rejectFile, false));
        break;
      case FAIL:
        File f = new File(rejectFile);
        if (f.exists())
        {
          throw new IOException(getMessage(MSGID_REJECT_FILE_EXISTS,
                                           rejectFile));
        }
        else
        {
          rejectWriter =
               new BufferedWriter(new FileWriter(rejectFile));
        }
        break;
    }
  }



  /**
   * Indicates that rejected entries should be written to the provided
   * output stream.  Note that this applies only to entries that are
   * rejected because they are invalid (e.g., are malformed or don't
   * conform to schema requirements), and not to entries that are
   * rejected because they matched exclude criteria.
   *
   * @param  outputStream  The output stream to which rejected entries
   *                       should be written.
   */
  public void writeRejectedEntries(OutputStream outputStream)
  {
    if (outputStream == null)
    {
      if (rejectWriter != null)
      {
        try
        {
          rejectWriter.close();
        } catch (Exception e) {}

        rejectWriter = null;
      }

      return;
    }

    rejectWriter =
         new BufferedWriter(new OutputStreamWriter(outputStream));
  }

  /**
   * Indicates that skipped entries should be written to the
   * specified file.  Note that this applies only to entries that are
   * skipped because they matched exclude criteria.
   *
   * @param  skipFile              The path to the file to which
   *                               skipped information should be
   *                               written.
   * @param  existingFileBehavior  Indicates how to treat an existing
   *                               file.
   *
   * @throws  IOException  If a problem occurs while opening the
   *                       skip file for writing.
   */
  public void writeSkippedEntries(String skipFile,
                   ExistingFileBehavior existingFileBehavior)
         throws IOException
  {
    if (skipFile == null)
    {
      if (skipWriter != null)
      {
        try
        {
          skipWriter.close();
        } catch (Exception e) {}

        skipWriter = null;
      }

      return;
    }

    switch (existingFileBehavior)
    {
      case APPEND:
        skipWriter =
             new BufferedWriter(new FileWriter(skipFile, true));
        break;
      case OVERWRITE:
        skipWriter =
             new BufferedWriter(new FileWriter(skipFile, false));
        break;
      case FAIL:
        File f = new File(skipFile);
        if (f.exists())
        {
          throw new IOException(getMessage(MSGID_SKIP_FILE_EXISTS,
                                           skipFile));
        }
        else
        {
          skipWriter =
               new BufferedWriter(new FileWriter(skipFile));
        }
        break;
    }
  }



  /**
   * Indicates that skipped entries should be written to the provided
   * output stream.  Note that this does not apply to entries that are
   * rejected because they are invalid (e.g., are malformed or don't
   * conform to schema requirements), but only apply to entries that
   * are skipped because they matched exclude criteria.
   *
   * @param  outputStream  The output stream to which skipped entries
   *                       should be written.
   */
  public void writeSkippedEntries(OutputStream outputStream)
  {
    if (outputStream == null)
    {
      if (skipWriter != null)
      {
        try
        {
          skipWriter.close();
        } catch (Exception e) {}

        skipWriter = null;
      }

      return;
    }

    skipWriter =
         new BufferedWriter(new OutputStreamWriter(outputStream));
  }

  /**
   * Indicates whether to append to an existing data set or completely
   * replace it.
   *
   * @return  <CODE>true</CODE> if the import should append to an
   *          existing data set, or <CODE>false</CODE> if not.
   */
  public boolean appendToExistingData()
  {
    return appendToExistingData;
  }



  /**
   * Specifies whether to append to an existing data set or completely
   * replace it.
   *
   * @param  appendToExistingData  Indicates whether to append to an
   *                               existing data set or completely
   *                               replace it.
   */
  public void setAppendToExistingData(boolean appendToExistingData)
  {
    this.appendToExistingData = appendToExistingData;
  }



  /**
   * Indicates whether to replace the existing entry if a duplicate is
   * found or to reject the new entry.  This only applies when
   * appending to an existing data set.
   *
   * @return  <CODE>true</CODE> if an existing entry should be
   *          replaced with the new entry from the provided data set,
   *          or <CODE>false</CODE> if the new entry should be
   *          rejected.
   */
  public boolean replaceExistingEntries()
  {
    return replaceExistingEntries;
  }



  /**
   * Specifies whether to replace the existing entry if a duplicate is
   * found or to reject the new entry.  This only applies when
   * appending to an existing data set.
   *
   * @param  replaceExistingEntries  Indicates whether to replace the
   *                                 existing entry if a duplicate is
   *                                 found or to reject the new entry.
   */
  public void setReplaceExistingEntries(
                   boolean replaceExistingEntries)
  {
    this.replaceExistingEntries = replaceExistingEntries;
  }



  /**
   * Indicates whether any LDIF import plugins registered with the
   * server should be invoked during the import operation.
   *
   * @return  <CODE>true</CODE> if registered LDIF import plugins
   *          should be invoked during the import operation, or
   *          <CODE>false</CODE> if they should not be invoked.
   */
  public boolean invokeImportPlugins()
  {
    return invokeImportPlugins;
  }



  /**
   * Specifies whether any LDIF import plugins registered with the
   * server should be invoked during the import operation.
   *
   * @param  invokeImportPlugins  Specifies whether any LDIF import
   *                              plugins registered with the server
   *                              should be invoked during the import
   *                              operation.
   */
  public void setInvokeImportPlugins(boolean invokeImportPlugins)
  {
    this.invokeImportPlugins = invokeImportPlugins;
  }



  /**
   * Indicates whether the input LDIF source is expected to be
   * compressed.
   *
   * @return  <CODE>true</CODE> if the LDIF source is expected to be
   *          compressed, or <CODE>false</CODE> if not.
   */
  public boolean isCompressed()
  {
    return isCompressed;
  }



  /**
   * Specifies whether the input LDIF source is expected to be
   * compressed.  If compression is used, then this must be set prior
   * to the initial call to <CODE>getReader</CODE>.
   *
   * @param  isCompressed  Indicates whether the input LDIF source is
   *                       expected to be compressed.
   */
  public void setCompressed(boolean isCompressed)
  {
    this.isCompressed = isCompressed;
  }



  /**
   * Indicates whether the input LDIF source is expected to be
   * encrypted.
   *
   * @return  <CODE>true</CODE> if the LDIF source is expected to be
   *          encrypted, or <CODE>false</CODE> if not.
   */
  public boolean isEncrypted()
  {
    return isEncrypted;
  }



  /**
   * Specifies whether the input LDIF source is expected to be
   * encrypted.  If encryption is used, then this must be set prior to
   * the initial call to <CODE>getReader</CODE>.
   *
   * @param  isEncrypted  Indicates whether the input LDIF source is
   *                      expected to be encrypted.
   */
  public void setEncrypted(boolean isEncrypted)
  {
    this.isEncrypted = isEncrypted;
  }



  /**
   * Indicates whether to clear the entire backend if importing to a
   * backend with more than one base DNs.
   *
   * @return <CODE>true</code> if the entire backend should be
   * cleared or <CODE>false</CODE> if not.
   */
  public boolean clearBackend()
  {
    return clearBackend;
  }



  /**
   * Specifies whether to clear the entire backend if importing to a
   * backend.
   *
   * @param clearBackend Indicates whether to clear the entire
   * backend.
   */
  public void setClearBackend(boolean clearBackend)
  {
    this.clearBackend = clearBackend;
  }



  /**
   * Indicates whether to perform schema validation on entries as they
   * are read.
   *
   * @return  <CODE>true</CODE> if schema validation should be
   *          performed on the entries as they are read, or
   *          <CODE>false</CODE> if not.
   */
  public boolean validateSchema()
  {
    return validateSchema;
  }



  /**
   * Specifies whether to perform schema validation on entries as they
   * are read.
   *
   * @param  validateSchema  Indicates whether to perform schema
   *                         validation on entries as they are read.
   */
  public void setValidateSchema(boolean validateSchema)
  {
    this.validateSchema = validateSchema;
  }



  /**
   * Retrieves the set of base DNs that specify the set of entries to
   * exclude from the import.  The contents of the returned list may
   * be altered by the caller.
   *
   * @return  The set of base DNs that specify the set of entries to
   *          exclude from the import.
   */
  public List<DN> getExcludeBranches()
  {
    return excludeBranches;
  }



  /**
   * Specifies the set of base DNs that specify the set of entries to
   * exclude from the import.
   *
   * @param  excludeBranches  The set of base DNs that specify the set
   *                          of entries to exclude from the import.
   */
  public void setExcludeBranches(List<DN> excludeBranches)
  {
    if (excludeBranches == null)
    {
      this.excludeBranches = new ArrayList<DN>(0);
    }
    else
    {
      this.excludeBranches = excludeBranches;
    }
  }



  /**
   * Retrieves the set of base DNs that specify the set of entries to
   * include in the import.  The contents of the returned list may be
   * altered by the caller.
   *
   * @return  The set of base DNs that specify the set of entries to
   *          include in the import.
   */
  public List<DN> getIncludeBranches()
  {
    return includeBranches;
  }



  /**
   * Specifies the set of base DNs that specify the set of entries to
   * include in the import.
   *
   * @param  includeBranches  The set of base DNs that specify the set
   *                          of entries to include in the import.
   */
  public void setIncludeBranches(List<DN> includeBranches)
  {
    if (includeBranches == null)
    {
      this.includeBranches = new ArrayList<DN>(0);
    }
    else
    {
      this.includeBranches = includeBranches;
    }
  }



  /**
   * Indicates whether to include the entry with the specified DN in
   * the import.
   *
   * @param  dn  The DN of the entry for which to make the
   *             determination.
   *
   * @return  <CODE>true</CODE> if the entry with the specified DN
   *          should be included in the import, or <CODE>false</CODE>
   *          if not.
   */
  public boolean includeEntry(DN dn)
  {
    if (! excludeBranches.isEmpty())
    {
      for (DN excludeBranch : excludeBranches)
      {
        if (excludeBranch.isAncestorOf(dn))
        {
          return false;
        }
      }
    }

    if (! includeBranches.isEmpty())
    {
      for (DN includeBranch : includeBranches)
      {
        if (includeBranch.isAncestorOf(dn))
        {
          return true;
        }
      }

      return false;
    }

    return true;
  }



  /**
   * Indicates whether the set of objectclasses should be included in
   * the entries read from the LDIF.
   *
   * @return  <CODE>true</CODE> if the set of objectclasses should be
   *          included in the entries read from the LDIF, or
   *          <CODE>false</CODE> if not.
   */
  public boolean includeObjectClasses()
  {
    return includeObjectClasses;
  }



  /**
   * Specifies whether the set of objectclasses should be included in
   * the entries read from the LDIF.
   *
   * @param  includeObjectClasses  Indicates whether the set of
   *                               objectclasses should be included in
   *                               the entries read from the LDIF.
   */
  public void setIncludeObjectClasses(boolean includeObjectClasses)
  {
    this.includeObjectClasses = includeObjectClasses;
  }



  /**
   * Retrieves the set of attributes that should be excluded from the
   * entries read from the LDIF.  The contents of the returned set may
   * be modified by the caller.
   *
   * @return  The set of attributes that should be excluded from the
   *          entries read from the LDIF.
   */
  public Set<AttributeType> getExcludeAttributes()
  {
    return excludeAttributes;
  }



  /**
   * Specifies the set of attributes that should be excluded from the
   * entries read from the LDIF.
   *
   * @param  excludeAttributes  The set of attributes that should be
   *                            excluded from the entries read from
   *                            the LDIF.
   */
  public void setExcludeAttributes(
                   Set<AttributeType> excludeAttributes)
  {
    if (excludeAttributes == null)
    {
      this.excludeAttributes = new HashSet<AttributeType>(0);
    }
    else
    {
      this.excludeAttributes = excludeAttributes;
    }
  }



  /**
   * Retrieves the set of attributes that should be included in the
   * entries read from the LDIF.  The contents of the returned set may
   * be modified by the caller.
   *
   * @return  The set of attributes that should be included in the
   *          entries read from the LDIF.
   */
  public Set<AttributeType> getIncludeAttributes()
  {
    return includeAttributes;
  }



  /**
   * Specifies the set of attributes that should be included in the
   * entries read from the LDIF.
   *
   * @param  includeAttributes  The set of attributes that should be
   *                            included in the entries read from the
   *                            LDIF.
   */
  public void setIncludeAttributes(
                   Set<AttributeType> includeAttributes)
  {
    if (includeAttributes == null)
    {
      this.includeAttributes = new HashSet<AttributeType>(0);
    }
    else
    {
      this.includeAttributes = includeAttributes;
    }
  }



  /**
   * Indicates whether the specified attribute should be included in
   * the entries read from the LDIF.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   *
   * @return  <CODE>true</CODE> if the specified attribute should be
   *          included in the entries read from the LDIF, or
   *         <CODE>false</CODE> if not.
   */
  public boolean includeAttribute(AttributeType attributeType)
  {
    if ((! excludeAttributes.isEmpty()) &&
        excludeAttributes.contains(attributeType))
    {
      return false;
    }

     if(excludeAllOpAttrs && attributeType.isOperational() ||
      excludeAllUserAttrs && !attributeType.isOperational())
    {
      return false;
    }

    if(includeAllUserAttrs && !attributeType.isOperational() ||
           includeAllOpAttrs && attributeType.isOperational())
    {
      return true;
    }

    if (! includeAttributes.isEmpty())
    {
      return includeAttributes.contains(attributeType);
    }
    else
    {
       if(includeAllUserAttrs && attributeType.isOperational() ||
               includeAllOpAttrs && !attributeType.isOperational())
       {
         return false;
       }
    }
    return true;
  }



  /**
   * Retrieves the set of search filters that should be used to
   * determine which entries to exclude from the LDIF.  The contents
   * of the returned list may be modified by the caller.
   *
   * @return  The set of search filters that should be used to
   *          determine which entries to exclude from the LDIF.
   */
  public List<SearchFilter> getExcludeFilters()
  {
    return excludeFilters;
  }



  /**
   * Specifies the set of search filters that should be used to
   * determine which entries to exclude from the LDIF.
   *
   * @param  excludeFilters  The set of search filters that should be
   *                         used to determine which entries to
   *                         exclude from the LDIF.
   */
  public void setExcludeFilters(List<SearchFilter> excludeFilters)
  {
    if (excludeFilters == null)
    {
      this.excludeFilters = new ArrayList<SearchFilter>(0);
    }
    else
    {
      this.excludeFilters = excludeFilters;
    }
  }



  /**
   * Retrieves the set of search filters that should be used to
   * determine which entries to include in the LDIF.  The contents of
   * the returned list may be modified by  the caller.
   *
   * @return  The set of search filters that should be used to
   *          determine which entries to include in the LDIF.
   */
  public List<SearchFilter> getIncludeFilters()
  {
    return includeFilters;
  }



  /**
   * Specifies the set of search filters that should be used to
   * determine which entries to include in the LDIF.
   *
   * @param  includeFilters  The set of search filters that should be
   *                         used to determine which entries to
   *                         include in the LDIF.
   */
  public void setIncludeFilters(List<SearchFilter> includeFilters)
  {
    if (includeFilters == null)
    {
      this.includeFilters = new ArrayList<SearchFilter>(0);
    }
    else
    {
      this.includeFilters = includeFilters;
    }
  }



  /**
   * Indicates whether the specified entry should be included in the
   * import based on the configured set of include and exclude
   * filters.
   *
   * @param  entry  The entry for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the specified entry should be
   *          included in the import, or <CODE>false</CODE> if not.
   *
   * @throws  DirectoryException  If there is a problem with any of
   *                              the search filters used to make the
   *                              determination.
   */
  public boolean includeEntry(Entry entry)
         throws DirectoryException
  {
    if (! excludeFilters.isEmpty())
    {
      for (SearchFilter filter : excludeFilters)
      {
        if (filter.matchesEntry(entry))
        {
          return false;
        }
      }
    }

    if (! includeFilters.isEmpty())
    {
      for (SearchFilter filter : includeFilters)
      {
        if (filter.matchesEntry(entry))
        {
          return true;
        }
      }

      return false;
    }

    return true;
  }



  /**
   * Retrieves the buffer size that should be used when reading LDIF
   * data.
   *
   * @return  The buffer size that should be used when reading LDIF
   *          data.
   */
  public int getBufferSize()
  {
    return bufferSize;
  }



  /**
   * Specifies the buffer size that should be used when reading LDIF
   * data.
   *
   * @param  bufferSize  The buffer size that should be used when
   *                     reading LDIF data.
   */
  public void setBufferSize(int bufferSize)
  {
    this.bufferSize = bufferSize;
  }



    /**
   * Specifies whether all the user attributes should be excluded.
   *
   * @param  excludeAllUserAttrs  Specifies all user attributes to
   *         be excluded.
   */
  public void setExcludeAllUserAttributes(boolean excludeAllUserAttrs)
  {
    this.excludeAllUserAttrs = excludeAllUserAttrs;
  }



  /**
   * Specifies whether all the operational attributes should be
   * excluded.
   *
   * @param  excludeAllOpAttrs  Specifies whether all the
   *                            operational attributes
   *                            should be excluded.
   */
  public void setExcludeAllOperationalAttributes(
                                    boolean excludeAllOpAttrs)
  {
    this.excludeAllOpAttrs = excludeAllOpAttrs;
  }



  /**
   * Specifies whether all the operational attributes should be
   * included.
   *
   * @param  includeAllOpAttrs  Specifies whether all
   *         the operation attributes should be included.
   *
   */
  public void setIncludeAllOpAttributes(boolean includeAllOpAttrs)
  {
    this.includeAllOpAttrs = includeAllOpAttrs;
  }



  /**
   * Specifies whether all the user attributes should be included.
   *
   * @param  includeAllUserAttrs  Specifies whether all the
   *                              user attributes should be
   *                              included.
   */
  public void setIncludeAllUserAttributes(boolean includeAllUserAttrs)
  {
    this.includeAllUserAttrs = includeAllUserAttrs;
  }



  /**
   * Closes any resources that this import config might have open.
   */
  public void close()
  {
    try
    {
      reader.close();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }

    if (rejectWriter != null)
    {
      try
      {
        rejectWriter.close();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    if (skipWriter != null)
    {
      try
      {
        skipWriter.close();
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
}

