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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import static org.opends.messages.LoggerMessages.*;
import static org.opends.server.util.StaticUtils.getFileForPath;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageDescriptor;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.CsvFileAccessLogPublisherCfg;

/** Common Audit publisher which publishes access events to CSV files. */
final class CsvFileAccessLogPublisher
  extends CommonAuditAccessLogPublisher<CsvFileAccessLogPublisherCfg>
  implements ConfigurationChangeListener<CsvFileAccessLogPublisherCfg>
{
  @Override
  boolean shouldLogControlOids()
  {
    return getConfig().isLogControlOids();
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(final CsvFileAccessLogPublisherCfg config)
  {
    setConfig(config);
    return new ConfigChangeResult();
  }

  @Override
  public boolean isConfigurationAcceptable(final CsvFileAccessLogPublisherCfg configuration,
                                           final List<LocalizableMessage> unacceptableReasons)
  {
    return super.isConfigurationAcceptable(configuration, unacceptableReasons)
            && isTamperEvidentConfigurationAcceptable(configuration, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(final CsvFileAccessLogPublisherCfg config,
      final List<LocalizableMessage> unacceptableReasons)
  {
    return isTamperEvidentConfigurationAcceptable(config, unacceptableReasons);
  }

  private boolean isTamperEvidentConfigurationAcceptable(final CsvFileAccessLogPublisherCfg config,
                                                         final List<LocalizableMessage> unacceptableReasons)
  {
    return !config.isTamperEvident()
            || (isKeyStoreFileAcceptable(config, unacceptableReasons)
                && isKeyStorePinFileAcceptable(config, unacceptableReasons));
  }

  private boolean isKeyStorePinFileAcceptable(
          final CsvFileAccessLogPublisherCfg config, final List<LocalizableMessage> unacceptableReasons)
  {
    return isFileAcceptable(config.getKeyStorePinFile(),
                config.dn(),
                ERR_COMMON_AUDIT_KEYSTORE_PIN_FILE_MISSING,
                ERR_COMMON_AUDIT_KEYSTORE_PIN_FILE_CONTAINS_EMPTY_PIN,
                ERR_COMMON_AUDIT_ERROR_READING_KEYSTORE_PIN_FILE,
                unacceptableReasons);
  }

  private boolean isKeyStoreFileAcceptable(
          final CsvFileAccessLogPublisherCfg config, final List<LocalizableMessage> unacceptableReasons)
  {
    return isFileAcceptable(config.getKeyStoreFile(),
                    config.dn(),
                    ERR_COMMON_AUDIT_KEYSTORE_FILE_MISSING,
                    ERR_COMMON_AUDIT_KEYSTORE_FILE_IS_EMPTY,
                    ERR_COMMON_AUDIT_ERROR_READING_KEYSTORE_FILE,
                    unacceptableReasons);
  }

  private boolean isFileAcceptable(
          final String fileName,
          final DN dn,
          final LocalizableMessageDescriptor.Arg2<Object, Object> missingMsg,
          final LocalizableMessageDescriptor.Arg2<Object, Object> emptyMsg,
          final LocalizableMessageDescriptor.Arg3<Object, Object, Object> ioErrorMsg,
          final List<LocalizableMessage> unacceptableReasons)
  {
    final File file = getFileForPath(fileName);
    if (!file.isFile())
    {
      unacceptableReasons.add(missingMsg.get(dn, file));
      return false;
    }
    try
    {
      if (Files.size(file.toPath()) == 0)
      {
        unacceptableReasons.add(emptyMsg.get(dn, file));
        return false;
      }
      return true;
    }
    catch (IOException e)
    {
      unacceptableReasons.add(ioErrorMsg.get(dn, file, stackTraceToSingleLineString(e)));
      return false;
    }
  }
}
