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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.forgerock.opendj.config.server;




import org.forgerock.opendj.adapter.server3x.Converters;
import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.Configuration;
import org.forgerock.opendj.config.ConfigurationClient;
import org.forgerock.opendj.config.DefinitionDecodingException;
import org.forgerock.opendj.config.LDAPProfile;
import org.forgerock.opendj.config.ManagedObjectPath;
import org.forgerock.opendj.config.RelationDefinition;
import org.forgerock.opendj.config.SingletonRelationDefinition;
import org.forgerock.opendj.config.server.ConstraintViolationException;
import org.forgerock.opendj.config.server.ServerManagedObject;
import org.forgerock.opendj.config.server.ServerManagedObjectDecodingException;
import org.forgerock.opendj.config.server.ServerManagementContext;
import org.forgerock.opendj.server.config.meta.RootCfgDefn;
import org.opends.server.types.Entry;



/**
 * This class defines some utility functions which can be used by test
 * cases which interact with the admin framework.
 */
public final class AdminTestCaseUtils {

  /**
   * The relation name which will be used for dummy configurations. A
   * deliberately obfuscated name is chosen to avoid clashes.
   */
  private static final String DUMMY_TEST_RELATION = "*dummy*test*relation*";

  /** Indicates if the dummy relation profile has been registered. */
  private static boolean isProfileRegistered;

  /** Prevent instantiation. */
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
  public static <C extends ConfigurationClient, S extends Configuration> S getConfiguration(
      AbstractManagedObjectDefinition<C, S> definition, Entry entry) throws ConfigException {
    try {
      ServerManagementContext context = new ServerManagementContext(null);
      ServerManagedObject<? extends S> mo = context.decode(getPath(definition), Converters.from(entry));

      // Ensure constraints are satisfied.
      mo.ensureIsUsable();

      return mo.getConfiguration();
    } catch (DefinitionDecodingException e) {
      throw ConfigExceptionFactory.getInstance().createDecodingExceptionAdaptor(Converters.from(entry.getName()), e);
    } catch (ServerManagedObjectDecodingException e) {
      throw ConfigExceptionFactory.getInstance().createDecodingExceptionAdaptor(e);
    } catch (ConstraintViolationException e) {
      throw ConfigExceptionFactory.getInstance()
      .createDecodingExceptionAdaptor(e);
    }
  }



  /** Construct a dummy path. */
  private static synchronized <C extends ConfigurationClient, S extends Configuration>
  ManagedObjectPath<C, S> getPath(AbstractManagedObjectDefinition<C, S> d) {
    if (!isProfileRegistered) {
      LDAPProfile.Wrapper profile = new LDAPProfile.Wrapper() {

        /** {@inheritDoc} */
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

    SingletonRelationDefinition.Builder<C, S> builder = new SingletonRelationDefinition.Builder<>(
        RootCfgDefn.getInstance(), DUMMY_TEST_RELATION, d);
    ManagedObjectPath<?, ?> root = ManagedObjectPath.emptyPath();
    return root.child(builder.getInstance());

  }
}
