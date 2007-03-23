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
package org.opends.server.admin.server;



import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.types.Entry;



/**
 * This class defines some utility functions which can be used by test
 * cases which interact with the admin framework.
 */
public final class AdminTestCaseUtils {

  // Prevent instantiation.
  private AdminTestCaseUtils() {
    // No implementation required.
  }



  /**
   * Decodes a configuration entry into the required type of server
   * configuration.
   *
   * @param <S>
   *          The type of server configuration to be decoded.
   * @param definition
   *          The required definition of the required managed object.
   * @param entry
   *          An entry containing the configuration to be decoded.
   * @return Returns the new server-side configuration.
   * @throws ConfigException
   *           If the entry could not be decoded.
   */
  public static <S extends Configuration> S getConfiguration(
      AbstractManagedObjectDefinition<?, S> definition, Entry entry)
      throws ConfigException {
    ConfigEntry configEntry = new ConfigEntry(entry, null);

    try {
      ServerManagedObject<? extends S> mo = ServerManagedObject
          .decode(ManagedObjectPath.emptyPath(), definition,
              configEntry);
      return mo.getConfiguration();
    } catch (DefinitionDecodingException e) {
      throw ConfigExceptionFactory.getInstance()
          .createDecodingExceptionAdaptor(entry.getDN(), e);
    } catch (ServerManagedObjectDecodingException e) {
      throw ConfigExceptionFactory.getInstance()
          .createDecodingExceptionAdaptor(e);
    }
  }
}
