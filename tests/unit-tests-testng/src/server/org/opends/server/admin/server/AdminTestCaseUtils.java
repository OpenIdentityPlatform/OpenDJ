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
package org.opends.server.admin.server;



import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.LDAPProfile;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.SingletonRelationDefinition;
import org.opends.server.admin.std.meta.RootCfgDefn;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.types.Entry;



/**
 * This class defines some utility functions which can be used by test
 * cases which interact with the admin framework.
 */
public final class AdminTestCaseUtils {

  // The relation name which will be used for dummy configurations. A
  // deliberately obfuscated name is chosen to avoid clashes.
  private static final String DUMMY_TEST_RELATION = "*dummy*test*relation*";

  // Indicates if the dummy relation profile has been registered.
  private static boolean isProfileRegistered = false;



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
      ServerManagementContext context = ServerManagementContext.getInstance();
      ServerManagedObject<? extends S> mo = context.decode(getPath(definition),
          configEntry);
      
      // Ensure constraints are satisfied.
      mo.ensureIsUsable();
      
      return mo.getConfiguration();
    } catch (DefinitionDecodingException e) {
      throw ConfigExceptionFactory.getInstance()
          .createDecodingExceptionAdaptor(entry.getDN(), e);
    } catch (ServerManagedObjectDecodingException e) {
      throw ConfigExceptionFactory.getInstance()
          .createDecodingExceptionAdaptor(e);
    } catch (ConstraintViolationException e) {
      throw ConfigExceptionFactory.getInstance()
      .createDecodingExceptionAdaptor(e);
    }
  }



  // Construct a dummy path.
  private synchronized static <C extends ConfigurationClient, S extends Configuration>
  ManagedObjectPath<C, S> getPath(AbstractManagedObjectDefinition<C, S> d) {
    if (!isProfileRegistered) {
      LDAPProfile.Wrapper profile = new LDAPProfile.Wrapper() {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getRelationRDNSequence(RelationDefinition<?, ?> r) {
          if (r.getName().equals(DUMMY_TEST_RELATION)) {
            return "cn=dummy configuration,cn=config";
          } else {
            return null;
          }
        }

      };

      LDAPProfile.getInstance().pushWrapper(profile);
      isProfileRegistered = true;
    }

    SingletonRelationDefinition.Builder<C, S> builder =
      new SingletonRelationDefinition.Builder<C, S>(
        RootCfgDefn.getInstance(), DUMMY_TEST_RELATION, d);
    ManagedObjectPath<?, ?> root = ManagedObjectPath.emptyPath();
    return root.child(builder.getInstance());

  }
}
