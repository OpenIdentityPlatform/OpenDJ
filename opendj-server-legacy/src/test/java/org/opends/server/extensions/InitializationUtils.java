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
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.adapter.server3x.Converters;
import org.forgerock.opendj.config.Configuration;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.config.server.AdminTestCaseUtils;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ServerManagementContext;
import org.forgerock.opendj.server.config.server.AccountStatusNotificationHandlerCfg;
import org.forgerock.opendj.server.config.server.CertificateMapperCfg;
import org.forgerock.opendj.server.config.server.ExtendedOperationHandlerCfg;
import org.forgerock.opendj.server.config.server.IdentityMapperCfg;
import org.forgerock.opendj.server.config.server.KeyManagerProviderCfg;
import org.forgerock.opendj.server.config.server.PasswordGeneratorCfg;
import org.forgerock.opendj.server.config.server.PasswordStorageSchemeCfg;
import org.forgerock.opendj.server.config.server.PasswordValidatorCfg;
import org.forgerock.opendj.server.config.server.PluginCfg;
import org.forgerock.opendj.server.config.server.SASLMechanismHandlerCfg;
import org.forgerock.opendj.server.config.server.TrustManagerProviderCfg;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.AccountStatusNotificationHandler;
import org.opends.server.api.CertificateMapper;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.api.IdentityMapper;
import org.opends.server.api.KeyManagerProvider;
import org.opends.server.api.PasswordGenerator;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.api.PasswordValidator;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.api.TrustManagerProvider;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;

public final class InitializationUtils {

  private InitializationUtils() {
    // private to prevent instantiation of util class
  }

  public static <M extends CertificateMapper<C>, C extends CertificateMapperCfg> M initializeCertificateMapper(
      M mapper, Entry e, ManagedObjectDefinition<?, C> cfgDefn) throws ConfigException, InitializationException {
    mapper.initializeCertificateMapper(getConfiguration(cfgDefn, e));
    return mapper;
  }

  public static <H extends ExtendedOperationHandler<C>, C extends ExtendedOperationHandlerCfg> H
      initializeExtendedOperationHandler(
          H handler, Entry e, ManagedObjectDefinition<?, C> cfgDefn) throws ConfigException, InitializationException {
    handler.initializeExtendedOperationHandler(getConfiguration(cfgDefn, e));
    return handler;
  }

  public static <P extends KeyManagerProvider<C>, C extends KeyManagerProviderCfg> P initializeKeyManagerProvider(
      P provider, Entry e, ManagedObjectDefinition<?, C> cfgDefn) throws ConfigException, InitializationException {
    provider.initializeKeyManagerProvider(getConfiguration(cfgDefn, e));
    return provider;
  }

  public static <M extends IdentityMapper<C>, C extends IdentityMapperCfg> M initializeIdentityMapper(
      M mapper, Entry e, ManagedObjectDefinition<?, C> cfgDefn) throws ConfigException, InitializationException {
    mapper.initializeIdentityMapper(getConfiguration(cfgDefn, e));
    return mapper;
  }

  public static <G extends AccountStatusNotificationHandler<C>, C extends AccountStatusNotificationHandlerCfg>
      G initializeStatusNotificationHandler(G generator, Entry e, ManagedObjectDefinition<?, C> cfgDefn)
        throws ConfigException, InitializationException {
    generator.initializeStatusNotificationHandler(getConfiguration(cfgDefn, e));
    return generator;
  }

  public static <G extends PasswordGenerator<C>, C extends PasswordGeneratorCfg> G initializePasswordGenerator(
      G generator, Entry e, ManagedObjectDefinition<?, C> cfgDefn) throws ConfigException, InitializationException {
    generator.initializePasswordGenerator(getConfiguration(cfgDefn, e));
    return generator;
  }

  public static <S extends PasswordStorageScheme<C>, C extends PasswordStorageSchemeCfg>
      S initializePasswordStorageScheme(S scheme, Entry cfgEntry, ManagedObjectDefinition<?, C> cfgDefn)
        throws ConfigException, InitializationException {
    scheme.initializePasswordStorageScheme(getConfiguration(cfgDefn, cfgEntry));
    return scheme;
  }

  public static <V extends PasswordValidator<C>, C extends PasswordValidatorCfg> V initializePasswordValidator(
        V validator, Entry cfgEntry, ManagedObjectDefinition<?, C> cfgDefn)
            throws ConfigException, InitializationException {
    validator.initializePasswordValidator(getConfiguration(cfgDefn, cfgEntry));
    return validator;
  }

  public static <P extends DirectoryServerPlugin<C>, C extends PluginCfg> P initializePlugin(
      P plugin, Entry e, ManagedObjectDefinition<?, C> cfgDefn) throws ConfigException, InitializationException {
    plugin.initializePlugin(TestCaseUtils.getPluginTypes(e), getConfiguration(cfgDefn, e));
    return plugin;
  }

  public static <H extends SASLMechanismHandler<C>,C extends SASLMechanismHandlerCfg> H initializeSASLMechanismHandler(
      H handler, Entry e, ManagedObjectDefinition<?, C> cfgDefn) throws ConfigException, InitializationException {
    handler.initializeSASLMechanismHandler(getConfiguration(cfgDefn, e));
    return handler;
  }

  public static <P extends TrustManagerProvider<C>,C extends TrustManagerProviderCfg> P initializeTrustManagerProvider(
      P provider, Entry e, ManagedObjectDefinition<?, C> cfgDefn) throws ConfigException, InitializationException {
    provider.initializeTrustManagerProvider(getConfiguration(cfgDefn, e));
    return provider;
  }

  public static <C extends Configuration> C getConfiguration(ManagedObjectDefinition<?, C> cfgDefn, Entry cfgEntry)
      throws ConfigException {
    ServerManagementContext context = DirectoryServer.getInstance().getServerContext().getServerManagementContext();
    try {
      return AdminTestCaseUtils.getConfiguration(context, cfgDefn, Converters.from(cfgEntry));
    } catch (IllegalArgumentException e) {
      throw new ConfigException(LocalizableMessage.raw(""), e);
    }
  }
}
