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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import static org.opends.messages.LoggerMessages.ERR_COMMON_AUDIT_INVALID_TRANSACTION_ID;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.util.Utils;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.meta.FileBasedAccessLogPublisherCfgDefn.LogFormat;
import org.forgerock.opendj.server.config.server.FileBasedAccessLogPublisherCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.controls.TransactionIdControl;
import org.opends.server.core.AbandonOperation;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BindOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.core.UnbindOperation;
import org.opends.server.types.AdditionalLogItem;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.FilePermission;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Operation;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.TimeThread;

/** This class provides the implementation of the access logger used by the directory server. */
public final class TextAccessLogPublisher extends
    AbstractTextAccessLogPublisher<FileBasedAccessLogPublisherCfg> implements
    ConfigurationChangeListener<FileBasedAccessLogPublisherCfg>
{
  /** The category to use when logging responses. */
  private static final String CATEGORY_RESPONSE = "RES";
  /** The category to use when logging requests. */
  private static final String CATEGORY_REQUEST = "REQ";

  /**
   * Returns an instance of the text access log publisher that will print all
   * messages to the provided writer. This is used to print the messages to the
   * console when the server starts up.
   *
   * @param writer
   *          The text writer where the message will be written to.
   * @param suppressInternal
   *          Indicates whether to suppress internal operations.
   * @return The instance of the text error log publisher that will print all
   *         messages to standard out.
   */
  public static TextAccessLogPublisher getStartupTextAccessPublisher(
      final TextWriter writer, final boolean suppressInternal)
  {
    final TextAccessLogPublisher startupPublisher = new TextAccessLogPublisher();
    startupPublisher.writer = writer;
    startupPublisher.buildFilters(suppressInternal);
    return startupPublisher;
  }

  private TextWriter writer;
  private FileBasedAccessLogPublisherCfg cfg;
  private boolean isCombinedMode;
  private boolean includeControlOIDs;
  private String timeStampFormat = "dd/MMM/yyyy:HH:mm:ss Z";
  private ServerContext serverContext;

  @Override
  public ConfigChangeResult applyConfigurationChange(FileBasedAccessLogPublisherCfg config)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    try
    {
      // Determine the writer we are using. If we were writing asynchronously,
      // we need to modify the underlying writer.
      TextWriter currentWriter;
      if (writer instanceof AsynchronousTextWriter)
      {
        currentWriter = ((AsynchronousTextWriter) writer).getWrappedWriter();
      }
      else
      {
        currentWriter = writer;
      }

      if (currentWriter instanceof MultifileTextWriter)
      {
        final MultifileTextWriter mfWriter = (MultifileTextWriter) currentWriter;
        configure(mfWriter, config);

        if (config.isAsynchronous())
        {
          if (writer instanceof AsynchronousTextWriter)
          {
            if (hasAsyncConfigChanged(config))
            {
              // reinstantiate
              final AsynchronousTextWriter previousWriter = (AsynchronousTextWriter) writer;
              writer = newAsyncWriter(mfWriter, config);
              previousWriter.shutdown(false);
            }
          }
          else
          {
            // turn async text writer on
            writer = newAsyncWriter(mfWriter, config);
          }
        }
        else
        {
          if (writer instanceof AsynchronousTextWriter)
          {
            // asynchronous is being turned off, remove async text writers.
            final AsynchronousTextWriter previousWriter = (AsynchronousTextWriter) writer;
            writer = mfWriter;
            previousWriter.shutdown(false);
          }
        }

        if (cfg.isAsynchronous() && config.isAsynchronous()
            && cfg.getQueueSize() != config.getQueueSize())
        {
          ccr.setAdminActionRequired(true);
        }

        if (!config.getLogRecordTimeFormat().equals(timeStampFormat))
        {
          TimeThread.removeUserDefinedFormatter(timeStampFormat);
          timeStampFormat = config.getLogRecordTimeFormat();
        }

        cfg = config;
        isCombinedMode = cfg.getLogFormat() == LogFormat.COMBINED;
        includeControlOIDs = cfg.isLogControlOids();
      }
    }
    catch (final Exception e)
    {
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
      ccr.addMessage(ERR_CONFIG_LOGGING_CANNOT_CREATE_WRITER.get(
          config.dn(), stackTraceToSingleLineString(e)));
    }

    return ccr;
  }

  private void configure(MultifileTextWriter mfWriter, FileBasedAccessLogPublisherCfg config) throws DirectoryException
  {
    final FilePermission perm = FilePermission.decodeUNIXMode(config.getLogFilePermissions());
    final boolean writerAutoFlush = config.isAutoFlush() && !config.isAsynchronous();

    final File logFile = getLogFile(config);
    final FileNamingPolicy fnPolicy = new TimeStampNaming(logFile);

    mfWriter.setNamingPolicy(fnPolicy);
    mfWriter.setFilePermissions(perm);
    mfWriter.setAppend(config.isAppend());
    mfWriter.setAutoFlush(writerAutoFlush);
    mfWriter.setBufferSize((int) config.getBufferSize());
    mfWriter.setInterval(config.getTimeInterval());

    mfWriter.removeAllRetentionPolicies();
    mfWriter.removeAllRotationPolicies();
    for (final DN dn : config.getRotationPolicyDNs())
    {
      mfWriter.addRotationPolicy(DirectoryServer.getRotationPolicy(dn));
    }
    for (final DN dn : config.getRetentionPolicyDNs())
    {
      mfWriter.addRetentionPolicy(DirectoryServer.getRetentionPolicy(dn));
    }
  }

  private File getLogFile(final FileBasedAccessLogPublisherCfg config)
  {
    return getFileForPath(config.getLogFile());
  }

  @Override
  public void initializeLogPublisher(final FileBasedAccessLogPublisherCfg cfg, ServerContext serverContext)
      throws ConfigException, InitializationException
  {
    this.serverContext = serverContext;
    final File logFile = getLogFile(cfg);
    final FileNamingPolicy fnPolicy = new TimeStampNaming(logFile);

    try
    {
      final FilePermission perm = FilePermission.decodeUNIXMode(cfg.getLogFilePermissions());
      final LogPublisherErrorHandler errorHandler = new LogPublisherErrorHandler(cfg.dn());
      final boolean writerAutoFlush = cfg.isAutoFlush() && !cfg.isAsynchronous();

      final MultifileTextWriter theWriter = new MultifileTextWriter(
          "Multifile Text Writer for " + cfg.dn(),
          cfg.getTimeInterval(), fnPolicy, perm, errorHandler, "UTF-8",
          writerAutoFlush, cfg.isAppend(), (int) cfg.getBufferSize());

      // Validate retention and rotation policies.
      for (final DN dn : cfg.getRotationPolicyDNs())
      {
        theWriter.addRotationPolicy(DirectoryServer.getRotationPolicy(dn));
      }
      for (final DN dn : cfg.getRetentionPolicyDNs())
      {
        theWriter.addRetentionPolicy(DirectoryServer.getRetentionPolicy(dn));
      }

      if (cfg.isAsynchronous())
      {
        this.writer = newAsyncWriter(theWriter, cfg);
      }
      else
      {
        this.writer = theWriter;
      }
    }
    catch (final DirectoryException e)
    {
      throw new InitializationException(
          ERR_CONFIG_LOGGING_CANNOT_CREATE_WRITER.get(cfg.dn(), e), e);
    }
    catch (final IOException e)
    {
      throw new InitializationException(
          ERR_CONFIG_LOGGING_CANNOT_OPEN_FILE.get(logFile, cfg.dn(), e), e);
    }

    initializeFilters(cfg);

    this.cfg = cfg;
    isCombinedMode = cfg.getLogFormat() == LogFormat.COMBINED;
    includeControlOIDs = cfg.isLogControlOids();
    timeStampFormat = cfg.getLogRecordTimeFormat();

    cfg.addFileBasedAccessChangeListener(this);
  }

  private boolean hasAsyncConfigChanged(FileBasedAccessLogPublisherCfg newConfig)
  {
    return hasParallelConfigChanged(newConfig) && cfg.getQueueSize() != newConfig.getQueueSize();
  }

  private boolean hasParallelConfigChanged(FileBasedAccessLogPublisherCfg newConfig)
  {
    return !cfg.dn().equals(newConfig.dn()) && cfg.isAutoFlush() != newConfig.isAutoFlush();
  }

  private AsynchronousTextWriter newAsyncWriter(MultifileTextWriter mfWriter, FileBasedAccessLogPublisherCfg config)
  {
    String name = "Asynchronous Text Writer for " + config.dn();
    return new AsynchronousTextWriter(name, config.getQueueSize(), config.isAutoFlush(), mfWriter);
  }

  @Override
  public boolean isConfigurationAcceptable(
      final FileBasedAccessLogPublisherCfg configuration,
      final List<LocalizableMessage> unacceptableReasons)
  {
    return isFilterConfigurationAcceptable(configuration, unacceptableReasons)
        && isConfigurationChangeAcceptable(configuration, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
      final FileBasedAccessLogPublisherCfg config,
      final List<LocalizableMessage> unacceptableReasons)
  {
    // Validate the time-stamp formatter.
    final String formatString = config.getLogRecordTimeFormat();
    try
    {
       new SimpleDateFormat(formatString);
    }
    catch (final Exception e)
    {
      final LocalizableMessage message = ERR_CONFIG_LOGGING_INVALID_TIME_FORMAT.get(formatString);
      unacceptableReasons.add(message);
      return false;
    }

    // Make sure the permission is valid.
    try
    {
      final FilePermission filePerm = FilePermission.decodeUNIXMode(config.getLogFilePermissions());
      if (!filePerm.isOwnerWritable())
      {
        final LocalizableMessage message = ERR_CONFIG_LOGGING_INSANE_MODE.get(config.getLogFilePermissions());
        unacceptableReasons.add(message);
        return false;
      }
    }
    catch (final DirectoryException e)
    {
      unacceptableReasons.add(ERR_CONFIG_LOGGING_MODE_INVALID.get(config.getLogFilePermissions(), e));
      return false;
    }

    return true;
  }

  @Override
  public void logAbandonRequest(final AbandonOperation abandonOperation)
  {
    if (isCombinedMode || !isRequestLoggable(abandonOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(abandonOperation, "ABANDON", CATEGORY_REQUEST, buffer);
    appendAbandonRequest(abandonOperation, buffer);
    writer.writeRecord(buffer.toString());
  }

  @Override
  public void logAbandonResult(final AbandonOperation abandonOperation)
  {
    if (!isResponseLoggable(abandonOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(abandonOperation, "ABANDON", CATEGORY_RESPONSE, buffer);
    if (isCombinedMode)
    {
      appendAbandonRequest(abandonOperation, buffer);
    }
    appendResultCodeAndMessage(buffer, abandonOperation);
    logAdditionalLogItems(abandonOperation, buffer);
    appendEtime(buffer, abandonOperation);
    writer.writeRecord(buffer.toString());
  }

  @Override
  public void logAddRequest(final AddOperation addOperation)
  {
    if (isCombinedMode || !isRequestLoggable(addOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(addOperation, "ADD", CATEGORY_REQUEST, buffer);
    appendAddRequest(addOperation, buffer);
    writer.writeRecord(buffer.toString());
  }

  @Override
  public void logAddResponse(final AddOperation addOperation)
  {
    if (!isResponseLoggable(addOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(addOperation, "ADD", CATEGORY_RESPONSE, buffer);
    if (isCombinedMode)
    {
      appendAddRequest(addOperation, buffer);
    }
    appendResultCodeAndMessage(buffer, addOperation);
    logAdditionalLogItems(addOperation, buffer);
    appendLabelIfNotNull(buffer, "authzDN", addOperation.getProxiedAuthorizationDN());
    appendEtime(buffer, addOperation);
    writer.writeRecord(buffer.toString());
  }

  @Override
  public void logBindRequest(final BindOperation bindOperation)
  {
    if (isCombinedMode || !isRequestLoggable(bindOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(bindOperation, "BIND", CATEGORY_REQUEST, buffer);
    appendBindRequest(bindOperation, buffer);
    writer.writeRecord(buffer.toString());
  }

  @Override
  public void logBindResponse(final BindOperation bindOperation)
  {
    if (!isResponseLoggable(bindOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(bindOperation, "BIND", CATEGORY_RESPONSE, buffer);
    if (isCombinedMode)
    {
      appendBindRequest(bindOperation, buffer);
    }
    appendResultCodeAndMessage(buffer, bindOperation);

    final LocalizableMessage failureMessage = bindOperation.getAuthFailureReason();
    if (failureMessage != null)
    {
      // this code path is mutually exclusive with the if result code is success down below
      appendLabel(buffer, "authFailureReason", failureMessage);
      if (bindOperation.getSASLMechanism() != null
          && bindOperation.getSASLAuthUserEntry() != null)
      { // SASL bind and we have successfully found a user entry for auth
        appendLabel(buffer, "authDN", bindOperation.getSASLAuthUserEntry().getName());
      }
      else
      { // SASL bind failed to find user entry for auth or simple bind
        appendLabel(buffer, "authDN", bindOperation.getRawBindDN());
      }
    }

    logAdditionalLogItems(bindOperation, buffer);

    if (bindOperation.getResultCode() == ResultCode.SUCCESS)
    {
      // this code path is mutually exclusive with the if failure message that exists just above
      final AuthenticationInfo authInfo = bindOperation.getAuthenticationInfo();
      if (authInfo != null)
      {
        final DN authDN = authInfo.getAuthenticationDN();
        if (authDN != null)
        {
          appendLabel(buffer, "authDN", authDN);

          final DN authzDN = authInfo.getAuthorizationDN();
          if (!authDN.equals(authzDN))
          {
            appendLabel(buffer, "authzDN", authzDN);
          }
        }
        else
        {
          buffer.append(" authDN=\"\"");
        }
      }
    }

    appendEtime(buffer, bindOperation);

    writer.writeRecord(buffer.toString());
  }

  @Override
  public void logCompareRequest(final CompareOperation compareOperation)
  {
    if (isCombinedMode || !isRequestLoggable(compareOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(compareOperation, "COMPARE", CATEGORY_REQUEST, buffer);
    appendCompareRequest(compareOperation, buffer);
    writer.writeRecord(buffer.toString());
  }

  @Override
  public void logCompareResponse(final CompareOperation compareOperation)
  {
    if (!isResponseLoggable(compareOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(compareOperation, "COMPARE", CATEGORY_RESPONSE, buffer);
    if (isCombinedMode)
    {
      appendCompareRequest(compareOperation, buffer);
    }
    appendResultCodeAndMessage(buffer, compareOperation);
    logAdditionalLogItems(compareOperation, buffer);
    appendLabelIfNotNull(buffer, "authzDN", compareOperation.getProxiedAuthorizationDN());
    appendEtime(buffer, compareOperation);
    writer.writeRecord(buffer.toString());
  }

  @Override
  public void logConnect(final ClientConnection clientConnection)
  {
    if (!isConnectLoggable(clientConnection))
    {
      return;
    }

    final long connectionID = clientConnection.getConnectionID();
    final StringBuilder buffer = new StringBuilder(100);
    buffer.append('[').append(TimeThread.getUserDefinedTime(timeStampFormat)).append(']');
    buffer.append(" CONNECT conn=").append(connectionID);
    buffer.append(" from=").append(clientConnection.getClientHostPort());
    buffer.append(" to=").append(clientConnection.getServerHostPort());
    buffer.append(" protocol=").append(clientConnection.getProtocol());

    writer.writeRecord(buffer.toString());
  }

  @Override
  public void logDeleteRequest(final DeleteOperation deleteOperation)
  {
    if (isCombinedMode || !isRequestLoggable(deleteOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(deleteOperation, "DELETE", CATEGORY_REQUEST, buffer);
    appendDeleteRequest(deleteOperation, buffer);
    writer.writeRecord(buffer.toString());
  }

  @Override
  public void logDeleteResponse(final DeleteOperation deleteOperation)
  {
    if (!isResponseLoggable(deleteOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(deleteOperation, "DELETE", CATEGORY_RESPONSE, buffer);
    if (isCombinedMode)
    {
      appendDeleteRequest(deleteOperation, buffer);
    }
    appendResultCodeAndMessage(buffer, deleteOperation);
    logAdditionalLogItems(deleteOperation, buffer);
    appendLabelIfNotNull(buffer, "authzDN", deleteOperation.getProxiedAuthorizationDN());
    appendEtime(buffer, deleteOperation);
    writer.writeRecord(buffer.toString());
  }

  @Override
  public void logDisconnect(final ClientConnection clientConnection,
      final DisconnectReason disconnectReason, final LocalizableMessage message)
  {
    if (!isDisconnectLoggable(clientConnection))
    {
      return;
    }

    final long connectionID = clientConnection.getConnectionID();
    final StringBuilder buffer = new StringBuilder(100);
    buffer.append('[').append(TimeThread.getUserDefinedTime(timeStampFormat)).append(']');
    buffer.append(" DISCONNECT conn=").append(connectionID);
    appendLabel(buffer, "reason", disconnectReason);
    appendLabelIfNotNull(buffer, "msg", message);

    writer.writeRecord(buffer.toString());
  }

  @Override
  public void logExtendedRequest(final ExtendedOperation extendedOperation)
  {
    if (isCombinedMode || !isRequestLoggable(extendedOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(extendedOperation, "EXTENDED", CATEGORY_REQUEST, buffer);
    appendExtendedRequest(extendedOperation, buffer);
    writer.writeRecord(buffer.toString());
  }

  @Override
  public void logExtendedResponse(final ExtendedOperation extendedOperation)
  {
    if (!isResponseLoggable(extendedOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(extendedOperation, "EXTENDED", CATEGORY_RESPONSE, buffer);
    if (isCombinedMode)
    {
      appendExtendedRequest(extendedOperation, buffer);
    }

    final String oid = extendedOperation.getResponseOID();
    if (oid != null)
    {
      final ExtendedOperationHandler<?> extOpHandler = DirectoryServer
          .getExtendedOperationHandler(oid);
      if (extOpHandler != null)
      {
        String name = extOpHandler.getExtendedOperationName();
        appendLabelIfNotNull(buffer, "name", name);
      }
      appendLabel(buffer, "oid", oid);
    }
    appendResultCodeAndMessage(buffer, extendedOperation);
    logAdditionalLogItems(extendedOperation, buffer);
    appendEtime(buffer, extendedOperation);

    writer.writeRecord(buffer.toString());
  }

  @Override
  public void logModifyDNRequest(final ModifyDNOperation modifyDNOperation)
  {
    if (isCombinedMode || !isRequestLoggable(modifyDNOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(modifyDNOperation, "MODIFYDN", CATEGORY_REQUEST, buffer);
    appendModifyDNRequest(modifyDNOperation, buffer);
    writer.writeRecord(buffer.toString());
  }

  @Override
  public void logModifyDNResponse(final ModifyDNOperation modifyDNOperation)
  {
    if (!isResponseLoggable(modifyDNOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(modifyDNOperation, "MODIFYDN", CATEGORY_RESPONSE, buffer);
    if (isCombinedMode)
    {
      appendModifyDNRequest(modifyDNOperation, buffer);
    }
    appendResultCodeAndMessage(buffer, modifyDNOperation);
    logAdditionalLogItems(modifyDNOperation, buffer);
    appendLabelIfNotNull(buffer, "authzDN", modifyDNOperation.getProxiedAuthorizationDN());
    appendEtime(buffer, modifyDNOperation);
    writer.writeRecord(buffer.toString());
  }

  @Override
  public void logModifyRequest(final ModifyOperation modifyOperation)
  {
    if (isCombinedMode || !isRequestLoggable(modifyOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(modifyOperation, "MODIFY", CATEGORY_REQUEST, buffer);
    appendModifyRequest(modifyOperation, buffer);
    writer.writeRecord(buffer.toString());
  }

  @Override
  public void logModifyResponse(final ModifyOperation modifyOperation)
  {
    if (!isResponseLoggable(modifyOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(modifyOperation, "MODIFY", CATEGORY_RESPONSE, buffer);
    if (isCombinedMode)
    {
      appendModifyRequest(modifyOperation, buffer);
    }
    appendResultCodeAndMessage(buffer, modifyOperation);
    logAdditionalLogItems(modifyOperation, buffer);
    appendLabelIfNotNull(buffer, "authzDN", modifyOperation.getProxiedAuthorizationDN());
    appendEtime(buffer, modifyOperation);
    writer.writeRecord(buffer.toString());
  }

  @Override
  public void logSearchRequest(final SearchOperation searchOperation)
  {
    if (isCombinedMode || !isRequestLoggable(searchOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(192);
    appendHeader(searchOperation, "SEARCH", CATEGORY_REQUEST, buffer);
    appendSearchRequest(searchOperation, buffer);
    writer.writeRecord(buffer.toString());
  }

  @Override
  public void logSearchResultDone(final SearchOperation searchOperation)
  {
    if (!isResponseLoggable(searchOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(128);
    appendHeader(searchOperation, "SEARCH", CATEGORY_RESPONSE, buffer);
    if (isCombinedMode)
    {
      appendSearchRequest(searchOperation, buffer);
    }
    appendResultCodeAndMessage(buffer, searchOperation);
    buffer.append(" nentries=").append(searchOperation.getEntriesSent());
    logAdditionalLogItems(searchOperation, buffer);
    appendLabelIfNotNull(buffer, "authzDN", searchOperation.getProxiedAuthorizationDN());
    appendEtime(buffer, searchOperation);
    writer.writeRecord(buffer.toString());
  }

  @Override
  public void logUnbind(final UnbindOperation unbindOperation)
  {
    if (!isRequestLoggable(unbindOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(unbindOperation, "UNBIND", CATEGORY_REQUEST, buffer);
    if (unbindOperation.isSynchronizationOperation())
    {
      buffer.append(" type=synchronization");
    }

    writer.writeRecord(buffer.toString());
  }

  @Override
  protected void close0()
  {
    writer.shutdown();
    TimeThread.removeUserDefinedFormatter(timeStampFormat);
    if (cfg != null)
    {
      cfg.removeFileBasedAccessChangeListener(this);
    }
  }

  private void appendAbandonRequest(final AbandonOperation abandonOperation, final StringBuilder buffer)
  {
    buffer.append(" idToAbandon=");
    buffer.append(abandonOperation.getIDToAbandon());
    appendRequestControls(abandonOperation, buffer);
    if (abandonOperation.isSynchronizationOperation())
    {
      buffer.append(" type=synchronization");
    }
  }

  private void appendAddRequest(final AddOperation addOperation, final StringBuilder buffer)
  {
    appendLabel(buffer, "dn", addOperation.getRawEntryDN());
    appendRequestControls(addOperation, buffer);
    if (addOperation.isSynchronizationOperation())
    {
      buffer.append(" type=synchronization");
    }
  }

  private void appendBindRequest(final BindOperation bindOperation, final StringBuilder buffer)
  {
    final String protocolVersion = bindOperation.getProtocolVersion();
    if (protocolVersion != null)
    {
      buffer.append(" version=");
      buffer.append(protocolVersion);
    }

    switch (bindOperation.getAuthenticationType())
    {
    case SIMPLE:
      buffer.append(" type=SIMPLE");
      break;
    case SASL:
      buffer.append(" type=SASL mechanism=").append(bindOperation.getSASLMechanism());
      break;
    default:
      buffer.append(" type=").append(bindOperation.getAuthenticationType());
      break;
    }

    appendLabel(buffer, "dn", bindOperation.getRawBindDN());
    appendRequestControls(bindOperation, buffer);
    if (bindOperation.isSynchronizationOperation())
    {
      buffer.append(" type=synchronization");
    }
  }

  private void appendCompareRequest(final CompareOperation compareOperation, final StringBuilder buffer)
  {
    appendLabel(buffer, "dn", compareOperation.getRawEntryDN());
    buffer.append(" attr=");
    buffer.append(compareOperation.getAttributeDescription().getAttributeType().getNameOrOID());
    appendRequestControls(compareOperation, buffer);
    if (compareOperation.isSynchronizationOperation())
    {
      buffer.append(" type=synchronization");
    }
  }

  private void appendDeleteRequest(final DeleteOperation deleteOperation, final StringBuilder buffer)
  {
    appendLabel(buffer, "dn", deleteOperation.getRawEntryDN());
    appendRequestControls(deleteOperation, buffer);
    if (deleteOperation.isSynchronizationOperation())
    {
      buffer.append(" type=synchronization");
    }
  }

  private void appendExtendedRequest(final ExtendedOperation extendedOperation,
      final StringBuilder buffer)
  {
    final String oid = extendedOperation.getRequestOID();
    final ExtendedOperationHandler<?> extOpHandler = DirectoryServer.getExtendedOperationHandler(oid);
    if (extOpHandler != null)
    {
      final String name = extOpHandler.getExtendedOperationName();
      appendLabelIfNotNull(buffer, "name", name);
    }
    appendLabel(buffer, "oid", oid);
    appendRequestControls(extendedOperation, buffer);
    if (extendedOperation.isSynchronizationOperation())
    {
      buffer.append(" type=synchronization");
    }
  }

  private void appendLabel(final StringBuilder buffer, final String label, final Object obj)
  {
    buffer.append(' ').append(label).append("=\"");
    if (obj != null)
    {
      buffer.append(obj);
    }
    buffer.append('\"');
  }

  private void appendLabelIfNotNull(final StringBuilder buffer, final String label, final Object obj)
  {
    if (obj != null)
    {
      appendLabel(buffer, label, obj);
    }
  }

  private void appendResultCodeAndMessage(StringBuilder buffer, Operation operation)
  {
    buffer.append(" result=");
    buffer.append(operation.getResultCode().intValue());

    final LocalizableMessageBuilder msg = operation.getErrorMessage();
    if (msg != null && msg.length() > 0)
    {
      appendLabel(buffer, "message", msg);
    }

    if (operation.getMaskedResultCode() != null)
    {
      buffer.append(" maskedResult=");
      buffer.append(operation.getMaskedResultCode().intValue());
    }
    final LocalizableMessageBuilder maskedMsg = operation.getMaskedErrorMessage();
    if (maskedMsg != null && maskedMsg.length() > 0)
    {
      appendLabel(buffer, "maskedMessage", maskedMsg);
    }
  }

  private void appendEtime(final StringBuilder buffer, final Operation operation)
  {
    buffer.append(" etime=");
    // the server can be configured to log processing time as nanos xor millis
    long etime = operation.getProcessingNanoTime();
    if (etime <= -1)
    {
      // if it is not configured for nanos, then use millis.
      etime = operation.getProcessingTime();
    }
    buffer.append(etime);
  }

  /** Appends the common log header information to the provided buffer. */
  private void appendHeader(final Operation operation, final String opType,
      final String category, final StringBuilder buffer)
  {
    buffer.append('[').append(TimeThread.getUserDefinedTime(timeStampFormat)).append("] ");
    buffer.append(opType);
    if (!isCombinedMode)
    {
      buffer.append(' ').append(category);
    }
    buffer.append(" conn=").append(operation.getConnectionID());
    buffer.append(" op=").append(operation.getOperationID());
    buffer.append(" msgID=").append(operation.getMessageID());
    appendTransactionId(operation, buffer);
  }

  private void appendModifyDNRequest(final ModifyDNOperation modifyDNOperation,
      final StringBuilder buffer)
  {
    appendLabel(buffer, "dn", modifyDNOperation.getRawEntryDN());
    appendLabel(buffer, "newRDN", modifyDNOperation.getRawNewRDN());
    appendLabel(buffer, "deleteOldRDN", modifyDNOperation.deleteOldRDN());

    final ByteString newSuperior = modifyDNOperation.getRawNewSuperior();
    appendLabelIfNotNull(buffer, "newSuperior", newSuperior);
    appendRequestControls(modifyDNOperation, buffer);
    if (modifyDNOperation.isSynchronizationOperation())
    {
      buffer.append(" type=synchronization");
    }
  }

  private void appendModifyRequest(final ModifyOperation modifyOperation, final StringBuilder buffer)
  {
    appendLabel(buffer, "dn", modifyOperation.getRawEntryDN());
    appendRequestControls(modifyOperation, buffer);
    if (modifyOperation.isSynchronizationOperation())
    {
      buffer.append(" type=synchronization");
    }
  }

  private void appendTransactionId(Operation operation, final StringBuilder buffer)
  {
    // In test context, serverContext may be null
    if (serverContext != null && serverContext.getCommonAudit().shouldTrustTransactionIds())
    {
      String transactionId = getTransactionIdFromControl(operation);
      if (transactionId != null)
      {
        buffer.append(" transactionId=").append(transactionId);
      }
    }
  }

  private String getTransactionIdFromControl(Operation operation)
  {
    try
    {
      TransactionIdControl control = operation.getRequestControl(TransactionIdControl.DECODER);
      return control != null ? control.getTransactionId() : null;
    }
    catch (DirectoryException e)
    {
      logger.error(ERR_COMMON_AUDIT_INVALID_TRANSACTION_ID.get(StaticUtils.stackTraceToSingleLineString(e)));
    }
    return null;
  }

  private void appendRequestControls(final Operation operation, final StringBuilder buffer)
  {
    appendControls(buffer, " requestControls=", operation.getRequestControls());
  }

  private void appendResponseControls(final Operation operation, final StringBuilder buffer)
  {
    appendControls(buffer, " responseControls=", operation.getResponseControls());
  }

  private void appendControls(final StringBuilder buffer, String label, List<Control> controls)
  {
    if (includeControlOIDs && !controls.isEmpty())
    {
      buffer.append(label);
      boolean isFirst = true;
      for (final Control control : controls)
      {
        if (!isFirst)
        {
          buffer.append(",");
        }
        buffer.append(control.getOID());
        isFirst = false;
      }
    }
  }

  private void appendSearchRequest(final SearchOperation searchOperation, final StringBuilder buffer)
  {
    appendLabel(buffer, "base", searchOperation.getRawBaseDN());
    buffer.append(" scope=");
    buffer.append(searchOperation.getScope());
    buffer.append(" filter=\"");
    searchOperation.getRawFilter().toString(buffer);

    final Set<String> attrs = searchOperation.getAttributes();
    if (attrs == null || attrs.isEmpty())
    {
      buffer.append("\" attrs=\"ALL\"");
    }
    else
    {
      buffer.append("\" attrs=\"");
      Utils.joinAsString(buffer, ",", attrs);
      buffer.append("\"");
    }
    appendRequestControls(searchOperation, buffer);
    if (searchOperation.isSynchronizationOperation())
    {
      buffer.append(" type=synchronization");
    }
  }

  /** Appends additional log items to the provided builder. */
  private void logAdditionalLogItems(final Operation operation, final StringBuilder builder)
  {
    appendResponseControls(operation, builder);
    for (final AdditionalLogItem item : operation.getAdditionalLogItems())
    {
      builder.append(' ');
      item.toString(builder);
    }
  }
}
