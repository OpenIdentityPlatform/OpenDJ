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
package org.opends.server.api;



import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;


/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server configuration handler.
 */
public abstract class ConfigHandler
       extends Backend
{
  /**
   * Bootstraps this configuration handler using the information in
   * the provided configuration file.  Depending on this configuration
   * handler implementation, the provided file may contain either the
   * entire server configuration or information that is needed to
   * access the configuration in some other location or repository.
   *
   * @param  configFile   The path to the file to use to initialize
   *                      this configuration handler.
   * @param  checkSchema  Indicates whether to perform schema checking
   *                      on the configuration data.
   *
   * @throws  InitializationException  If a problem occurs while
   *                                   attempting to initialize this
   *                                   configuration handler.
   */
  public abstract void initializeConfigHandler(String configFile,
                                               boolean checkSchema)
         throws InitializationException;



  /**
   * Finalizes this configuration handler so that it will release any
   * resources associated with it so that it will no longer be used.
   * This will be called when the Directory Server is shutting down,
   * as well as in the startup process once the schema has been read
   * so that the configuration can be re-read using the updated
   * schema.
   */
  public abstract void finalizeConfigHandler();



  /**
   * Retrieves the entry that is at the root of the Directory Server
   * configuration.
   *
   * @return  The entry that is at the root of the Directory Server
   *          configuration.
   *
   * @throws  ConfigException  If a problem occurs while interacting
   *                           with the configuration.
   */
  public abstract ConfigEntry getConfigRootEntry()
         throws ConfigException;



  /**
   * Retrieves the requested entry from the configuration.
   *
   * @param  entryDN  The distinguished name of the configuration
   *                  entry to retrieve.
   *
   * @return  The requested configuration entry.
   *
   * @throws  ConfigException  If a problem occurs while interacting
   *                           with the configuration.
   */
  public abstract ConfigEntry getConfigEntry(DN entryDN)
         throws ConfigException;



  /**
   * Retrieves the absolute path of the Directory Server instance
   * root.
   *
   * @return  The absolute path of the Directory Server instance root.
   */
  public abstract String getServerRoot();



  /**
   * Writes an updated version of the Directory Server configuration
   * to the repository.  This should ensure that the stored
   * configuration matches the pending configuration.
   *
   * @throws  DirectoryException  If a problem is encountered while
   *                              writing the updated configuration.
   */
  public abstract void writeUpdatedConfig()
         throws DirectoryException;
}

