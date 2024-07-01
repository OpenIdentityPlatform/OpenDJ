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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.tools.upgrade;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entries;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.Matcher;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldif.EntryReader;
import org.forgerock.opendj.ldif.LDIF;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.opendj.ldif.LDIFEntryWriter;
import org.forgerock.util.Reject;
import org.opends.server.core.DirectoryServer;
import org.opends.server.util.ChangeOperationType;
import org.opends.server.util.SchemaUtils;

import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.upgrade.FileManager.*;
import static org.opends.server.tools.upgrade.Installation.*;
import static org.opends.server.util.ChangeOperationType.*;
import static org.opends.server.util.ServerConstants.*;

/** Common utility methods needed by the upgrade. */
final class UpgradeUtils
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The {@code config} folder of the current instance. */
  static final File configDirectory = new File(getInstancePath(), CONFIG_PATH_RELATIVE);
  /** The {@code config/schema} folder of the current instance. */
  static final File configSchemaDirectory = new File(configDirectory, SCHEMA_PATH_RELATIVE);
  /** The {@code config/upgrade} folder of the current instance. */
  private static final File configUpgradeDirectory = new File(configDirectory, "upgrade");
  /** The {@code template} folder of the current installation. */
  private static final File templateDirectory = new File(getInstallationPath(), TEMPLATE_RELATIVE_PATH);
  /** The {@code template/config} folder of the current installation. */
  static final File templateConfigDirectory = new File(templateDirectory, CONFIG_PATH_RELATIVE);
  /** The {@code template/config/schema} folder of the current installation. */
  static final File templateConfigSchemaDirectory = new File(templateConfigDirectory, SCHEMA_PATH_RELATIVE);
  /** The {@code lib} folder of the current installation. */
  static final File libDirectory = new File(getInstallationPath(), LIB_RELATIVE_PATH);
  /** The {@code bin} folder of the current installation. */
  static final File binDirectory = new File(getInstallationPath(), UNIX_BINARIES_PATH_RELATIVE);
  /** The {@code bat} folder of the current installation. */
  static final File batDirectory = new File(getInstallationPath(), WINDOWS_BINARIES_PATH_RELATIVE);
  /** The server configuration file path. */
  static final File configFile = new File(configDirectory, CURRENT_CONFIG_FILE_NAME);
  /** The concatenated schema file of the current installation. */
  static final File concatenatedSchemaFile = new File(configUpgradeDirectory, SCHEMA_CONCAT_FILE_NAME);

  /**
   * Returns the path of the installation of the directory server. Note that
   * this method assumes that this code is being run locally.
   *
   * @return the path of the installation of the directory server.
   */
  private static String getInstallPathFromClasspath()
  {
    String installPath = DirectoryServer.getServerRoot();
    if (installPath != null)
    {
      return installPath;
    }

    /* Get the install path from the Class Path */
    final String sep = System.getProperty("path.separator");
    final String[] classPaths = System.getProperty("java.class.path").split(sep);
    final String path = getInstallPath(classPaths);
    if (path == null)
    {
      return null;
    }

    /*
     * Do a best effort to avoid having a relative representation
     * (for instance to avoid having ../../../).
     */
    final File f = new File(path).getAbsoluteFile();
    final File librariesDir = f.getParentFile();
    try
    {
      return librariesDir.getParentFile().getCanonicalPath();
    }
    catch (IOException ignore)
    {
      // Best effort
      return librariesDir.getParent();
    }
  }

  private static String getInstallPath(final String[] classPaths)
  {
    for (String classPath : classPaths)
    {
      final String normPath = classPath.replace(File.separatorChar, '/');
      if (normPath.endsWith(OPENDJ_BOOTSTRAP_JAR_RELATIVE_PATH))
      {
        return classPath;
      }
    }
    return null;
  }

  /**
   * Returns the path of the installation of the directory server. Note that
   * this method assumes that this code is being run locally.
   *
   * @param installPath
   *          The installation path
   * @return the path of the installation of the directory server.
   */
  static String getInstancePathFromInstallPath(final String installPath)
  {
    final File svcScriptPath = new File(installPath, SVC_SCRIPT_FILE_NAME);

    // look for /etc/opt/opendj/instance.loc
    File f = new File(INSTANCE_LOCATION_PATH);
    if (!svcScriptPath.exists() || !f.exists())
    {
      // look for <installPath>/instance.loc
      f = new File(installPath, INSTANCE_LOCATION_PATH_RELATIVE);
      if (!f.exists())
      {
        return installPath;
      }
    }

    // Read the first line and close the file.
    try (BufferedReader reader = new BufferedReader(new FileReader(f)))
    {
      String line = reader.readLine();
      File instanceLoc = new File(line.trim());
      if (instanceLoc.isAbsolute())
      {
        return instanceLoc.getAbsolutePath();
      }
      return new File(installPath, instanceLoc.getPath()).getAbsolutePath();
    }
    catch (IOException e)
    {
      return installPath;
    }
  }

  /**
   * Returns the absolute path for the given file. It tries to get the canonical
   * file path. If it fails it returns the string representation.
   *
   * @param f
   *          File to get the path
   * @return the absolute path for the given file.
   */
  static String getPath(File f)
  {
    if (f == null)
    {
      return null;
    }
    try
    {
      /*
       * Do a best effort to avoid having a relative representation
       * (for instance to avoid having ../../../).
       */
      return f.getCanonicalFile().toString();
    }
    catch (IOException ignore)
    {
      /*
       * This is a best effort to get the best possible representation of the file:
       * reporting the error is not necessary.
       */
      return f.toString();
    }
  }

  static File getFileForPath(String path)
  {
    final File f = new File(path);
    return f.isAbsolute() ? f : new File(getInstancePath(), path);
  }

  /**
   * Returns the instance root directory (the path where the instance is
   * installed).
   *
   * @return the instance root directory (the path where the instance is
   *         installed).
   */
  static String getInstancePath()
  {
    final String installPath = getInstallationPath();
    return installPath != null ? getInstancePathFromInstallPath(installPath) : null;
  }

  /**
   * Returns the server's installation path.
   *
   * @return The server's installation path.
   */
  static String getInstallationPath()
  {
    // The upgrade runs from the bits extracted by BuildExtractor in the staging directory.
    // However we still want the Installation to point at the build being
    // upgraded so the install path reported in [installroot].
    String installationPath = System.getProperty("INSTALL_ROOT");
    if (installationPath != null)
    {
      return installationPath;
    }

    final String path = getInstallPathFromClasspath();
    if (path != null)
    {
      final File f = new File(path);
      if (f.getParentFile() != null
          && f.getParentFile().getParentFile() != null
          && new File(f.getParentFile().getParentFile(), LOCKS_PATH_RELATIVE).exists())
      {
        return getPath(f.getParentFile().getParentFile());
      }
      return path;
    }
    return null;
  }

  /**
   * Return a {@link Map} with backend id as key and associated baseDNs as values.
   * <p>
   * Disabled backends are not filtered out.
   *
   * @return A {@link Map} of all enabled backends of the server with their baseDNs.
   */
  static Map<String, Set<String>> getBaseDNsPerBackendsFromConfig()
  {
    final SearchRequest sr = Requests.newSearchRequest("", SearchScope.WHOLE_SUBTREE,
            "(&(objectclass=ds-cfg-pluggable-backend)(ds-cfg-enabled=true))",
            "ds-cfg-base-dn", "ds-cfg-backend-id");
    final Map<String, Set<String>> baseDNs = new HashMap<>();
    try (final EntryReader entryReader = searchConfigFile(sr))
    {
      while (entryReader.hasNext())
      {
        final Entry entry = entryReader.readEntry();
        baseDNs.put(entry.parseAttribute("ds-cfg-backend-id").asString(),
                    entry.parseAttribute("ds-cfg-base-dn").asSetOfString());
      }
    }
    catch (Exception ex)
    {
      logger.error(LocalizableMessage.raw(ex.getMessage()));
    }
    return baseDNs;
  }

  static EntryReader searchConfigFile(final SearchRequest searchRequest) throws FileNotFoundException
  {
    final Schema schema = getUpgradeSchema();
    final File configFile = new File(configDirectory, CURRENT_CONFIG_FILE_NAME);
    final LDIFEntryReader entryReader = new LDIFEntryReader(new FileInputStream(configFile)).setSchema(schema);
    return LDIF.search(entryReader, searchRequest, schema);
  }

  /**
   * Updates the config file during the upgrade process.
   *
   * @param configFile
   *          The original path to the file.
   * @param filter
   *          The filter to select entries. Only useful for modify change type.
   * @param changeType
   *          The change type which must be applied to ldif lines.
   * @param ldifLines
   *          The change record ldif lines. For ADD change type, the first line must be the dn. For
   *          DELETE change type, the first and only line must be the dn.
   * @throws IOException
   *           If an Exception occurs during the input output methods.
   * @return The changes number that have occurred.
   */
  static int updateConfigFile(final File configFile,
      final Filter filter, final ChangeOperationType changeType,
      final String... ldifLines) throws IOException
  {
    final File copyConfig =
        File.createTempFile("copyConfig", ".tmp", configFile.getParentFile());

    int changeCount = 0;
    final Schema schema = getUpgradeSchema();
    try (LDIFEntryReader entryReader = new LDIFEntryReader(new FileInputStream(configFile)).setSchema(schema);
        LDIFEntryWriter writer = new LDIFEntryWriter(new FileOutputStream(copyConfig)))
    {
      writer.setWrapColumn(80);

      // Writes the header on the new file.
      writer.writeComment(INFO_CONFIG_FILE_HEADER.get());
      writer.setWrapColumn(0);

      DN ldifDN = null;
      Set<DN> ldifDNs = new HashSet<>();
      if (filter == null)
      {
        switch (changeType)
        {
        case ADD:
          // The first line should start with dn:
          ldifDN = DN.valueOf(removeDnPrefix(ldifLines[0]));
          ldifDNs.add(ldifDN);
          break;

        case DELETE:
          // All lines represent dns
          for (String dnLine : ldifLines)
          {
            ldifDNs.add(DN.valueOf(removeDnPrefix(dnLine)));
          }
          break;
        }
      }

      boolean entryAlreadyExist = false;
      final Filter f = filter != null ? filter : Filter.alwaysFalse();
      final Matcher matcher = f.matcher(schema);
      while (entryReader.hasNext())
      {
        Entry entry = entryReader.readEntry();
        final DN entryDN = entry.getName();
        // Searching for the related entries
        if (changeType == MODIFY
            && matcher.matches(entry) == ConditionResult.TRUE)
        {
          try
          {
            final ModifyRequest mr = Requests.newModifyRequest(
                readLDIFLines(entryDN, changeType, ldifLines));
            entry = Entries.modifyEntryPermissive(entry, mr.getModifications());
            changeCount++;
            logger.debug(LocalizableMessage.raw("The following entry has been modified : %s", entryDN));
          }
          catch (Exception ex)
          {
            logger.error(LocalizableMessage.raw(ex.getMessage()));
          }
        }

        if (ldifDNs.remove(entryDN))
        {
          logger.debug(LocalizableMessage.raw("Entry %s found", entryDN));
          entryAlreadyExist = true;

          if (changeType == DELETE)
          {
            entry = null;
            changeCount++;
            logger.debug(LocalizableMessage.raw("The following entry has been deleted : %s", entryDN));
          }
        }

        if (entry != null)
        {
          writer.writeEntry(entry);
        }
      }

      if (changeType == ADD && !entryAlreadyExist)
      {
        writer.writeEntry(Requests.newAddRequest(ldifLines));
        logger.debug(LocalizableMessage.raw("Entry successfully added %s in %s", ldifDN, configFile.getAbsolutePath()));
        changeCount++;
      }
    }
    catch (Exception ex)
    {
      throw new IOException(ex.getMessage());
    }

    // The reader and writer must be closed before renaming files.
    // Otherwise it causes exceptions under windows OS.

    try
    {
      // Renaming the file, overwriting previous one.
      rename(copyConfig, configFile);
      return changeCount;
    }
    catch (IOException e)
    {
      logger.error(LocalizableMessage.raw(e.getMessage()));
      deleteRecursively(configFile);
      throw e;
    }
  }

  private static String removeDnPrefix(String dnLine)
  {
    return dnLine.replaceFirst("dn: ", "");
  }

  /**
   * This task adds or updates attributes / object classes in the specified
   * destination file. The new attributes and object classes must be originally
   * defined in the template file. The definitions will replace previous definitions
   * if they have the same normalized value (i.e. OID), and add new definitions if
   * they don't previously exist.
   *
   * @param templateFile
   *          The file in which the attribute/object definition can be read.
   * @param destination
   *          The file where we want to update the definitions.
   * @param attributes
   *          Those attributes needed to be stored in the new destination file.
   * @param objectClasses
   *          Those object classes needed to be stored in the new destination file.
   * @return An integer which represents each time an attribute / object class
   *         is updated successfully in the destination file.
   * @throws IOException
   *           If an unexpected IO error occurred while reading the entry.
   * @throws IllegalStateException
   *           Failure to find an attribute in the template schema indicates
   *           either a programming error (e.g. typo in the attribute name) or
   *           template corruption. Upgrade should stop.
   */
  static int updateSchemaFile(final File templateFile, final File destination,
      final String[] attributes, final String[] objectClasses)
      throws IOException, IllegalStateException
  {
    int changeCount = 0;

    final Entry templateSchemaEntry = readFirstEntryFromTemplate(templateFile);
    final Entry destinationSchemaEntry = readFirstEntryFromTemplate(destination);

    if (attributes != null)
    {
      for (final String att : attributes)
      {
        final ByteString attributeType = getSchemaElement(templateSchemaEntry, "attributeTypes", att);
        destinationSchemaEntry.getAttribute("attributeTypes").add(attributeType);
        changeCount++;
        logger.debug(LocalizableMessage.raw("Added %s", attributeType));
      }
    }

    if (objectClasses != null)
    {
      for (final String oc : objectClasses)
      {
        final ByteString objectClass = getSchemaElement(templateSchemaEntry, "objectClasses", oc);
        destinationSchemaEntry.getAttribute("objectClasses").add(objectClass);
        changeCount++;
        logger.trace("Added %s", objectClass);
      }
    }

    File copy = File.createTempFile("copySchema", ".tmp", destination.getParentFile());
    try (final FileOutputStream fos = new FileOutputStream(copy);
        LDIFEntryWriter destinationWriter = new LDIFEntryWriter(fos))
    {
      // Then writes the new schema entry.
      destinationWriter.setWrapColumn(79);
      // Copy comments to fos (get License and first comments only).
      writeFileHeaderComments(templateFile, destinationWriter);
      // Writes the entry after.
      destinationWriter.writeEntry(destinationSchemaEntry);
    }
    // Readers and writer must be closed before writing files.
    // This causes exceptions under windows OS.

    // Renames the copy to make it the new schema file.
    try
    {
      rename(copy, destination);
    }
    catch (IOException e)
    {
      logger.error(LocalizableMessage.raw(e.getMessage()));
      deleteRecursively(copy);
      throw e;
    }

    return changeCount;
  }

  private static Entry readFirstEntryFromTemplate(final File destination) throws IOException
  {
    try (LDIFEntryReader r = new LDIFEntryReader(new FileInputStream(destination)))
    {
      if (!r.hasNext())
      {
        // Unless template are corrupted, this should not happen.
        throw new IOException(ERR_UPGRADE_CORRUPTED_TEMPLATE.get(destination.getPath()).toString());
      }
      return r.readEntry();
    }
  }

  /**
   * Gets and writes the first comments of a file.
   *
   * @param file
   *          The selected file to get the comments.
   * @param writer
   *          The writer which is going to write the comments.
   * @throws IOException
   *           If an error occurred with the file.
   */
  private static void writeFileHeaderComments(final File file,
      final LDIFEntryWriter writer) throws IOException
  {
    try (BufferedReader br = new BufferedReader(new FileReader(file)))
    {
      String comment = br.readLine();

      while (comment != null && comment.startsWith("#"))
      {
        writer.writeComment(comment.replaceAll("# ", "").replaceAll("#", ""));
        comment = br.readLine();
      }
    }
    catch (IOException ex)
    {
      throw ex;
    }
  }

  /**
   * Returns the definition of the selected attribute / object class OID.
   *
   * @param schemaEntry
   *          The selected schema entry to search on.
   * @param type
   *          The type of the research. ("objectClasses" or "attributeTypes")
   * @param oid
   *          The OID of the element to search for.
   * @return The byte string definition of the element.
   */
  private static ByteString getSchemaElement(final Entry schemaEntry,
      final String type, final String oid)
  {
    final Attribute attribute = schemaEntry.getAttribute(type);
    final MatchingRule mrule =
        CoreSchema.getObjectIdentifierFirstComponentMatchingRule();
    Assertion assertion;
    try
    {
      assertion = mrule.getAssertion(ByteString.valueOfUtf8(oid));
      for (final ByteString value : attribute)
      {
        final ByteString nvalue = mrule.normalizeAttributeValue(value);
        if (assertion.matches(nvalue).toBoolean())
        {
          return value;
        }
      }
    }
    catch (DecodeException e)
    {
      throw new IllegalStateException(e);
    }
    throw new IllegalStateException(ERR_UPGRADE_UNKNOWN_OC_ATT.get(type, oid).toString());
  }

  /**
   * Creates a new file in The {@code config/upgrade} folder.
   * The new file is a concatenation of entries of all files contained in the
   * {@code config/schema} folder.
   *
   * @param folder
   *          The folder containing the schema files.
   * @param revision
   *          The revision number of the current binary version.
   * @throws Exception
   *           If we cannot read the files contained in the folder where the
   *           schema files are supposed to be, or the file has errors.
   */
  static void updateConfigUpgradeSchemaFile(final File folder,
      final String revision) throws Exception
  {
    if (!folder.isDirectory())
    {
      return;
    }
    // We need to upgrade the schema.ldif.<rev> file contained in the
    // config/upgrade folder otherwise, we cannot enable the backend at
    // server's start. We need to read all files contained in config/schema
    // and add all attribute/object classes in this new super entry which
    // will be read at start-up.
    Entry theNewSchemaEntry = new LinkedHashMapEntry();
    final FilenameFilter filter = new SchemaUtils.SchemaFileFilter();
    for (final File f : folder.listFiles(filter))
    {
      logger.debug(LocalizableMessage.raw("Processing %s", f.getAbsolutePath()));
      try (LDIFEntryReader reader = new LDIFEntryReader(new FileInputStream(f)))
      {
        while (reader.hasNext())
        {
          final Entry entry = reader.readEntry();
          theNewSchemaEntry.setName(entry.getName());
          for (final Attribute at : entry.getAllAttributes())
          {
            theNewSchemaEntry.addAttribute(at);
          }
        }
      }
      catch (Exception ex)
      {
        throw new Exception("Error parsing existing schema file " + f.getName() + " - " + ex.getMessage(), ex);
      }
    }

    // Creates a File object representing
    // config/upgrade/schema.ldif.revision which the server creates
    // the first time it starts if there are schema customizations.
    final File destination = new File(configDirectory, UPGRADE_PATH + File.separator + "schema.ldif." + revision);

    // Checks if the parent exists (eg. embedded
    // server doesn't seem to provide that folder)
    File parentDirectory = destination.getParentFile();
    if (!parentDirectory.exists())
    {
      logger.debug(LocalizableMessage.raw("Parent file of %s doesn't exist", destination.getPath()));

      parentDirectory.mkdirs();

      logger.debug(LocalizableMessage.raw("Parent directory %s created.", parentDirectory.getPath()));
    }
    if (!destination.exists())
    {
      destination.createNewFile();
    }

    logger.debug(LocalizableMessage.raw("Writing entries in %s.", destination.getAbsolutePath()));

    try (LDIFEntryWriter writer = new LDIFEntryWriter(new FileOutputStream(destination)))
    {
      writer.writeEntry(theNewSchemaEntry);
      logger.debug(LocalizableMessage.raw("%s created and completed successfully.", destination.getAbsolutePath()));
    }
  }

  /**
   * Returns a schema used by upgrade(default octet string matching rule and
   * directory string syntax). Added attribute types which we know we are
   * sensitive to in the unit tests, e.g. ds-cfg-enabled (boolean syntax),
   * ds-cfg-filter(case ingnore), ds-cfg-collation (case ignore)... related to
   * upgrade tasks. See OPENDJ-1245.
   *
   * @return A schema which may used in the upgrade context.
   */
  private static Schema getUpgradeSchema()
  {
    final SchemaBuilder sb = new SchemaBuilder(Schema.getCoreSchema())
        .setOption(DEFAULT_MATCHING_RULE_OID, getCaseExactMatchingRule().getOID())
        .setOption(DEFAULT_SYNTAX_OID, getDirectoryStringSyntax().getOID());

    // Adds ds-cfg-enabled / boolean syntax
    sb.addAttributeType("( 1.3.6.1.4.1.26027.1.1.2 NAME 'ds-cfg-enabled'"
        + " EQUALITY booleanMatch SYNTAX 1.3.6.1.4.1.1466.115.121.1.7"
        + " SINGLE-VALUE X-ORIGIN 'OpenDS Directory Server' )", false);

    // Adds ds-cfg-filter / ignore match syntax
    sb.addAttributeType("( 1.3.6.1.4.1.26027.1.1.279 NAME 'ds-cfg-filter'"
        + " EQUALITY caseIgnoreMatch SYNTAX 1.3.6.1.4.1.1466.115.121.1.15"
        + " X-ORIGIN 'OpenDS Directory Server' )", false);

    // Adds ds-cfg-collation / ignore match syntax
    sb.addAttributeType("( 1.3.6.1.4.1.26027.1.1.500 NAME 'ds-cfg-collation'"
        + " EQUALITY caseIgnoreMatch SYNTAX 1.3.6.1.4.1.1466.115.121.1.15"
        + " X-ORIGIN 'OpenDS Directory Server' )", false);

    return sb.toSchema().asNonStrictSchema();
  }

  private static String[] readLDIFLines(final DN dn,
      final ChangeOperationType changeType, final String... lines)
  {
    final String[] modifiedLines = new String[lines.length + 2];
    if (changeType == MODIFY)
    {
      modifiedLines[0] = "dn: " + dn;
      modifiedLines[1] = "changetype: modify";
    }
    System.arraycopy(lines, 0, modifiedLines, 2, lines.length);
    return modifiedLines;
  }

  /** Returns {@code true} if the installed instance contains at least one JE backend. */
  static boolean instanceContainsJeBackends()
  {
    final SearchRequest sr = Requests.newSearchRequest(
            "", SearchScope.WHOLE_SUBTREE, "(objectclass=ds-cfg-je-backend)", "dn");
    try (final EntryReader entryReader = searchConfigFile(sr))
    {
      return entryReader.hasNext();
    }
    catch (final IOException unlikely)
    {
      logger.error(ERR_UPGRADE_READING_CONF_FILE.get(unlikely.getMessage()));
      return true;
    }
  }

  static void deleteFileIfExists(final File f)
  {
    Reject.ifNull(f);
    if (f.exists())
    {
      f.delete();
    }
  }

  /** Prevent instantiation. */
  private UpgradeUtils()
  {
    throw new AssertionError();
  }
}
