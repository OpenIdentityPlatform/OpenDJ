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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.loggers;



import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.types.ResultCode.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.AccessLogPublisherCfg;
import org.opends.server.admin.std.server.FileBasedAccessLogPublisherCfg;
import org.opends.server.api.AccessLogPublisher;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.types.*;
import org.opends.server.util.Base64;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.TimeThread;



/**
 * This class provides the implementation of the audit logger used by
 * the directory server.
 */
public class TextAuditLogPublisher extends
    AccessLogPublisher<FileBasedAccessLogPublisherCfg> implements
    ConfigurationChangeListener<FileBasedAccessLogPublisherCfg>
{

  private boolean suppressInternalOperations = true;

  private boolean suppressSynchronizationOperations = false;

  private TextWriter writer;

  private FileBasedAccessLogPublisherCfg currentConfig;



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
      // asyncronously,
      // we need to modify the underlaying writer.
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
          // The asynronous setting is being turned off.
          AsyncronousTextWriter asyncWriter = ((AsyncronousTextWriter) writer);
          writer = mfWriter;
          asyncWriter.shutdown(false);
        }

        if (!(writer instanceof AsyncronousTextWriter)
            && config.isAsynchronous())
        {
          // The asynronous setting is being turned on.
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
  @Override()
  public void close()
  {
    writer.shutdown();
    currentConfig.removeFileBasedAccessChangeListener(this);
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
  @Override()
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
  public boolean isConfigurationAcceptable(AccessLogPublisherCfg configuration,
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
  @Override()
  public void logAddResponse(AddOperation addOperation)
  {
    if (!isLoggable(addOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(50);
    appendHeader(addOperation, buffer);

    buffer.append("dn:");
    encodeValue(addOperation.getEntryDN().toString(), buffer);
    buffer.append(EOL);

    buffer.append("changetype: add");
    buffer.append(EOL);

    for (String ocName : addOperation.getObjectClasses().values())
    {
      buffer.append("objectClass: ");
      buffer.append(ocName);
      buffer.append(EOL);
    }

    for (List<Attribute> attrList : addOperation.getUserAttributes().values())
    {
      for (Attribute a : attrList)
      {
        for (AttributeValue v : a)
        {
          buffer.append(a.getName());
          buffer.append(":");
          encodeValue(v.getValue(), buffer);
          buffer.append(EOL);
        }
      }
    }

    for (List<Attribute> attrList : addOperation.getOperationalAttributes()
        .values())
    {
      for (Attribute a : attrList)
      {
        for (AttributeValue v : a)
        {
          buffer.append(a.getName());
          buffer.append(":");
          encodeValue(v.getValue(), buffer);
          buffer.append(EOL);
        }
      }
    }

    writer.writeRecord(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void logDeleteResponse(DeleteOperation deleteOperation)
  {
    if (!isLoggable(deleteOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(50);
    appendHeader(deleteOperation, buffer);

    buffer.append("dn:");
    encodeValue(deleteOperation.getEntryDN().toString(), buffer);
    buffer.append(EOL);

    buffer.append("changetype: delete");
    buffer.append(EOL);

    writer.writeRecord(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void logModifyDNResponse(ModifyDNOperation modifyDNOperation)
  {
    if (!isLoggable(modifyDNOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(50);
    appendHeader(modifyDNOperation, buffer);

    buffer.append("dn:");
    encodeValue(modifyDNOperation.getEntryDN().toString(), buffer);
    buffer.append(EOL);

    buffer.append("changetype: moddn");
    buffer.append(EOL);

    buffer.append("newrdn:");
    encodeValue(modifyDNOperation.getNewRDN().toString(), buffer);
    buffer.append(EOL);

    buffer.append("deleteoldrdn: ");
    if (modifyDNOperation.deleteOldRDN())
    {
      buffer.append("1");
    }
    else
    {
      buffer.append("0");
    }
    buffer.append(EOL);

    DN newSuperior = modifyDNOperation.getNewSuperior();
    if (newSuperior != null)
    {
      buffer.append("newsuperior:");
      encodeValue(newSuperior.toString(), buffer);
      buffer.append(EOL);
    }

    writer.writeRecord(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void logModifyResponse(ModifyOperation modifyOperation)
  {
    if (!isLoggable(modifyOperation))
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(50);
    appendHeader(modifyOperation, buffer);

    buffer.append("dn:");
    encodeValue(modifyOperation.getEntryDN().toString(), buffer);
    buffer.append(EOL);

    buffer.append("changetype: modify");
    buffer.append(EOL);

    boolean first = true;
    for (Modification mod : modifyOperation.getModifications())
    {
      if (first)
      {
        first = false;
      }
      else
      {
        buffer.append("-");
        buffer.append(EOL);
      }

      switch (mod.getModificationType())
      {
      case ADD:
        buffer.append("add: ");
        break;
      case DELETE:
        buffer.append("delete: ");
        break;
      case REPLACE:
        buffer.append("replace: ");
        break;
      case INCREMENT:
        buffer.append("increment: ");
        break;
      default:
        continue;
      }

      Attribute a = mod.getAttribute();
      buffer.append(a.getName());
      buffer.append(EOL);

      for (AttributeValue v : a)
      {
        buffer.append(a.getName());
        buffer.append(":");
        encodeValue(v.getValue(), buffer);
        buffer.append(EOL);
      }
    }

    writer.writeRecord(buffer.toString());
  }



  // Appends the common log header information to the provided buffer.
  private void appendHeader(Operation operation, StringBuilder buffer)
  {
    buffer.append("# ");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("; conn=");
    buffer.append(operation.getConnectionID());
    buffer.append("; op=");
    buffer.append(operation.getOperationID());
    buffer.append(EOL);
  }



  /**
   * Appends the appropriately-encoded attribute value to the provided
   * buffer.
   *
   * @param str
   *          The ASN.1 octet string containing the value to append.
   * @param buffer
   *          The buffer to which to append the value.
   */
  private void encodeValue(ByteSequence str, StringBuilder buffer)
  {
    if(StaticUtils.needsBase64Encoding(str))
    {
      buffer.append(": ");
      buffer.append(Base64.encode(str));
    }
    else
    {
      buffer.append(" ");
      buffer.append(str.toString());
    }
  }



  /**
   * Appends the appropriately-encoded attribute value to the provided
   * buffer.
   *
   * @param str
   *          The string containing the value to append.
   * @param buffer
   *          The buffer to which to append the value.
   */
  private void encodeValue(String str, StringBuilder buffer)
  {
    if (StaticUtils.needsBase64Encoding(str))
    {
      buffer.append(": ");
      buffer.append(Base64.encode(getBytes(str)));
    }
    else
    {
      buffer.append(" ");
      buffer.append(str);
    }
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

    if (operation.getResultCode() != SUCCESS)
    {
      return false;
    }

    return true;
  }
}
