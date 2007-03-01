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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.installer;

import java.io.File;
import java.io.IOException;

import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.installer.webstart.JnlpProperties;
import org.opends.quicksetup.util.Utils;

/**
 * This is the only class that uses classes in org.opends.server (excluding the
 * case of org.opends.server.util.DynamicConstants and
 * org.opends.server.util.SetupUtils which are already included in
 * quicksetup.jar).
 *
 * Important note: do not include references to the classes in package
 * org.opends.server in the import. These classes must be loaded during
 * Runtime.
 * The code is written in a way that when we execute the code that uses these
 * classes the required jar files are already loaded. However these jar files
 * are not necessarily loaded when we create this class.
 */
class InstallerHelper implements JnlpProperties
{

  /**
   * Invokes the method ConfigureDS.configMain with the provided parameters.
   * @param args the arguments to be passed to ConfigureDS.configMain.
   * @return the return code of the ConfigureDS.configMain method.
   * @throws InstallException if something goes wrong.
   * @see org.opends.server.tools.ConfigureDS.configMain(args).
   */
  public int invokeConfigureServer(String[] args) throws InstallException
  {
    return org.opends.server.tools.ConfigureDS.configMain(args);
  }

  /**
   * Invokes the method ImportLDIF.mainImportLDIF with the provided parameters.
   * @param args the arguments to be passed to ImportLDIF.mainImportLDIF.
   * @return the return code of the ImportLDIF.mainImportLDIF method.
   * @throws InstallException if something goes wrong.
   * @see org.opends.server.tools.ImportLDIF.mainImportLDIF(args).
   */
  public int invokeImportLDIF(String[] args) throws InstallException
  {
    return org.opends.server.tools.ImportLDIF.mainImportLDIF(args);
  }

  /**
   * Returns the Message ID that corresponds to a successfully started server.
   * @return the Message ID that corresponds to a successfully started server.
   */
  public String getStartedId()
  {
    return String.valueOf(org.opends.server.messages.CoreMessages.
        MSGID_DIRECTORY_SERVER_STARTED);
  }

  private String getThrowableMsg(String key, Throwable t)
  {
    return getThrowableMsg(key, null, t);
  }

  private String getThrowableMsg(String key, String[] args, Throwable t)
  {
    return Utils.getThrowableMsg(ResourceProvider.getInstance(), key, args, t);
  }

  /**
   * Creates a template LDIF file with an entry that has as dn the provided
   * baseDn.
   * @param baseDn the dn of the entry that will be created in the LDIF file.
   * @return the File object pointing to the created temporary file.
   * @throws InstallException if something goes wrong.
   */
  public File createBaseEntryTempFile(String baseDn) throws InstallException
  {
    File ldifFile = null;
    try
    {
      ldifFile = File.createTempFile("opends-base-entry", ".ldif");
      ldifFile.deleteOnExit();
    } catch (IOException ioe)
    {
      String failedMsg = getThrowableMsg("error-creating-temp-file", null, ioe);
      throw new InstallException(InstallException.Type.FILE_SYSTEM_ERROR,
          failedMsg, ioe);
    }

    try
    {
      org.opends.server.types.LDIFExportConfig exportConfig =
          new org.opends.server.types.LDIFExportConfig(ldifFile
              .getAbsolutePath(),
              org.opends.server.types.ExistingFileBehavior.OVERWRITE);

      org.opends.server.util.LDIFWriter writer =
          new org.opends.server.util.LDIFWriter(exportConfig);

      org.opends.server.types.DN dn =
        org.opends.server.types.DN.decode(baseDn);
      org.opends.server.types.Entry entry =
          org.opends.server.util.StaticUtils.createEntry(dn);

      writer.writeEntry(entry);
      writer.close();
    } catch (org.opends.server.types.DirectoryException de)
    {
      throw new InstallException(InstallException.Type.CONFIGURATION_ERROR,
          getThrowableMsg("error-importing-ldif", null, de), de);
    } catch (org.opends.server.util.LDIFException le)
    {
      throw new InstallException(InstallException.Type.CONFIGURATION_ERROR,
          getThrowableMsg("error-importing-ldif", null, le), le);
    } catch (IOException ioe)
    {
      throw new InstallException(InstallException.Type.CONFIGURATION_ERROR,
          getThrowableMsg("error-importing-ldif", null, ioe), ioe);
    } catch (Throwable t)
    {
      throw new InstallException(InstallException.Type.BUG, getThrowableMsg(
          "bug-msg", t), t);
    }
    return ldifFile;
  }
}
