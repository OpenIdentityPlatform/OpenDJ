/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.tools.upgrade;

import java.io.*;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.requests.AddRequest;
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
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SchemaConfigManager;
import org.opends.server.util.ChangeOperationType;
import org.opends.server.util.StaticUtils;

import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.upgrade.FileManager.*;
import static org.opends.server.tools.upgrade.Installation.*;
import static org.opends.server.util.ChangeOperationType.*;

/**
 * Common utility methods needed by the upgrade.
 */
final class UpgradeUtils
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The config folder of the current instance. */
  static final File configDirectory = new File(getInstancePath(),
      Installation.CONFIG_PATH_RELATIVE);

  /** The config/schema folder of the current instance. */
  static final File configSchemaDirectory = new File(
      configDirectory, Installation.SCHEMA_PATH_RELATIVE);

  /** The template folder of the current installation. */
  static final File templateDirectory = new File(getInstallationPath(),
       Installation.TEMPLATE_RELATIVE_PATH);

  /** The template/config folder of the current installation. */
  static final File templateConfigDirectory = new File(templateDirectory,
       Installation.CONFIG_PATH_RELATIVE);

  /** The template/config/schema folder of the current installation. */
  static final File templateConfigSchemaDirectory = new File(
      templateConfigDirectory,
      Installation.SCHEMA_PATH_RELATIVE);

  /** The config/snmp/security folder of the current instance. */
  static final File configSnmpSecurityDirectory = new File(
      configDirectory + File.separator + Installation.SNMP_PATH_RELATIVE
          + File.separator + Installation.SECURITY_PATH_RELATIVE);

  /** The bin folder of the current installation. */
  static final File binDirectory = new File(getInstallationPath(), Installation.UNIX_BINARIES_PATH_RELATIVE);

  /** The bat folder of the current installation. */
  static final File batDirectory = new File(getInstallationPath(), Installation.WINDOWS_BINARIES_PATH_RELATIVE);

  /**
   * Returns the path of the installation of the directory server. Note that
   * this method assumes that this code is being run locally.
   *
   * @return the path of the installation of the directory server.
   */
  static String getInstallPathFromClasspath()
  {
    String installPath = DirectoryServer.getServerRoot();
    if (installPath != null)
    {
      return installPath;
    }

    /* Get the install path from the Class Path */
    final String sep = System.getProperty("path.separator");
    final String[] classPaths =
        System.getProperty("java.class.path").split(sep);
    String path = getInstallPath(classPaths);
    if (path != null)
    {
      final File f = new File(path).getAbsoluteFile();
      final File librariesDir = f.getParentFile();

      /*
       * Do a best effort to avoid having a relative representation (for
       * instance to avoid having ../../../).
       */
      try
      {
        installPath = librariesDir.getParentFile().getCanonicalPath();
      }
      catch (IOException ioe)
      {
        // Best effort
        installPath = librariesDir.getParent();
      }
    }
    return installPath;
  }

  private static String getInstallPath(final String[] classPaths)
  {
    for (String classPath : classPaths)
    {
      final String normPath = classPath.replace(File.separatorChar, '/');
      if (normPath.endsWith(Installation.OPENDJ_BOOTSTRAP_JAR_RELATIVE_PATH))
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
    String instancePathFileName = Installation.INSTANCE_LOCATION_PATH;
    final File _svcScriptPath =
        new File(installPath + File.separator
            + SVC_SCRIPT_FILE_NAME);

    // look for /etc/opt/opendj/instance.loc
    File f = new File(instancePathFileName);
    if (!_svcScriptPath.exists() || !f.exists())
    {
      // look for <installPath>/instance.loc
      instancePathFileName =
          installPath + File.separator
              + Installation.INSTANCE_LOCATION_PATH_RELATIVE;
      f = new File(instancePathFileName);
      if (!f.exists())
      {
        return installPath;
      }
    }

    BufferedReader reader;
    try
    {
      reader = new BufferedReader(new FileReader(instancePathFileName));
    }
    catch (Exception e)
    {
      return installPath;
    }

    // Read the first line and close the file.
    String line;
    try
    {
      line = reader.readLine();
      File instanceLoc = new File(line.trim());
      if (instanceLoc.isAbsolute())
      {
        return instanceLoc.getAbsolutePath();
      }
      else
      {
        return new File(installPath + File.separator + instanceLoc.getPath())
            .getAbsolutePath();
      }
    }
    catch (Exception e)
    {
      return installPath;
    }
    finally
    {
      StaticUtils.close(reader);
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
    String path = null;
    if (f != null)
    {
      try
      {
        /*
         * Do a best effort to avoid having a relative representation (for
         * instance to avoid having ../../../).
         */
        f = f.getCanonicalFile();
      }
      catch (IOException ioe)
      {
        /*
         * This is a best effort to get the best possible representation of the
         * file: reporting the error is not necessary.
         */
      }
      path = f.toString();
    }
    return path;
  }

  /**
   * Returns the absolute path for the given parentPath and relativePath.
   *
   * @param parentPath
   *          the parent path.
   * @param relativePath
   *          the relative path.
   * @return the absolute path for the given parentPath and relativePath.
   */
  static String getPath(final String parentPath,
      final String relativePath)
  {
    return getPath(new File(new File(parentPath), relativePath));
  }

  /**
   * Determines whether one file is the parent of another.
   *
   * @param ancestor
   *          possible parent of <code>descendant</code>
   * @param descendant
   *          possible child 0f <code>ancestor</code>
   * @return return true if ancestor is a parent of descendant
   */
  static boolean isParentOf(final File ancestor, File descendant)
  {
    if (ancestor != null)
    {
      if (ancestor.equals(descendant))
      {
        return false;
      }
      while ((descendant != null) && !ancestor.equals(descendant))
      {
        descendant = descendant.getParentFile();
      }
    }
    return (ancestor != null) && (descendant != null);
  }

  /**
   * Returns <CODE>true</CODE> if the first provided path is under the second
   * path in the file system.
   * @param descendant the descendant candidate path.
   * @param path the path.
   * @return <CODE>true</CODE> if the first provided path is under the second
   * path in the file system; <code>false</code> otherwise or if
   * either of the files are null
   */
  static boolean isDescendant(File descendant, File path) {
    boolean isDescendant = false;
    if (descendant != null && path != null) {
      File parent = descendant.getParentFile();
      while ((parent != null) && !isDescendant) {
        isDescendant = path.equals(parent);
        if (!isDescendant) {
          parent = parent.getParentFile();
        }
      }
    }
    return isDescendant;
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
    if (installPath == null)
    {
      return null;
    }

    return getInstancePathFromInstallPath(installPath);
  }

  /**
   * Returns the server's installation path.
   *
   * @return The server's installation path.
   */
  static String getInstallationPath()
  {
    // The upgrade runs from the bits extracted by BuildExtractor
    // in the staging directory.  However
    // we still want the Installation to point at the build being
    // upgraded so the install path reported in [installroot].
    String installationPath = System.getProperty("INSTALL_ROOT");
    if (installationPath == null)
    {
      final String path = getInstallPathFromClasspath();
      if (path != null)
      {
        final File f = new File(path);
        if (f.getParentFile() != null
            && f.getParentFile().getParentFile() != null
            && new File(f.getParentFile().getParentFile(),
                Installation.LOCKS_PATH_RELATIVE).exists())
        {
          installationPath = getPath(f.getParentFile().getParentFile());
        }
        else
        {
          installationPath = path;
        }
      }
    }
    return installationPath;
  }

  /**
   * Retrieves the backends from the current configuration file. The backends
   * must be enabled to be listed. No operations should be done within a
   * disabled backend.
   *
   * @return A backend list.
   */
  static List<String> getLocalBackendsFromConfig()
  {
    final Schema schema = getUpgradeSchema();

    final List<String> listBackends = new LinkedList<>();
    LDIFEntryReader entryReader = null;
    try
    {
      entryReader =
          new LDIFEntryReader(new FileInputStream(new File(configDirectory,
              CURRENT_CONFIG_FILE_NAME))).setSchema(schema);

      final SearchRequest sr =
          Requests.newSearchRequest("", SearchScope.WHOLE_SUBTREE,
              "(&(objectclass=ds-cfg-local-db-backend)(ds-cfg-enabled=true))",
              "ds-cfg-base-dn");

      final EntryReader resultReader = LDIF.search(entryReader, sr, schema);

      while (resultReader.hasNext())
      {
        final Entry entry = resultReader.readEntry();
        listBackends.add(entry.getAttribute("ds-cfg-base-dn")
            .firstValueAsString());
      }
    }
    catch (Exception ex)
    {
      logger.error(LocalizableMessage.raw(ex.getMessage()));
    }
    finally
    {
      StaticUtils.close(entryReader);
    }

    return listBackends;
  }

  /**
   * Updates the config file during the upgrade process.
   *
   * @param configPath
   *          The original path to the file.
   * @param filter
   *          The filter to select entries. Only useful for modify change type.
   * @param changeType
   *          The change type which must be applied to ldif lines.
   * @param ldifLines
   *          The change record ldif lines.
   *          For ADD change type, the first line must be the dn.
   *          For DELETE change type, the first and only line must be the dn.
   * @throws IOException
   *           If an Exception occurs during the input output methods.
   * @return The changes number that have occurred.
   */
  static int updateConfigFile(final String configPath,
      final Filter filter, final ChangeOperationType changeType,
      final String... ldifLines) throws IOException
  {
    final File original = new File(configPath);
    final File copyConfig =
        File.createTempFile("copyConfig", ".tmp", original.getParentFile());

    int changeCount = 0;
    LDIFEntryReader entryReader = null;
    LDIFEntryWriter writer = null;
    try
    {
      final Schema schema = getUpgradeSchema();
      entryReader =
          new LDIFEntryReader(new FileInputStream(configPath))
              .setSchema(schema);

      writer = new LDIFEntryWriter(new FileOutputStream(copyConfig));
      writer.setWrapColumn(80);

      // Writes the header on the new file.
      writer.writeComment(INFO_CONFIG_FILE_HEADER.get());
      writer.setWrapColumn(0);

      boolean entryAlreadyExist = false;
      DN ldifDN = null;
      if (filter == null && (changeType == ADD || changeType == DELETE))
      {
        // The first line should start with dn:
        ldifDN = DN.valueOf(ldifLines[0].replaceFirst("dn: ", ""));
      }
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
            logger.debug(LocalizableMessage.raw(
                "The following entry has been modified : %s", entryDN));
          }
          catch (Exception ex)
          {
            logger.error(LocalizableMessage.raw(ex.getMessage()));
          }
        }

        if (entryDN.equals(ldifDN))
        {
          logger.debug(LocalizableMessage.raw("Entry %s found", entryDN));
          entryAlreadyExist = true;

          if (changeType == DELETE)
          {
            entry = null;
            changeCount++;
            logger.debug(LocalizableMessage.raw(
                "The following entry has been deleted : %s", entryDN));
          }
        }

        if (entry != null)
        {
          writer.writeEntry(entry);
        }
      }

      if (changeType == ADD && !entryAlreadyExist)
      {
        final AddRequest ar = Requests.newAddRequest(ldifLines);
        writer.writeEntry(ar);
        logger.debug(LocalizableMessage.raw("Entry successfully added %s in %s",
            ldifDN, original.getAbsolutePath()));
        changeCount++;
      }
    }
    catch (Exception ex)
    {
      throw new IOException(ex.getMessage());
    }
    finally
    {
      // The reader and writer must be close before renaming files.
      // Otherwise it causes exceptions under windows OS.
      StaticUtils.close(entryReader, writer);
    }

    try
    {
      // Renaming the file, overwriting previous one.
      rename(copyConfig, new File(configPath));
    }
    catch (IOException e)
    {
      logger.error(LocalizableMessage.raw(e.getMessage()));
      deleteRecursively(original);
      throw e;
    }

    return changeCount;
  }

  /**
   * This task adds new attributes / object classes to the specified destination
   * file. The new attributes and object classes must be originally defined in
   * the template file.
   *
   * @param templateFile
   *          The file in which the new attribute/object definition can be read.
   * @param destination
   *          The file where we want to add the new definitions.
   * @param attributes
   *          Those attributes needed to be inserted into the new destination
   *          file.
   * @param objectClasses
   *          Those object classes needed to be inserted into the new
   *          destination file.
   * @return An integer which represents each time an attribute / object class
   *         is inserted successfully to the destination file.
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
    LDIFEntryReader templateReader = null;
    LDIFEntryReader destinationReader = null;
    LDIFEntryWriter destinationWriter = null;
    File copy = null;
    try
    {
      templateReader = new LDIFEntryReader(new FileInputStream(templateFile));
      if (!templateReader.hasNext())
      {
        // Unless template are corrupted, this should not happen.
        throw new IOException(ERR_UPGRADE_CORRUPTED_TEMPLATE.get(
            templateFile.getPath()).toString());
      }
      final Entry templateSchemaEntry = templateReader.readEntry();

      destinationReader = new LDIFEntryReader(
          new FileInputStream(destination));
      if (!destinationReader.hasNext())
      {
        // Unless template are corrupted, this should not happen.
        throw new IOException(ERR_UPGRADE_CORRUPTED_TEMPLATE.get(
            destination.getPath()).toString());
      }
      final Entry destinationSchemaEntry = destinationReader.readEntry();

      if (attributes != null)
      {
        for (final String att : attributes)
        {
          final ByteString attributeType =
              getSchemaElement(templateSchemaEntry, "attributeTypes", att);
          destinationSchemaEntry.getAttribute("attributeTypes").add(
              attributeType);
          changeCount++;
          logger.debug(LocalizableMessage.raw(String.format("Added %s", attributeType)));
        }
      }

      if (objectClasses != null)
      {
        for (final String oc : objectClasses)
        {
          final ByteString objectClass =
              getSchemaElement(templateSchemaEntry, "objectClasses", oc);
          destinationSchemaEntry.getAttribute("objectClasses").add(objectClass);
          changeCount++;
          logger.trace("Added %s", objectClass);
        }
      }

      // Then writes the new schema entry.
      copy =
          File.createTempFile("copySchema", ".tmp",
              destination.getParentFile());
      final FileOutputStream fos = new FileOutputStream(copy);
      destinationWriter = new LDIFEntryWriter(fos);
      destinationWriter.setWrapColumn(79);
      // Copy comments to fos (get License and first comments only).
      writeFileHeaderComments(templateFile, destinationWriter);
      // Writes the entry after.
      destinationWriter.writeEntry(destinationSchemaEntry);
    }
    finally
    {
      // Readers and writer must be close before writing files.
      // This causes exceptions under windows OS.
      StaticUtils.close(templateReader, destinationReader, destinationWriter);
    }

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
    BufferedReader br = null;
    try
    {
      br = new BufferedReader(new FileReader(file));
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
    finally
    {
      StaticUtils.close(br);
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
      assertion = mrule.getAssertion(ByteString.valueOf(oid));
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
    throw new IllegalStateException(ERR_UPGRADE_UNKNOWN_OC_ATT.get(type, oid)
        .toString());
  }

  /**
   * Creates a new file in the config/upgrade folder. The new file is a
   * concatenation of entries of all files contained in the config/schema
   * folder.
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
    // We need to upgrade the schema.ldif.<rev> file contained in the
    // config/upgrade folder otherwise, we cannot enable the backend at
    // server's start. We need to read all files contained in config/schema
    // and add all attribute/object classes in this new super entry which
    // will be read at start-up.
    Entry theNewSchemaEntry = new LinkedHashMapEntry();
    LDIFEntryReader reader = null;
    LDIFEntryWriter writer = null;
    try
    {
      if (folder.isDirectory())
      {
        final FilenameFilter filter =
            new SchemaConfigManager.SchemaFileFilter();
        for (final File f : folder.listFiles(filter))
        {
          logger.debug(LocalizableMessage.raw(String.format("Processing %s", f
              .getAbsolutePath())));
          reader = new LDIFEntryReader(new FileInputStream(f));
          try
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
            throw new Exception("Error parsing existing schema file "
                + f.getName() + " - " + ex.getMessage(), ex);
          }
        }

        // Creates a File object representing
        // config/upgrade/schema.ldif.revision which the server creates
        // the first time it starts if there are schema customizations.
        final File destination =
            new File(configDirectory, Installation.UPGRADE_PATH
                + File.separator + "schema.ldif." + revision);

        // Checks if the parent exists (eg. embedded
        // server doesn't seem to provide that folder)
        File parentDirectory = destination.getParentFile();
        if (!parentDirectory.exists())
        {
          logger.debug(LocalizableMessage.raw(String.format("Parent file of %s doesn't exist",
              destination.getPath())));

          parentDirectory.mkdirs();

          logger.debug(LocalizableMessage.raw(String.format("Parent directory %s created.",
              parentDirectory.getPath())));
        }
        if (!destination.exists())
        {
          destination.createNewFile();
        }

        logger.debug(LocalizableMessage.raw(String.format("Writing entries in %s.", destination
            .getAbsolutePath())));

        writer = new LDIFEntryWriter(new FileOutputStream(destination));
        writer.writeEntry(theNewSchemaEntry);

        logger.debug(LocalizableMessage.raw(String.format(
            "%s created and completed successfully.", destination
                .getAbsolutePath())));
      }
    }
    finally
    {
      StaticUtils.close(reader, writer);
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
  static Schema getUpgradeSchema() {
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

  /** Prevent instantiation. */
  private UpgradeUtils()
  {
    throw new AssertionError();
  }
}
