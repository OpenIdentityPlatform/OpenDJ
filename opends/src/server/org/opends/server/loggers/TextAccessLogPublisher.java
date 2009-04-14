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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.loggers;



import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.AccessLogPublisherCfg;
import org.opends.server.admin.std.server.FileBasedAccessLogPublisherCfg;
import org.opends.server.api.AccessLogPublisher;
import org.opends.server.api.ClientConnection;
import org.opends.server.config.ConfigException;
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
import org.opends.server.core.UnbindOperation;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.FilePermission;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Operation;
import org.opends.server.types.ResultCode;
import org.opends.server.util.TimeThread;



/**
 * This class provides the implementation of the access logger used by
 * the directory server.
 */
public class TextAccessLogPublisher extends
    AccessLogPublisher<FileBasedAccessLogPublisherCfg> implements
    ConfigurationChangeListener<FileBasedAccessLogPublisherCfg>
{

  /**
   * The category to use when logging responses.
   */
  private static final String CATEGORY_RESPONSE = "RES";

  /**
   * The category to use when logging requests.
   */
  private static final String CATEGORY_REQUEST = "REQ";



  /**
   * Returns an instance of the text access log publisher that will
   * print all messages to the provided writer. This is used to print
   * the messages to the console when the server starts up.
   *
   * @param writer
   *          The text writer where the message will be written to.
   * @param suppressInternal
   *          Indicates whether to suppress internal operations.
   * @return The instance of the text error log publisher that will
   *         print all messages to standard out.
   */
  public static TextAccessLogPublisher getStartupTextAccessPublisher(
      TextWriter writer, boolean suppressInternal)
  {
    TextAccessLogPublisher startupPublisher = new TextAccessLogPublisher();
    startupPublisher.writer = writer;
    startupPublisher.suppressInternalOperations = suppressInternal;

    return startupPublisher;
  }

  private FileBasedAccessLogPublisherCfg currentConfig;

  private boolean suppressInternalOperations = true;

  private boolean suppressSynchronizationOperations = false;

  private TextWriter writer;



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      FileBasedAccessLogPublisherCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    suppressInternalOperations = config.isSuppressInternalOperations();
    suppressSynchronizationOperations = config
        .isSuppressSynchronizationOperations();

    File logFile = getFileForPath(config.getLogFile());
    FileNamingPolicy fnPolicy = new TimeStampNaming(logFile);

    try
    {
      FilePermission perm = FilePermission.decodeUNIXMode(config
          .getLogFilePermissions());

      boolean writerAutoFlush = config.isAutoFlush()
          && !config.isAsynchronous();

      TextWriter currentWriter;
      // Determine the writer we are using. If we were writing
      // asynchronously, we need to modify the underlying writer.
      if (writer instanceof AsyncronousTextWriter)
      {
        currentWriter = ((AsyncronousTextWriter) writer).getWrappedWriter();
      }
      else
      {
        currentWriter = writer;
      }

      if (currentWriter instanceof MultifileTextWriter)
      {
        MultifileTextWriter mfWriter = (MultifileTextWriter) currentWriter;

        mfWriter.setNamingPolicy(fnPolicy);
        mfWriter.setFilePermissions(perm);
        mfWriter.setAppend(config.isAppend());
        mfWriter.setAutoFlush(writerAutoFlush);
        mfWriter.setBufferSize((int) config.getBufferSize());
        mfWriter.setInterval(config.getTimeInterval());

        mfWriter.removeAllRetentionPolicies();
        mfWriter.removeAllRotationPolicies();

        for (DN dn : config.getRotationPolicyDNs())
        {
          mfWriter.addRotationPolicy(DirectoryServer.getRotationPolicy(dn));
        }

        for (DN dn : config.getRetentionPolicyDNs())
        {
          mfWriter.addRetentionPolicy(DirectoryServer.getRetentionPolicy(dn));
        }

        if (writer instanceof AsyncronousTextWriter && !config.isAsynchronous())
        {
          // The asynchronous setting is being turned off.
          AsyncronousTextWriter asyncWriter = ((AsyncronousTextWriter) writer);
          writer = mfWriter;
          asyncWriter.shutdown(false);
        }

        if (!(writer instanceof AsyncronousTextWriter)
            && config.isAsynchronous())
        {
          // The asynchronous setting is being turned on.
          AsyncronousTextWriter asyncWriter = new AsyncronousTextWriter(
              "Asyncronous Text Writer for " + config.dn().toNormalizedString(),
              config.getQueueSize(), config.isAutoFlush(), mfWriter);
          writer = asyncWriter;
        }

        if ((currentConfig.isAsynchronous() && config.isAsynchronous())
            && (currentConfig.getQueueSize() != config.getQueueSize()))
        {
          adminActionRequired = true;
        }

        currentConfig = config;
      }
    }
    catch (Exception e)
    {
      Message message = ERR_CONFIG_LOGGING_CANNOT_CREATE_WRITER.get(config.dn()
          .toString(), stackTraceToSingleLineString(e));
      resultCode = DirectoryServer.getServerErrorResultCode();
      messages.add(message);

    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void close()
  {
    writer.shutdown();

    if (currentConfig != null)
    {
      currentConfig.removeFileBasedAccessChangeListener(this);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public DN getDN()
  {
    if (currentConfig != null)
    {
      return currentConfig.dn();
    }
    else
    {
      return null;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeAccessLogPublisher(
      FileBasedAccessLogPublisherCfg config)
      throws ConfigException, InitializationException
  {
    File logFile = getFileForPath(config.getLogFile());
    FileNamingPolicy fnPolicy = new TimeStampNaming(logFile);

    try
    {
      FilePermission perm = FilePermission.decodeUNIXMode(config
          .getLogFilePermissions());

      LogPublisherErrorHandler errorHandler = new LogPublisherErrorHandler(
          config.dn());

      boolean writerAutoFlush = config.isAutoFlush()
          && !config.isAsynchronous();

      MultifileTextWriter writer = new MultifileTextWriter(
          "Multifile Text Writer for " + config.dn().toNormalizedString(),
          config.getTimeInterval(), fnPolicy, perm, errorHandler, "UTF-8",
          writerAutoFlush, config.isAppend(), (int) config.getBufferSize());

      // Validate retention and rotation policies.
      for (DN dn : config.getRotationPolicyDNs())
      {
        writer.addRotationPolicy(DirectoryServer.getRotationPolicy(dn));
      }

      for (DN dn : config.getRetentionPolicyDNs())
      {
        writer.addRetentionPolicy(DirectoryServer.getRetentionPolicy(dn));
      }

      if (config.isAsynchronous())
      {
        this.writer = new AsyncronousTextWriter("Asyncronous Text Writer for "
            + config.dn().toNormalizedString(), config.getQueueSize(), config
            .isAutoFlush(), writer);
      }
      else
      {
        this.writer = writer;
      }
    }
    catch (DirectoryException e)
    {
      Message message = ERR_CONFIG_LOGGING_CANNOT_CREATE_WRITER.get(config.dn()
          .toString(), String.valueOf(e));
      throw new InitializationException(message, e);

    }
    catch (IOException e)
    {
      Message message = ERR_CONFIG_LOGGING_CANNOT_OPEN_FILE.get(logFile
          .toString(), config.dn().toString(), String.valueOf(e));
      throw new InitializationException(message, e);

    }

    suppressInternalOperations = config.isSuppressInternalOperations();
    suppressSynchronizationOperations = config
        .isSuppressSynchronizationOperations();

    currentConfig = config;

    config.addFileBasedAccessChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationAcceptable(
      AccessLogPublisherCfg configuration,
      List<Message> unacceptableReasons)
  {
    FileBasedAccessLogPublisherCfg config =
      (FileBasedAccessLogPublisherCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      FileBasedAccessLogPublisherCfg config, List<Message> unacceptableReasons)
  {
    // Make sure the permission is valid.
    try
    {
      FilePermission filePerm = FilePermission.decodeUNIXMode(config
          .getLogFilePermissions());
      if (!filePerm.isOwnerWritable())
      {
        Message message = ERR_CONFIG_LOGGING_INSANE_MODE.get(config
            .getLogFilePermissions());
        unacceptableReasons.add(message);
        return false;
      }
    }
    catch (DirectoryException e)
    {
      Message message = ERR_CONFIG_LOGGING_MODE_INVALID.get(config
          .getLogFilePermissions(), String.valueOf(e));
      unacceptableReasons.add(message);
      return false;
    }

    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void logAbandonIntermediateMessage(AbandonOperation abandonOperation,
      String category, Map<String, String> content)
  {
    logIntermediateMessage(abandonOperation, "ABANDON", category, content);
  }



  /**
   * Writes a message to the access logger with information about the
   * abandon request associated with the provided abandon operation.
   *
   * @param abandonOperation
   *          The abandon operation containing the information to use
   *          to log the abandon request.
   */
  @Override
  public void logAbandonRequest(AbandonOperation abandonOperation)
  {
    if (!isLoggable(abandonOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(100);
    appendHeader(abandonOperation, "ABANDON", CATEGORY_REQUEST, buffer);
    buffer.append(" idToAbandon=");
    buffer.append(abandonOperation.getIDToAbandon());
    if (abandonOperation.isSynchronizationOperation())
      buffer.append(" type=synchronization");

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the
   * result of the provided abandon operation.
   *
   * @param abandonOperation
   *          The abandon operation containing the information to use
   *          to log the abandon request.
   */
  @Override
  public void logAbandonResult(AbandonOperation abandonOperation)
  {
    if (!isLoggable(abandonOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(100);
    appendHeader(abandonOperation, "ABANDON", CATEGORY_RESPONSE, buffer);
    buffer.append(" result=");
    buffer.append(abandonOperation.getResultCode().getIntValue());
    MessageBuilder msg = abandonOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" message=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    msg = abandonOperation.getAdditionalLogMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" additionalInfo=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    buffer.append(" etime=");
    buffer.append(abandonOperation.getProcessingTime());

    writer.writeRecord(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void logAddIntermediateMessage(AddOperation addOperation,
      String category, Map<String, String> content)
  {
    logIntermediateMessage(addOperation, "ADD", category, content);
  }



  /**
   * Writes a message to the access logger with information about the
   * add request associated with the provided add operation.
   *
   * @param addOperation
   *          The add operation containing the information to use to
   *          log the add request.
   */
  @Override
  public void logAddRequest(AddOperation addOperation)
  {
    if (!isLoggable(addOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(100);
    appendHeader(addOperation, "ADD", CATEGORY_REQUEST, buffer);
    buffer.append(" dn=\"");
    buffer.append(addOperation.getRawEntryDN().toString());
    buffer.append("\"");
    if (addOperation.isSynchronizationOperation())
      buffer.append(" type=synchronization");

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the
   * add response associated with the provided add operation.
   *
   * @param addOperation
   *          The add operation containing the information to use to
   *          log the add response.
   */
  @Override
  public void logAddResponse(AddOperation addOperation)
  {
    if (!isLoggable(addOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(100);
    appendHeader(addOperation, "ADD", CATEGORY_RESPONSE, buffer);
    buffer.append(" result=");
    buffer.append(addOperation.getResultCode().getIntValue());

    MessageBuilder msg = addOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" message=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    msg = addOperation.getAdditionalLogMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" additionalInfo=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    DN proxiedAuthDN = addOperation.getProxiedAuthorizationDN();
    if (proxiedAuthDN != null)
    {
      buffer.append(" authzDN=\"");
      proxiedAuthDN.toString(buffer);
      buffer.append('\"');
    }

    buffer.append(" etime=");
    long etime = addOperation.getProcessingNanoTime();
    if (etime <= -1)
    {
      etime = addOperation.getProcessingTime();
    }
    buffer.append(etime);

    writer.writeRecord(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void logBindIntermediateMessage(BindOperation bindOperation,
      String category, Map<String, String> content)
  {
    logIntermediateMessage(bindOperation, "BIND", category, content);
  }



  /**
   * Writes a message to the access logger with information about the
   * bind request associated with the provided bind operation.
   *
   * @param bindOperation
   *          The bind operation with the information to use to log
   *          the bind request.
   */
  @Override
  public void logBindRequest(BindOperation bindOperation)
  {
    if (!isLoggable(bindOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(100);
    appendHeader(bindOperation, "BIND", CATEGORY_REQUEST, buffer);

    switch (bindOperation.getAuthenticationType())
    {
    case SIMPLE:
      buffer.append(" type=SIMPLE");
      break;
    case SASL:
      buffer.append(" type=SASL mechanism=");
      buffer.append(bindOperation.getSASLMechanism());
      break;
    default:
      buffer.append(" type=");
      buffer.append(bindOperation.getAuthenticationType());
      break;
    }

    buffer.append(" dn=\"");
    buffer.append(bindOperation.getRawBindDN().toString());
    buffer.append("\"");
    if (bindOperation.isSynchronizationOperation())
      buffer.append(" type=synchronization");

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the
   * bind response associated with the provided bind operation.
   *
   * @param bindOperation
   *          The bind operation containing the information to use to
   *          log the bind response.
   */
  @Override
  public void logBindResponse(BindOperation bindOperation)
  {
    if (!isLoggable(bindOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(100);
    appendHeader(bindOperation, "BIND", CATEGORY_RESPONSE, buffer);
    buffer.append(" result=");
    buffer.append(bindOperation.getResultCode().getIntValue());

    MessageBuilder msg = bindOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" message=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    Message failureMessage = bindOperation.getAuthFailureReason();
    if (failureMessage != null)
    {
      buffer.append(" authFailureID=");
      buffer.append(failureMessage.getDescriptor().getId());
      buffer.append(" authFailureReason=\"");
      buffer.append(failureMessage);
      buffer.append('\"');
    }

    msg = bindOperation.getAdditionalLogMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" additionalInfo=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    if (bindOperation.getResultCode() == ResultCode.SUCCESS)
    {
      AuthenticationInfo authInfo = bindOperation.getAuthenticationInfo();
      if (authInfo != null)
      {
        DN authDN = authInfo.getAuthenticationDN();
        if (authDN != null)
        {
          buffer.append(" authDN=\"");
          authDN.toString(buffer);
          buffer.append('\"');

          DN authzDN = authInfo.getAuthorizationDN();
          if (!authDN.equals(authzDN))
          {
            buffer.append(" authzDN=\"");
            if (authzDN != null)
            {
              authzDN.toString(buffer);
            }
            buffer.append('\"');
          }
        }
        else
        {
          buffer.append(" authDN=\"\"");
        }
      }
    }

    buffer.append(" etime=");
    long etime = bindOperation.getProcessingNanoTime();
    if (etime <= -1)
    {
      etime = bindOperation.getProcessingTime();
    }
    buffer.append(etime);

    writer.writeRecord(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void logCompareIntermediateMessage(CompareOperation compareOperation,
      String category, Map<String, String> content)
  {
    logIntermediateMessage(compareOperation, "COMPARE", category, content);
  }



  /**
   * Writes a message to the access logger with information about the
   * compare request associated with the provided compare operation.
   *
   * @param compareOperation
   *          The compare operation containing the information to use
   *          to log the compare request.
   */
  @Override
  public void logCompareRequest(CompareOperation compareOperation)
  {
    if (!isLoggable(compareOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(100);
    appendHeader(compareOperation, "COMPARE", CATEGORY_REQUEST, buffer);
    buffer.append(" dn=\"");
    buffer.append(compareOperation.getRawEntryDN().toString());
    buffer.append("\" attr=");
    buffer.append(compareOperation.getAttributeType());
    if (compareOperation.isSynchronizationOperation())
      buffer.append(" type=synchronization");

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the
   * compare response associated with the provided compare operation.
   *
   * @param compareOperation
   *          The compare operation containing the information to use
   *          to log the compare response.
   */
  @Override
  public void logCompareResponse(CompareOperation compareOperation)
  {
    if (!isLoggable(compareOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(100);
    appendHeader(compareOperation, "COMPARE", CATEGORY_RESPONSE, buffer);
    buffer.append(" result=");
    buffer.append(compareOperation.getResultCode().getIntValue());

    MessageBuilder msg = compareOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" message=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    msg = compareOperation.getAdditionalLogMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" additionalInfo=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    DN proxiedAuthDN = compareOperation.getProxiedAuthorizationDN();
    if (proxiedAuthDN != null)
    {
      buffer.append(" authzDN=\"");
      proxiedAuthDN.toString(buffer);
      buffer.append('\"');
    }

    buffer.append(" etime=");
    long etime = compareOperation.getProcessingNanoTime();
    if (etime <= -1)
    {
      etime = compareOperation.getProcessingTime();
    }
    buffer.append(etime);

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about a
   * new client connection that has been established, regardless of
   * whether it will be immediately terminated.
   *
   * @param clientConnection
   *          The client connection that has been established.
   */
  @Override
  public void logConnect(ClientConnection clientConnection)
  {
    long connectionID = clientConnection.getConnectionID();

    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(100);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" CONNECT conn=");
    buffer.append(connectionID);
    buffer.append(" from=");
    buffer.append(clientConnection.getClientHostPort());
    buffer.append(" to=");
    buffer.append(clientConnection.getServerHostPort());
    buffer.append(" protocol=");
    buffer.append(clientConnection.getProtocol());

    writer.writeRecord(buffer.toString());

  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void logDeleteIntermediateMessage(DeleteOperation deleteOperation,
      String category, Map<String, String> content)
  {
    logIntermediateMessage(deleteOperation, "DELETE", category, content);
  }



  /**
   * Writes a message to the access logger with information about the
   * delete request associated with the provided delete operation.
   *
   * @param deleteOperation
   *          The delete operation with the information to use to log
   *          the delete request.
   */
  @Override
  public void logDeleteRequest(DeleteOperation deleteOperation)
  {
    if (!isLoggable(deleteOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(100);
    appendHeader(deleteOperation, "DELETE", CATEGORY_REQUEST, buffer);
    buffer.append(" dn=\"");
    buffer.append(deleteOperation.getRawEntryDN().toString());
    buffer.append("\"");
    if (deleteOperation.isSynchronizationOperation())
      buffer.append(" type=synchronization");

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the
   * delete response associated with the provided delete operation.
   *
   * @param deleteOperation
   *          The delete operation containing the information to use
   *          to log the delete response.
   */
  @Override
  public void logDeleteResponse(DeleteOperation deleteOperation)
  {
    if (!isLoggable(deleteOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(100);
    appendHeader(deleteOperation, "DELETE", CATEGORY_RESPONSE, buffer);
    buffer.append(" result=");
    buffer.append(deleteOperation.getResultCode().getIntValue());

    MessageBuilder msg = deleteOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" message=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    msg = deleteOperation.getAdditionalLogMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" additionalInfo=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    DN proxiedAuthDN = deleteOperation.getProxiedAuthorizationDN();
    if (proxiedAuthDN != null)
    {
      buffer.append(" authzDN=\"");
      proxiedAuthDN.toString(buffer);
      buffer.append('\"');
    }

    buffer.append(" etime=");
    long etime = deleteOperation.getProcessingNanoTime();
    if (etime <= -1)
    {
      etime = deleteOperation.getProcessingTime();
    }
    buffer.append(etime);

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the
   * termination of an existing client connection.
   *
   * @param clientConnection
   *          The client connection that has been terminated.
   * @param disconnectReason
   *          A generic disconnect reason for the connection
   *          termination.
   * @param message
   *          A human-readable message that can provide additional
   *          information about the disconnect.
   */
  @Override
  public void logDisconnect(ClientConnection clientConnection,
      DisconnectReason disconnectReason, Message message)
  {
    long connectionID = clientConnection.getConnectionID();
    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(100);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" DISCONNECT conn=");
    buffer.append(connectionID);
    buffer.append(" reason=\"");
    buffer.append(disconnectReason);

    if (message != null)
    {
      buffer.append("\" msg=\"");
      buffer.append(message);
    }

    buffer.append("\"");

    writer.writeRecord(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void logExtendedIntermediateMessage(
      ExtendedOperation extendedOperation, String category,
      Map<String, String> content)
  {
    logIntermediateMessage(extendedOperation, "EXTENDED", category, content);
  }



  /**
   * Writes a message to the access logger with information about the
   * extended request associated with the provided extended operation.
   *
   * @param extendedOperation
   *          The extended operation containing the information to use
   *          to log the extended request.
   */
  @Override
  public void logExtendedRequest(ExtendedOperation extendedOperation)
  {
    if (!isLoggable(extendedOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(100);
    appendHeader(extendedOperation, "EXTENDED", CATEGORY_REQUEST, buffer);
    buffer.append(" oid=\"");
    buffer.append(extendedOperation.getRequestOID());
    buffer.append("\"");
    if (extendedOperation.isSynchronizationOperation())
      buffer.append(" type=synchronization");

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the
   * extended response associated with the provided extended
   * operation.
   *
   * @param extendedOperation
   *          The extended operation containing the info to use to log
   *          the extended response.
   */
  @Override
  public void logExtendedResponse(ExtendedOperation extendedOperation)
  {
    if (!isLoggable(extendedOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(100);
    appendHeader(extendedOperation, "EXTENDED", CATEGORY_RESPONSE, buffer);

    String oid = extendedOperation.getResponseOID();
    if (oid != null)
    {
      buffer.append(" oid=\"");
      buffer.append(oid);
      buffer.append('\"');
    }

    buffer.append(" result=");
    buffer.append(extendedOperation.getResultCode().getIntValue());

    MessageBuilder msg = extendedOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" message=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    msg = extendedOperation.getAdditionalLogMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" additionalInfo=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    buffer.append(" etime=");
    long etime = extendedOperation.getProcessingNanoTime();
    if (etime <= -1)
    {
      etime = extendedOperation.getProcessingTime();
    }
    buffer.append(etime);

    writer.writeRecord(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void logModifyDNIntermediateMessage(
      ModifyDNOperation modifyDNOperation, String category,
      Map<String, String> content)
  {
    logIntermediateMessage(modifyDNOperation, "MODIFY", category, content);
  }



  /**
   * Writes a message to the access logger with information about the
   * modify DN request associated with the provided modify DN
   * operation.
   *
   * @param modifyDNOperation
   *          The modify DN operation containing the info to use to
   *          log the modify DN request.
   */
  @Override
  public void logModifyDNRequest(ModifyDNOperation modifyDNOperation)
  {
    if (!isLoggable(modifyDNOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(100);
    appendHeader(modifyDNOperation, "MODIFYDN", CATEGORY_REQUEST, buffer);
    buffer.append(" dn=\"");
    buffer.append(modifyDNOperation.getRawEntryDN().toString());
    buffer.append("\" newRDN=\"");
    buffer.append(modifyDNOperation.getRawNewRDN().toString());
    buffer.append("\" deleteOldRDN=");
    buffer.append(modifyDNOperation.deleteOldRDN());

    ByteString newSuperior = modifyDNOperation.getRawNewSuperior();
    if (newSuperior != null)
    {
      buffer.append(" newSuperior=\"");
      buffer.append(newSuperior.toString());
    }
    if (modifyDNOperation.isSynchronizationOperation())
      buffer.append(" type=synchronization");

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the
   * modify DN response associated with the provided modify DN
   * operation.
   *
   * @param modifyDNOperation
   *          The modify DN operation containing the information to
   *          use to log the modify DN response.
   */
  @Override
  public void logModifyDNResponse(ModifyDNOperation modifyDNOperation)
  {
    if (!isLoggable(modifyDNOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(100);
    appendHeader(modifyDNOperation, "MODIFYDN", CATEGORY_RESPONSE, buffer);
    buffer.append(" result=");
    buffer.append(modifyDNOperation.getResultCode().getIntValue());

    MessageBuilder msg = modifyDNOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" message=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    msg = modifyDNOperation.getAdditionalLogMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" additionalInfo=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    DN proxiedAuthDN = modifyDNOperation.getProxiedAuthorizationDN();
    if (proxiedAuthDN != null)
    {
      buffer.append(" authzDN=\"");
      proxiedAuthDN.toString(buffer);
      buffer.append('\"');
    }

    buffer.append(" etime=");
    long etime = modifyDNOperation.getProcessingNanoTime();
    if (etime <= -1)
    {
      etime = modifyDNOperation.getProcessingTime();
    }
    buffer.append(etime);

    writer.writeRecord(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void logModifyIntermediateMessage(ModifyOperation modifyOperation,
      String category, Map<String, String> content)
  {
    logIntermediateMessage(modifyOperation, "MODIFY", category, content);
  }



  /**
   * Writes a message to the access logger with information about the
   * modify request associated with the provided modify operation.
   *
   * @param modifyOperation
   *          The modify operation containing the information to use
   *          to log the modify request.
   */
  @Override
  public void logModifyRequest(ModifyOperation modifyOperation)
  {
    if (!isLoggable(modifyOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(100);
    appendHeader(modifyOperation, "MODIFY", CATEGORY_REQUEST, buffer);
    buffer.append(" dn=\"");
    buffer.append(modifyOperation.getRawEntryDN().toString());
    buffer.append("\"");
    if (modifyOperation.isSynchronizationOperation())
      buffer.append(" type=synchronization");

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the
   * modify response associated with the provided modify operation.
   *
   * @param modifyOperation
   *          The modify operation containing the information to use
   *          to log the modify response.
   */
  @Override
  public void logModifyResponse(ModifyOperation modifyOperation)
  {
    if (!isLoggable(modifyOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(100);
    appendHeader(modifyOperation, "MODIFY", CATEGORY_RESPONSE, buffer);
    buffer.append(" result=");
    buffer.append(modifyOperation.getResultCode().getIntValue());

    MessageBuilder msg = modifyOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" message=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    msg = modifyOperation.getAdditionalLogMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" additionalInfo=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    DN proxiedAuthDN = modifyOperation.getProxiedAuthorizationDN();
    if (proxiedAuthDN != null)
    {
      buffer.append(" authzDN=\"");
      proxiedAuthDN.toString(buffer);
      buffer.append('\"');
    }

    buffer.append(" etime=");
    long etime = modifyOperation.getProcessingNanoTime();
    if (etime <= -1)
    {
      etime = modifyOperation.getProcessingTime();
    }
    buffer.append(etime);

    writer.writeRecord(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void logSearchIntermediateMessage(SearchOperation searchOperation,
      String category, Map<String, String> content)
  {
    logIntermediateMessage(searchOperation, "SEARCH", category, content);
  }



  /**
   * Writes a message to the access logger with information about the
   * search request associated with the provided search operation.
   *
   * @param searchOperation
   *          The search operation containing the info to use to log
   *          the search request.
   */
  @Override
  public void logSearchRequest(SearchOperation searchOperation)
  {
    if (!isLoggable(searchOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(100);
    appendHeader(searchOperation, "SEARCH", CATEGORY_REQUEST, buffer);
    buffer.append(" base=\"");
    buffer.append(searchOperation.getRawBaseDN().toString());
    buffer.append("\" scope=");
    buffer.append(searchOperation.getScope());
    buffer.append(" filter=\"");
    searchOperation.getRawFilter().toString(buffer);

    LinkedHashSet<String> attrs = searchOperation.getAttributes();
    if ((attrs == null) || attrs.isEmpty())
    {
      buffer.append("\" attrs=\"ALL\"");
    }
    else
    {
      buffer.append("\" attrs=\"");

      Iterator<String> iterator = attrs.iterator();
      buffer.append(iterator.next());
      while (iterator.hasNext())
      {
        buffer.append(",");
        buffer.append(iterator.next());
      }

      buffer.append("\"");
    }
    if (searchOperation.isSynchronizationOperation())
      buffer.append(" type=synchronization");

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the
   * completion of the provided search operation.
   *
   * @param searchOperation
   *          The search operation containing the information to use
   *          to log the search result done message.
   */
  @Override
  public void logSearchResultDone(SearchOperation searchOperation)
  {
    if (!isLoggable(searchOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(100);
    appendHeader(searchOperation, "SEARCH", CATEGORY_RESPONSE, buffer);
    buffer.append(" result=");
    buffer.append(searchOperation.getResultCode().getIntValue());

    MessageBuilder msg = searchOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" message=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    buffer.append(" nentries=");
    buffer.append(searchOperation.getEntriesSent());

    msg = searchOperation.getAdditionalLogMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" additionalInfo=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    DN proxiedAuthDN = searchOperation.getProxiedAuthorizationDN();
    if (proxiedAuthDN != null)
    {
      buffer.append(" authzDN=\"");
      proxiedAuthDN.toString(buffer);
      buffer.append('\"');
    }

    buffer.append(" etime=");
    long etime = searchOperation.getProcessingNanoTime();
    if (etime <= -1)
    {
      etime = searchOperation.getProcessingTime();
    }
    buffer.append(etime);

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the
   * unbind request associated with the provided unbind operation.
   *
   * @param unbindOperation
   *          The unbind operation containing the info to use to log
   *          the unbind request.
   */
  @Override
  public void logUnbind(UnbindOperation unbindOperation)
  {
    if (!isLoggable(unbindOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(100);
    appendHeader(unbindOperation, "UNBIND", CATEGORY_REQUEST, buffer);
    if (unbindOperation.isSynchronizationOperation())
      buffer.append(" type=synchronization");

    writer.writeRecord(buffer.toString());
  }



  // Appends the common log header information to the provided buffer.
  private void appendHeader(Operation operation, String opType,
      String category, StringBuilder buffer)
  {
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("] ");
    buffer.append(opType);
    buffer.append(" ");
    buffer.append(category);
    buffer.append(" conn=");
    buffer.append(operation.getConnectionID());
    buffer.append(" op=");
    buffer.append(operation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(operation.getMessageID());
  }



  // Determines whether the provided operation should be logged.
  private boolean isLoggable(Operation operation)
  {
    long connectionID = operation.getConnectionID();
    if (connectionID < 0)
    {
      // This is an internal operation.
      if (operation.isSynchronizationOperation())
      {
        if (suppressSynchronizationOperations)
        {
          return false;
        }
      }
      else
      {
        if (suppressInternalOperations)
        {
          return false;
        }
      }
    }
    return true;
  }



  // Writes an intermediate message to the log.
  private void logIntermediateMessage(Operation operation, String opType,
      String category, Map<String, String> content)
  {
    if (!isLoggable(operation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(100);
    appendHeader(operation, opType, category, buffer);

    for (Map.Entry<String, String> entry : content.entrySet())
    {
      buffer.append(' ');
      buffer.append(entry.getKey());
      buffer.append('=');
      buffer.append(entry.getValue());
    }

    writer.writeRecord(buffer.toString());
  }
}
