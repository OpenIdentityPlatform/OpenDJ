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
package org.opends.server.api;



import java.nio.ByteBuffer;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.InitializationException;
import org.opends.server.types.DebugLogCategory;
import org.opends.server.types.DebugLogSeverity;



/**
 * This class defines the set of methods and structures that must be
 * implemented for a Directory Server debug logger.
 */
public abstract class DebugLogger
{
  /**
   * Initializes this debug logger based on the information in the
   * provided configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the
   *                      information to use to initialize this debug
   *                      logger.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in
   *                           the process of performing the
   *                           initialization.
   *
   * @throws  InitializationException  If a problem occurs during
   *                                   initialization that is not
   *                                   related to the server
   *                                   configuration.
   */
  public abstract void initializeDebugLogger(ConfigEntry configEntry)
         throws ConfigException, InitializationException;



  /**
   * Closes this debug logger and releases any resources it might have
   * held.
   */
  public abstract void closeDebugLogger();



  /**
   * Writes a message to the debug logger indicating that the
   * specified raw data was read.
   *
   * @param  className   The fully-qualified name of the Java class in
   *                     which the data was read.
   * @param  methodName  The name of the method in which the data was
   *                     read.
   * @param  buffer      The byte buffer containing the data that has
   *                     been read.  The byte buffer must be in the
   *                     same state when this method returns as when
   *                     it was entered.
   */
  public abstract void debugBytesRead(String className,
                                      String methodName,
                                      ByteBuffer buffer);



  /**
   * Writes a message to the debug logger indicating that the
   * specified raw data was written.
   *
   * @param  className   The fully-qualified name of the Java class in
   *                     which the data was written.
   * @param  methodName  The name of the method in which the data was
   *                     written.
   * @param  buffer      The byte buffer containing the data that has
   *                     been written.  The byte buffer must be in the
   *                     same state when this method returns as when
   *                     it was entered.
   */
  public abstract void debugBytesWritten(String className,
                                         String methodName,
                                         ByteBuffer buffer);



  /**
   * Writes a message to the debug logger indicating that the
   * constructor for the specified class has been invoked.
   *
   * @param  className  The fully-qualified name of the Java class
   *                    whose constructor has been invoked.
   * @param  args       The set of arguments provided for the
   *                    constructor.
   */
  public abstract void debugConstructor(String className,
                                        String... args);



  /**
   * Writes a message to the debug logger indicating that the
   * specified method has been entered.
   *
   * @param  className   The fully-qualified name of the Java class in
   *                     which the specified method resides.
   * @param  methodName  The name of the method that has been entered.
   * @param  args        The set of arguments provided to the method.
   */
  public abstract void debugEnter(String className, String methodName,
                                  String... args);



  /**
   * Writes a generic message to the debug logger using the provided
   * information.
   *
   * @param  category    The category associated with this debug
   *                     message.
   * @param  severity    The severity associated with this debug
   *                     message.
   * @param  className   The fully-qualified name of the Java class in
   *                     which the debug message was generated.
   * @param  methodName  The name of the method in which the debug
   *                     message was generated.
   * @param  message     The actual contents of the debug message.
   */
  public abstract void debugMessage(DebugLogCategory category,
                                    DebugLogSeverity severity,
                                    String className,
                                    String methodName,
                                    String message);



  /**
   * Writes a message to the debug logger containing information from
   * the provided exception that was thrown.
   *
   * @param  className   The fully-qualified name of the Java class in
   *                     which the exception was thrown.
   * @param  methodName  The name of the method in which the exception
   *                     was thrown.
   * @param  exception   The exception that was thrown.
   */
  public abstract void debugException(String className,
                                      String methodName,
                                      Throwable exception);



  /**
   * Writes a message to the debug logger indicating that the provided
   * protocol element has been read.
   *
   * @param  className        The fully-qualified name of the Java
   *                          class in which the protocol element was
   *                          read.
   * @param  methodName       The name of the method in which the
   *                          protocol element was read.
   * @param  protocolElement  The protocol element that was read.
   */
  public abstract void debugProtocolElementRead(String className,
                            String methodName,
                            ProtocolElement protocolElement);



  /**
   * Writes a message to the debug logger indicating that the provided
   * protocol element has been written.
   *
   * @param  className        The fully-qualified name of the Java
   *                          class in which the protocol element was
   *                          written.
   * @param  methodName       The name of the method in which the
   *                          protocol element was written.
   * @param  protocolElement  The protocol element that was written.
   */
  public abstract void debugProtocolElementWritten(String className,
                            String methodName,
                            ProtocolElement protocolElement);



  /**
   * Indicates whether the provided object is equal to this debug
   * logger.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided object is determined
   *          to be equal to this debug logger, or <CODE>false</CODE>
   *          if not.
   */
  public abstract boolean equals(Object o);



  /**
   * Retrieves the hash code for this debug logger.
   *
   * @return  The hash code for this debug logger.
   */
  public abstract int hashCode();
}

