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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.opends.server.loggers.Debug.*;



/**
 * This class defines a data structure that can be used to hold
 * information about the result of processing a configuration change.
 */
public class ConfigChangeResult
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.types.ConfigChangeResult";



  // A set of messages describing the changes that were made, any
  // action that may be required, or any problems that were
  // encountered.
  private ArrayList<String> messages;

  // Indicates whether one or more of the changes requires
  // administrative action in order to take effect.
  private boolean adminActionRequired;

  // The result code to return to the client from this configuration
  // change.
  private ResultCode resultCode;



  /**
   * Creates a new config change result object with the provided
   * information.
   *
   * @param  resultCode           The result code for this config
   *                              change result.
   * @param  adminActionRequired  Indicates whether administrative
   *                              action is required for one or more
   *                              of the changes to take effect.
   */
  public ConfigChangeResult(ResultCode resultCode,
                            boolean adminActionRequired)
  {
    assert debugEnter(CLASS_NAME, String.valueOf(resultCode),
                      String.valueOf(adminActionRequired));

    this.resultCode          = resultCode;
    this.adminActionRequired = adminActionRequired;
    this.messages            = new ArrayList<String>();
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
                            ArrayList<String> messages)
  {
    assert debugEnter(CLASS_NAME, String.valueOf(resultCode),
                      String.valueOf(adminActionRequired),
                      String.valueOf(messages));

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
    assert debugEnter(CLASS_NAME, "getResultCode");

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
    assert debugEnter(CLASS_NAME, "setResultCode",
                      String.valueOf(resultCode));

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
    assert debugEnter(CLASS_NAME, "adminActionRequired");

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
    assert debugEnter(CLASS_NAME, "setAdminActionRequired",
                      String.valueOf(adminActionRequired));

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
  public List<String> getMessages()
  {
    assert debugEnter(CLASS_NAME, "getMessages");

    return messages;
  }



  /**
   * Adds the provided message to the set of messages for this config
   * change result.
   *
   * @param  message  The message to add to the set of messages for
   *                  this config change result.
   */
  public void addMessage(String message)
  {
    assert debugEnter(CLASS_NAME, "addMessage",
                      String.valueOf(message));

    messages.add(message);
  }



  /**
   * Retrieves a string representation of this config change result.
   *
   * @return  A string representation of this config change result.
   */
  public String toString()
  {
    assert debugEnter(CLASS_NAME, "toString");

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
    assert debugEnter(CLASS_NAME, "toString",
                      "java.lang.StringBuilder");

    buffer.append("ConfigChangeResult(result=");
    buffer.append(resultCode.toString());
    buffer.append(", adminActionRequired=");
    buffer.append(adminActionRequired);
    buffer.append(", messages={");

    if (! messages.isEmpty())
    {
      Iterator<String> iterator = messages.iterator();

      String firstMessage = iterator.next();
      buffer.append(firstMessage);

      while (iterator.hasNext())
      {
        buffer.append(",");
        buffer.append(iterator.next());
      }
    }


    buffer.append("})");
  }
}

