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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.admin.server;
import org.opends.messages.Message;



import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.config.ConfigException;
import org.opends.messages.AdminMessages;
import org.opends.server.types.DN;



/**
 * A utility class for converting admin exceptions to config exceptions.
 */
final class ConfigExceptionFactory {

  // The singleton instance.
  private static final ConfigExceptionFactory INSTANCE =
    new ConfigExceptionFactory();



  // Prevent instantiation.
  private ConfigExceptionFactory() {
    // Do nothing.
  }



  /**
   * Get the configuration exception factory instance.
   *
   * @return Returns the configuration exception factory instance.
   */
  public static ConfigExceptionFactory getInstance() {
    return INSTANCE;
  }



  /**
   * Create a configuration exception from a definition decoding exception.
   *
   * @param dn
   *          The dn of the configuration entry that could not be decoded.
   * @param e
   *          The definition decoding exception
   * @return Returns the configuration exception.
   */
  public ConfigException createDecodingExceptionAdaptor(DN dn,
      DefinitionDecodingException e) {
    Message message = AdminMessages.ERR_ADMIN_MANAGED_OBJECT_DECODING_PROBLEM.
        get(String.valueOf(dn), stackTraceToSingleLineString(e));
    return new ConfigException(message, e);
  }



  /**
   * Create a configuration exception from a server managed object decoding
   * exception.
   *
   * @param e
   *          The server managed object decoding exception.
   * @return Returns the configuration exception.
   */

  public ConfigException createDecodingExceptionAdaptor(
      ServerManagedObjectDecodingException e) {
    DN dn = e.getPartialManagedObject().getDN();
    Message message =
            AdminMessages.ERR_ADMIN_MANAGED_OBJECT_DECODING_PROBLEM.get(
                    String.valueOf(dn),
        stackTraceToSingleLineString(e));
    return new ConfigException(message, e);
  }



  /**
   * Create a configuration exception from a constraints violation
   * decoding exception.
   *
   * @param e
   *          The constraints violation decoding exception.
   * @return Returns the configuration exception.
   */
  public ConfigException createDecodingExceptionAdaptor(
      ConstraintViolationException e) {
    DN dn = e.getManagedObject().getDN();
    Message message = AdminMessages.ERR_ADMIN_MANAGED_OBJECT_DECODING_PROBLEM
        .get(String.valueOf(dn), stackTraceToSingleLineString(e));
    return new ConfigException(message, e);
  }



  /**
   * Create an exception that describes a problem that occurred when
   * attempting to load and instantiate a class.
   *
   * @param dn
   *          The dn of the configuration entry was being processed.
   * @param className
   *          The name of the class that could not be loaded or
   *          instantiated.
   * @param e
   *          The exception that occurred.
   * @return Returns the configuration exception.
   */

  public ConfigException createClassLoadingExceptionAdaptor(DN dn,
      String className, Exception e) {
    Message message = AdminMessages.ERR_ADMIN_CANNOT_INSTANTIATE_CLASS.
        get(String.valueOf(className), String.valueOf(dn),
            stackTraceToSingleLineString(e));
    return new ConfigException(message, e);
  }
}
