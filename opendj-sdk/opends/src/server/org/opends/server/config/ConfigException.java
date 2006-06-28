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
package org.opends.server.config;



import static org.opends.server.loggers.Debug.*;



/**
 * This class defines an exception that may be thrown during the course of
 * interactions with the Directory Server configuration.
 */
public class ConfigException
       extends Exception
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.config.ConfigException";



  /**
   * The serial version identifier required to satisfy the compiler because this
   * class extends <CODE>java.lang.Exception</CODE>, which implements the
   * <CODE>java.io.Serializable</CODE> interface.  This value was generated
   * using the <CODE>serialver</CODE> command-line utility included with the
   * Java SDK.
   */
  private static final long serialVersionUID = 3135563348838654570L;



  // The unique message ID for the associated message.
  private int messageID;



  /**
   * Creates a new configuration exception with the provided message.
   *
   * @param  messageID  The unique message ID for the provided message.
   * @param  message    The message to use for this configuration exception.
   */
  public ConfigException(int messageID, String message)
  {
    super(message);

    assert debugConstructor(CLASS_NAME, String.valueOf(messageID),
                            String.valueOf(message));

    this.messageID = messageID;
  }



  /**
   * Creates a new configuration exception with the provided message and
   * underlying cause.
   *
   * @param  messageID  The unique message ID for the provided message.
   * @param  message    The message to use for this configuration exception.
   * @param  cause      The underlying cause that triggered this configuration
   *                    exception.
   */
  public ConfigException(int messageID, String message, Throwable cause)
  {
    super(message, cause);

    assert debugConstructor(CLASS_NAME, String.valueOf(messageID),
                            String.valueOf(message), String.valueOf(cause));

    this.messageID = messageID;
  }



  /**
   * Retrieves the message ID for this exception.
   *
   * @return  The message ID for this exception.
   */
  public int getMessageID()
  {
    assert debugEnter(CLASS_NAME, "getMessageID");

    return messageID;
  }
}

