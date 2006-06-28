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



import java.util.HashMap;

import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This enumeration defines the set of possible categories that may be
 * used when writing a message to an error logger.
 */
public enum ErrorLogCategory
{
  /**
   * The error log category that will be used for messages related to
   * access control processing.
   */
  ACCESS_CONTROL(MSGID_ERROR_CATEGORY_ACCESS_CONTROL),



  /**
   * The error log category that will be used for messages related to
   * backend processing.
   */
  BACKEND(MSGID_ERROR_CATEGORY_BACKEND),



  /**
   * The error log category that will be used for messages related to
   * configuration processing.
   */
  CONFIGURATION(MSGID_ERROR_CATEGORY_CONFIG),



  /**
   * The error log category that will be used for messages related to
   * connection handling.
   */
  CONNECTION_HANDLING(MSGID_ERROR_CATEGORY_CONNECTION_HANDLING),



  /**
   * The error log category that will be used for messages related to
   * core server processing.
   */
  CORE_SERVER(MSGID_ERROR_CATEGORY_CORE_SERVER),



  /**
   * The error log category that will be used for messages providing
   * exception information.
   */
  EXCEPTION(MSGID_ERROR_CATEGORY_EXCEPTION),



  /**
   * The error log category that will be used for messages related to
   * extended operation processing.
   */
  EXTENDED_OPERATION(MSGID_ERROR_CATEGORY_EXTENDED_OPERATION),



  /**
   * The error log category that will be used for messages related to
   * processing in Directory Server extensions.
   */
  EXTENSIONS(MSGID_ERROR_CATEGORY_EXTENSIONS),



  /**
   * The error log category that will be used for messages related to
   * plugin processing.
   */
  PLUGIN(MSGID_ERROR_CATEGORY_PLUGIN),



  /**
   * The error log category that will be used for messages related to
   * request handling.
   */
  REQUEST_HANDLING(MSGID_ERROR_CATEGORY_REQUEST_HANDLING),



  /**
   * The error log category that will be used for messages related to
   * SASL bind processing.
   */
  SASL_MECHANISM(MSGID_ERROR_CATEGORY_SASL_MECHANISM),



  /**
   * The error log category that will be used for messages related to
   * schema processing.
   */
  SCHEMA(MSGID_ERROR_CATEGORY_SCHEMA),



  /**
   * The error log category that will be used for messages generated
   * during the Directory Server shutdown process.
   */
  SHUTDOWN(MSGID_ERROR_CATEGORY_SHUTDOWN),



  /**
   * The error log category that will be used for messages generated
   * during the Directory Server startup process.
   */
  STARTUP(MSGID_ERROR_CATEGORY_STARTUP),



  /**
   * The error log category that will be used for messages related to
   * synchronization processing.
   */
  SYNCHRONIZATION(MSGID_ERROR_CATEGORY_SYNCHRONIZATION),



  /**
   * The error log category that will be used for messages related to
   * task processing.
   */
  TASK(MSGID_ERROR_CATEGORY_TASK);



  // The static hash mapping category names to their associated
  // category.
  private static HashMap<String,ErrorLogCategory> nameMap;

  // The unique identifier for this error log category.
  private int categoryID;

  // The short human-readable name for this error log category.
  private String categoryName;



  static
  {
    nameMap = new HashMap<String,ErrorLogCategory>(16);
    nameMap.put(ERROR_CATEGORY_ACCESS_CONTROL, ACCESS_CONTROL);
    nameMap.put(ERROR_CATEGORY_BACKEND, BACKEND);
    nameMap.put(ERROR_CATEGORY_CONFIG, CONFIGURATION);
    nameMap.put(ERROR_CATEGORY_CONNECTION_HANDLING,
                CONNECTION_HANDLING);
    nameMap.put(ERROR_CATEGORY_CORE_SERVER, CORE_SERVER);
    nameMap.put(ERROR_CATEGORY_EXCEPTION, EXCEPTION);
    nameMap.put(ERROR_CATEGORY_EXTENDED_OPERATION,
                EXTENDED_OPERATION);
    nameMap.put(ERROR_CATEGORY_EXTENSIONS, EXTENSIONS);
    nameMap.put(ERROR_CATEGORY_PLUGIN, PLUGIN);
    nameMap.put(ERROR_CATEGORY_REQUEST, REQUEST_HANDLING);
    nameMap.put(ERROR_CATEGORY_SASL_MECHANISM, SASL_MECHANISM);
    nameMap.put(ERROR_CATEGORY_SCHEMA, SCHEMA);
    nameMap.put(ERROR_CATEGORY_SHUTDOWN, SHUTDOWN);
    nameMap.put(ERROR_CATEGORY_STARTUP, STARTUP);
    nameMap.put(ERROR_CATEGORY_SYNCHRONIZATION, SYNCHRONIZATION);
    nameMap.put(ERROR_CATEGORY_TASK, TASK);
  }



  /**
   * Creates a new error log category with the specified category ID.
   *
   * @param  categoryID  The unique identifier for this error log
   *                     category.
   */
  private ErrorLogCategory(int categoryID)
  {
    this.categoryID   = categoryID;
    this.categoryName = null;
  }



  /**
   * Retrieves the error log category for the specified name.  The
   * name used must be the default English name for that category.
   *
   * @param  name  The name of the error log category to retrieve.
   *
   * @return  The error log category for the specified name, or
   *          <CODE>null</CODE> if no such category exists.
   */
  public static ErrorLogCategory getByName(String name)
  {
    return nameMap.get(name);
  }



  /**
   * Retrieves the unique identifier for this error log category.
   *
   * @return  The unique identifier for this error log category.
   */
  public int getCategoryID()
  {
    return categoryID;
  }



  /**
   * Retrieves the short human-readable name for this error log
   * category.
   *
   * @return  The short human-readable name for this error log
   *          category.
   */
  public String getCategoryName()
  {
    if (categoryName == null)
    {
      categoryName = getMessage(categoryID);
    }

    return categoryName;
  }



  /**
   * Retrieves a string representation of this error log category.
   *
   * @return  A string representation of this error log category.
   */
  public String toString()
  {
    return getCategoryName();
  }
}

