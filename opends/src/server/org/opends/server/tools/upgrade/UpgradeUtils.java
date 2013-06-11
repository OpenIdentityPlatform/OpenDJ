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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.tools.upgrade;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.upgrade.FileManager.*;
import static org.opends.server.tools.upgrade.Installation.*;
import static org.opends.server.util.ServerConstants.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entries;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.Matcher;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.SchemaValidationPolicy;
import org.forgerock.opendj.ldap.schema.UnknownSchemaElementException;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.opendj.ldif.LDIFEntryWriter;
import org.opends.server.controls.PersistentSearchChangeType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.util.SetupUtils;
import org.opends.server.util.StaticUtils;

/**
 * Common utility methods needed by the upgrade.
 */
final class UpgradeUtils
{

  /**
   * Upgrade's logger.
   */
  private final static Logger LOG = Logger
      .getLogger(UpgradeCli.class.getName());

  /** The config folder of the current installation. */
  static final File configDirectory = new File(getInstallationPath(),
      Installation.CONFIG_PATH_RELATIVE);

  /** The config/schema folder of the current installation. */
  static final File configSchemaDirectory = new File(getInstallationPath(),
      Installation.CONFIG_PATH_RELATIVE + File.separator
          + Installation.SCHEMA_PATH_RELATIVE);

  /** The template folder of the current installation. */
  static final File templateDirectory = new File(getInstallationPath(),
      Installation.CONFIG_PATH_RELATIVE + File.separator
          + Installation.TEMPLATE_RELATIVE_PATH);

  /** The template/config/schema folder of the current installation. */
  static final File templateConfigSchemaDirectory = new File(
      getInstallationPath(), Installation.TEMPLATE_RELATIVE_PATH
          + File.separator + Installation.CONFIG_PATH_RELATIVE
          + File.separator + Installation.SCHEMA_PATH_RELATIVE);

  /** The template/config folder of the current installation. */
  static final File templateConfigDirectory = new File(
      getInstallationPath(), Installation.TEMPLATE_RELATIVE_PATH
          + File.separator + Installation.CONFIG_PATH_RELATIVE);

  /** The config snmp security folder of the current installation. */
  static final File configSnmpSecurityDirectory = new File(
      getInstallationPath(), Installation.CONFIG_PATH_RELATIVE
      + File.separator + Installation.SNMP_PATH_RELATIVE + File.separator
      + Installation.SECURITY_PATH_RELATIVE);

  /**
   * Returns the path of the installation of the directory server. Note that
   * this method assumes that this code is being run locally.
   *
   * @return the path of the installation of the directory server.
   */
  public static String getInstallPathFromClasspath()
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
  public static String getInstancePathFromInstallPath(final String installPath)
  {
    String instancePathFileName = Installation.INSTANCE_LOCATION_PATH;
    final File configureScriptPath =
        new File(installPath + File.separator
            + Installation.UNIX_CONFIGURE_FILE_NAME);

    // look for /etc/opt/opends/instance.loc
    File f = new File(instancePathFileName);
    if (!configureScriptPath.exists() || !f.exists())
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
  public static String getPath(File f)
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
        File canonical = f.getCanonicalFile();
        f = canonical;
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
  public static String getPath(final String parentPath,
      final String relativePath)
  {
    return getPath(new File(new File(parentPath), relativePath));
  }

  /**
   * Returns <CODE>true</CODE> if we are running under windows and
   * <CODE>false</CODE> otherwise.
   *
   * @return <CODE>true</CODE> if we are running under windows and
   *         <CODE>false</CODE> otherwise.
   */
  public static boolean isWindows()
  {
    return SetupUtils.isWindows();
  }

  /**
   * Returns <CODE>true</CODE> if we are running under Unix and
   * <CODE>false</CODE> otherwise.
   *
   * @return <CODE>true</CODE> if we are running under Unix and
   *         <CODE>false</CODE> otherwise.
   */
  public static boolean isUnix()
  {
    return SetupUtils.isUnix();
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
  static public boolean isParentOf(final File ancestor, File descendant)
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
  public static boolean isDescendant(File descendant, File path) {
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
  static final String getInstancePath()
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
  static final String getInstallationPath()
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

  // This function is not in use actually but may be useful later
  // eg. for rebuild index task.
  @SuppressWarnings("unused")
  private static final SortedMap<String, LinkedList<String>> getLocalBackends()
  {
    // Config.ldif path
    final File configLdif = new File(configDirectory,
        CURRENT_CONFIG_FILE_NAME);
    SortedMap<String, LinkedList<String>> result =
        new TreeMap<String, LinkedList<String>>();

    LDIFEntryReader entryReader = null;
    try
    {
      entryReader = new LDIFEntryReader(new FileInputStream(configLdif));
      final Filter filter =
          Filter.equality("objectclass", "ds-cfg-local-db-backend");
      final Matcher includeFilter = filter.matcher();
      entryReader.setIncludeFilter(includeFilter);

      Entry entry = null;
      while (entryReader.hasNext())
      {
        LinkedList<String> dataRelativesToBck = new LinkedList<String>();
        entry = entryReader.readEntry();
        // db path
        dataRelativesToBck.add(entry.getAttribute("ds-cfg-db-directory")
            .firstValueAsString());
        // enabled ?
        dataRelativesToBck.add(entry.getAttribute("ds-cfg-enabled")
            .firstValueAsString());
        // backend name
        result.put(
            entry.getAttribute("ds-cfg-backend-id").firstValueAsString(),
            dataRelativesToBck);
      }
    }
    catch (Exception ex)
    {
      LOG.log(Level.SEVERE, ex.getMessage());
    }
    finally
    {
      StaticUtils.close(entryReader);
    }

    return result;
  }

  /**
   * Updates the config file during the upgrade process.
   *
   * @param configPath
   *          The original path to the file.
   * @param filter
   *          The filter to avoid files.
   * @param changeType
   *          The change type which must be applied to ldif lines.
   * @param lines
   *          The change record ldif lines.
   * @throws IOException
   *           If an Exception occurs during the input output methods.
   * @return The changes number that have occurred.
   */
  static final int updateConfigFile(final String configPath,
      final LDAPFilter filter, final PersistentSearchChangeType changeType,
      final String... lines) throws IOException
  {
    final File original = new File(configPath);
    final File copyConfig =
        File.createTempFile("copyConfig", ".tmp", original.getParentFile());

    int changeCount = 0;
    LDIFEntryReader entryReader = null;
    LDIFEntryWriter writer = null;
    try
    {
      entryReader = new LDIFEntryReader(new FileInputStream(configPath));
      entryReader.setSchemaValidationPolicy(SchemaValidationPolicy.ignoreAll());

      writer = new LDIFEntryWriter(new FileOutputStream(copyConfig));
      writer.setWrapColumn(80);

      // Writes the header on the new file.
      writer.writeComment(INFO_CONFIG_FILE_HEADER.get());
      writer.setWrapColumn(0);

      Entry entry = null;

      boolean alreadyExist = false;
      while (entryReader.hasNext())
      {
        entry = entryReader.readEntry();
        // Searching for the related entries
        if (filter != null
            && Filter.valueOf(filter.toString()).matches(entry)
              == ConditionResult.TRUE)
        {
          try
          {
            final ModifyRequest mr =
                Requests.newModifyRequest(readLDIFLines(entry.getName(),
                    changeType, lines));
            entry = Entries.modifyEntryPermissive(entry, mr.getModifications());
            changeCount++;
            LOG.log(Level.INFO,
                String.format("The following entry has been modified : %s",
                    entry.getName()));
          }
          catch (Exception ex)
          {
            LOG.log(Level.SEVERE, ex.getMessage());
          }
        }
        if (filter == null && changeType == PersistentSearchChangeType.ADD
            && ("dn: " + entry.getName()).equals(lines[0]))
        {
          LOG.log(Level.INFO, String.format("Entry %s found", entry.getName()
              .toString()));
          alreadyExist = true;
        }
        writer.writeEntry(entry);
      }

      if (filter == null && changeType == PersistentSearchChangeType.ADD
          && !alreadyExist)
      {
        final AddRequest ar = Requests.newAddRequest(lines);
        writer.writeEntry(ar);
        LOG.log(Level.INFO, String.format("Entry successfully added %s in %s",
            entry.getName().toString(), original.getAbsolutePath()));
        changeCount++;
      }
    }
    catch (Exception ex)
    {
      throw new IOException(ex.getMessage());
    }
    finally
    {
      // The reader and writer must be close before writing files.
      // This causes exceptions under windozs OS.
      StaticUtils.close(entryReader, writer);
    }

    try
    {
      // Renaming the file, overwriting previous one.
      rename(copyConfig, new File(configPath));
    }
    catch (IOException e)
    {
      LOG.log(Level.SEVERE, e.getMessage());
      deleteRecursively(original);
      throw e;
    }

    return changeCount;
  }

  /**
   * This task adds new attributes / object classes to the specified destination
   * file. The new attributes and object classes must be originaly defined in
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
   */
  static int updateSchemaFile(final File templateFile, final File destination,
      final String[] attributes, final String[] objectClasses)
      throws IOException
  {
    int changeCount = 0;
    LDIFEntryReader reader = null;
    BufferedReader br = null;
    FileWriter fw = null;
    final File copy =
        File.createTempFile("copySchema", ".tmp",
            destination.getParentFile());
    try
    {
      reader = new LDIFEntryReader(new FileInputStream(templateFile));

      final LinkedList<String> definitionsList = new LinkedList<String>();

      final Entry schemaEntry = reader.readEntry();
      Schema schema = null;

      schema =
          new SchemaBuilder(Schema.getCoreSchema())
              .addSchema(schemaEntry, true).toSchema();
      if (attributes != null)
      {
        for (final String att : attributes)
        {
          try
          {
            final String definition =
                "attributeTypes: " + schema.getAttributeType(att);
            definitionsList.add(definition);
            LOG.log(Level.INFO, String.format("Added %s", definition));
          }
          catch (UnknownSchemaElementException e)
          {
            LOG.log(Level.SEVERE, ERR_UPGRADE_UNKNOWN_OC_ATT.get("attribute",
                att).toString());
          }
        }
      }

      if (objectClasses != null)
      {
        for (final String oc : objectClasses)
        {
          try
          {
            final String definition =
                "objectClasses: " + schema.getObjectClass(oc);
            definitionsList.add(definition);
            LOG.log(Level.INFO, String.format("Added %s", definition));
          }
          catch (UnknownSchemaElementException e)
          {
            LOG.log(Level.SEVERE, ERR_UPGRADE_UNKNOWN_OC_ATT.get(
                "object class", oc).toString());
          }
        }
      }
      // Then, open the destination file and write the new attribute
      // or objectClass definitions

      br = new BufferedReader(new FileReader(destination));
      fw = new FileWriter(copy);
      String line = br.readLine();
      while (line != null && !"".equals(line))
      {
        fw.write(line + EOL);
        line = br.readLine();
      }
      for (final String definition : definitionsList)
      {
        writeLine(fw, definition, 80);
        changeCount++;
      }
      // Must be ended with a blank line
      fw.write(EOL);
    }
    finally
    {
      // The reader and writer must be close before writing files.
      // This causes exceptions under windows OS.
      StaticUtils.close(br, fw, reader);
    }

    // Writes the schema file.
    try
    {
      rename(copy, destination);
    }
    catch (IOException e)
    {
      LOG.log(Level.SEVERE, e.getMessage());
      deleteRecursively(copy);
      throw e;
    }

    return changeCount;
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
   * @throws IOException
   *           If we cannot read the files contained in the folder where the
   *           schema files are supposed to be.
   */
  static void updateConfigUpgradeSchemaFile(final File folder,
      final String revision) throws IOException
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
        for (final File f : folder.listFiles())
        {
          LOG.log(Level.INFO, String.format("Processing %s", f
              .getAbsolutePath()));
          reader = new LDIFEntryReader(new FileInputStream(f));
          while (reader.hasNext())
          {
            try
            {
              final Entry entry = reader.readEntry();
              theNewSchemaEntry.setName(entry.getName());
              for (final Attribute at : entry.getAllAttributes())
              {
                theNewSchemaEntry.addAttribute(at);
              }
            }
            catch (Exception ex)
            {
              LOG.log(Level.SEVERE, ex.getMessage());
            }
          }
        }

        // Creates a File object representing
        // config/upgrade/schema.ldif.revision which the server creates
        // the first time it starts if there are schema customizations.
        final File destination =
            new File(configDirectory, Installation.UPGRADE_PATH
                + File.separator + "schema.ldif." + revision);
        writer = new LDIFEntryWriter(new FileOutputStream(destination));
        writer.writeEntry(theNewSchemaEntry);

        LOG.log(Level.INFO, String.format("%s file created successfully.",
            destination.getAbsolutePath()));
      }
    }
    finally
    {
      reader.close();
      writer.close();
    }
  }

  private static String[] readLDIFLines(final DN dn,
      final PersistentSearchChangeType changeType, final String... lines)
  {
    final String[] modifiedLines = new String[lines.length + 2];

    int index = 0;
    if (changeType == PersistentSearchChangeType.MODIFY)
    {
      modifiedLines[0] = "dn: " + dn;
      modifiedLines[1] = "changetype: modify";
      index = 2;
    }
    for (final String line : lines)
    {
      modifiedLines[index] = line;
      index++;
    }
    return modifiedLines;
  }

  private static void writeLine(final FileWriter fw, final String line,
      final int wrapColumn) throws IOException
  {
    final int length = line.length();
    if (length > wrapColumn)
    {
      fw.write(line.subSequence(0, wrapColumn).toString());
      fw.write(EOL);
      int pos = wrapColumn;
      while (pos < length)
      {
        final int writeLength = Math.min(wrapColumn - 1, length - pos);
        fw.write(" ");
        fw.write(line.subSequence(pos, pos + writeLength).toString());
        fw.write(EOL);
        pos += wrapColumn - 1;
      }
    }
    else
    {
      fw.write(line);
      fw.write(EOL);
    }
  }

  // Prevent instantiation.
  private UpgradeUtils()
  {
    throw new AssertionError();
  }
}
