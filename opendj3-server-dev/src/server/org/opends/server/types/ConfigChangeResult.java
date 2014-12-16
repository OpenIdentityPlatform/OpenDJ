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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.types;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ResultCode;

/**
 * This class defines a data structure that can be used to hold
 * information about the result of processing a configuration change.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class ConfigChangeResult
{
  /**
   * A set of messages describing the changes that were made, any action that
   * may be required, or any problems that were encountered.
   */
  private final List<LocalizableMessage> messages;

  /**
   * Indicates whether one or more of the changes requires administrative action
   * in order to take effect.
   */
  private boolean adminActionRequired;

  /** The result code to return to the client from this configuration change. */
  private ResultCode resultCode;

  /** Creates a new mutable config change result object. */
  public ConfigChangeResult()
  {
    this(ResultCode.SUCCESS, false, new ArrayList<LocalizableMessage>());
  }

  /**
   * Creates a new config change result object with the provided information.
   *
   * @param  resultCode           The result code for this config change result.
   * @param  adminActionRequired  Indicates whether administrative
   *                              action is required for one or more
   *                              of the changes to take effect.
   */
  public ConfigChangeResult(ResultCode resultCode,
                            boolean adminActionRequired)
  {
    this(resultCode, adminActionRequired, new ArrayList<LocalizableMessage>());
  }

  /**
   * Creates a new config change result object with the provided
   * information.
   *
   * @param  resultCode           The result code for this config
   *                              change result.
   * @param  adminActionRequired  Indicates whether administrative
   *                              action is required for one or more
   *                              of the changes to take effect.
   * @param  messages             A set of messages that provide
   *                              additional information about the
   *                              change processing.
   */
  public ConfigChangeResult(ResultCode resultCode,
                            boolean adminActionRequired,
                            List<LocalizableMessage> messages)
  {
    this.resultCode          = resultCode;
    this.adminActionRequired = adminActionRequired;
    this.messages            = messages;
  }

  /**
   * Retrieves the result code for this config change result.
   *
   * @return  The result code for this config change result.
   */
  public ResultCode getResultCode()
  {
    return resultCode;
  }



  /**
   * Specifies the result code for this config change result.
   *
   * @param  resultCode  The result code for this config change
   *                     result.
   */
  public void setResultCode(ResultCode resultCode)
  {
    this.resultCode = resultCode;
  }

  /**
   * Indicates whether administrative action is required before one or
   * more of the changes will take effect.
   *
   * @return  <CODE>true</CODE> if one or more of the configuration
   *          changes require administrative action to take effect, or
   *          <CODE>false</CODE> if not.
   */
  public boolean adminActionRequired()
  {
    return adminActionRequired;
  }

  /**
   * Specifies whether administrative action is required before one or
   * more of the changes will take effect.
   *
   * @param  adminActionRequired  Specifies whether administrative
   *                              action is required before one or
   *                              more of the changes will take
   *                              effect.
   */
  public void setAdminActionRequired(boolean adminActionRequired)
  {
    this.adminActionRequired = adminActionRequired;
  }

  /**
   * Retrieves the set of messages that provide explanation for the
   * processing of the configuration changes.  This list may be
   * modified by the caller.
   *
   * @return  The set of messages that provide explanation for the
   *          processing of the configuration changes.
   */
  public List<LocalizableMessage> getMessages()
  {
    return messages;
  }

  /**
   * Adds the provided message to the set of messages for this config
   * change result.
   *
   * @param  message  The message to add to the set of messages for
   *                  this config change result.
   */
  public void addMessage(LocalizableMessage message)
  {
    messages.add(message);
  }

  /**
   * Retrieves a string representation of this config change result.
   *
   * @return  A string representation of this config change result.
   */
  @Override
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }

  /**
   * Appends a string representation of this config change result to
   * the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("ConfigChangeResult(result=");
    buffer.append(resultCode);
    buffer.append(", adminActionRequired=");
    buffer.append(adminActionRequired);
    buffer.append(", messages={");

    if (! messages.isEmpty())
    {
      final Iterator<LocalizableMessage> iterator = messages.iterator();
      buffer.append(iterator.next());
      while (iterator.hasNext())
      {
        buffer.append(",");
        buffer.append(iterator.next());
      }
    }

    buffer.append("})");
  }
}
