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
 * used when writing a message to a debug logger.
 */
public enum DebugLogCategory
{
  /**
   * The category that will be used for debug messages relating to
   * access control processing.
   */
  ACCESS_CONTROL(MSGID_DEBUG_CATEGORY_ACCESS_CONTROL),



  /**
   * The category that will be used for debug messages relating to
   * backend processing.
   */
  BACKEND(MSGID_DEBUG_CATEGORY_BACKEND),



  /**
   * The category that will be used for debug messages relating to
   * configuration processing.
   */
  CONFIGURATION(MSGID_DEBUG_CATEGORY_CONFIG),



  /**
   * The category that will be used for debug messages relating to
   * connection handling.
   */
  CONNECTION_HANDLING(MSGID_DEBUG_CATEGORY_CONNECTION_HANDLING),



  /**
   * The category that will be used to indicate that a constructor has
   * been invoked.
   */
  CONSTRUCTOR(MSGID_DEBUG_CATEGORY_CONSTRUCTOR),



  /**
   * The category that will be used for debug messages relating to
   * core server processing.
   */
  CORE_SERVER(MSGID_DEBUG_CATEGORY_CORE_SERVER),



  /**
   * The category that will be used for debug messages relating to raw
   * data read from a client.
   */
  DATA_READ(MSGID_DEBUG_CATEGORY_DATA_READ),



  /**
   * The category that will be used for debug messages relating to raw
   * data written to a client.
   */
  DATA_WRITE(MSGID_DEBUG_CATEGORY_DATA_WRITE),



  /**
   * The category that will be used for debug messages containing
   * details about an exception that was caught.
   */
  EXCEPTION(MSGID_DEBUG_CATEGORY_EXCEPTION),



  /**
   * The category that will be used for debug messages related to
   * processing an extended operation.
   */
  EXTENDED_OPERATION(MSGID_DEBUG_CATEGORY_EXTENDED_OPERATION),



  /**
   * The category that will be used for debug messages related to
   * processing in the directory server extensions.
   */
  EXTENSIONS(MSGID_DEBUG_CATEGORY_EXTENSIONS),



  /**
   * The category that will be used to indicate that a method has been
   * entered.
   */
  METHOD_ENTER(MSGID_DEBUG_CATEGORY_ENTER),



  /**
   * The category that will be used for debug messages related to
   * password policy processing.
   */
  PASSWORD_POLICY(MSGID_DEBUG_CATEGORY_PASSWORD_POLICY),



  /**
   * The category that will be used for debug messages relating to
   * plugin processing.
   */
  PLUGIN(MSGID_DEBUG_CATEGORY_PLUGIN),



  /**
   * The category that will be used for debug messages relating to
   * protocol elements read from a client.
   */
  PROTOCOL_READ(MSGID_DEBUG_CATEGORY_PROTOCOL_READ),



  /**
   * The category that will be used for debug messages relating to
   * protocol elements written to a client.
   */
  PROTOCOL_WRITE(MSGID_DEBUG_CATEGORY_PROTOCOL_WRITE),



  /**
   * The category that will be used for debug messages relating to
   * processing a SASL bind.
   */
  SASL_MECHANISM(MSGID_DEBUG_CATEGORY_SASL_MECHANISM),



  /**
   * The category that will be used for debug messages generated
   * during processing related to schema elements.
   */
  SCHEMA(MSGID_DEBUG_CATEGORY_SCHEMA),



  /**
   * The category that will be used for debug messages generated
   * during the Directory Server shutdown process.
   */
  SHUTDOWN(MSGID_DEBUG_CATEGORY_SHUTDOWN),



  /**
   * The category that will be used for debug messages generated
   * during the Directory Server startup process.
   */
  STARTUP(MSGID_DEBUG_CATEGORY_STARTUP),



  /***
   * The category that will be used for debug messages relating to
   * synchronization processing.
   */
  SYNCHRONIZATION(MSGID_DEBUG_CATEGORY_SYNCHRONIZATION),



  /**
   * The category that will be used for debug messages relating to raw
   * data read from the database.
   */
  DATABASE_READ(MSGID_DEBUG_CATEGORY_DATABASE_READ),



  /**
   * The category that will be used for debug messages relating to raw
   * data written to the database.
   */
  DATABASE_WRITE(MSGID_DEBUG_CATEGORY_DATABASE_WRITE),



  /**
   * The category that will be used for debug messages relating to
   * access to the database.
   */
  DATABASE_ACCESS(MSGID_DEBUG_CATEGORY_DATABASE_ACCESS);



  // The static hash mapping category names to their associated
  // category.
  private static HashMap<String,DebugLogCategory> nameMap;

  // The unique ID that indicates the category for the debug message.
  private int categoryID;

  // The short human-readable name for the category.
  private String categoryName;



  static
  {
    nameMap = new HashMap<String,DebugLogCategory>(19);
    nameMap.put(DEBUG_CATEGORY_ACCESS_CONTROL, ACCESS_CONTROL);
    nameMap.put(DEBUG_CATEGORY_BACKEND, BACKEND);
    nameMap.put(DEBUG_CATEGORY_CONFIG, CONFIGURATION);
    nameMap.put(DEBUG_CATEGORY_CONNECTION_HANDLING,
                CONNECTION_HANDLING);
    nameMap.put(DEBUG_CATEGORY_CONSTRUCTOR, CONSTRUCTOR);
    nameMap.put(DEBUG_CATEGORY_CORE_SERVER, CORE_SERVER);
    nameMap.put(DEBUG_CATEGORY_DATA_READ, DATA_READ);
    nameMap.put(DEBUG_CATEGORY_DATA_WRITE, DATA_WRITE);
    nameMap.put(DEBUG_CATEGORY_ENTER, METHOD_ENTER);
    nameMap.put(DEBUG_CATEGORY_EXCEPTION, EXCEPTION);
    nameMap.put(DEBUG_CATEGORY_EXTENDED_OPERATION,
                EXTENDED_OPERATION);
    nameMap.put(DEBUG_CATEGORY_EXTENSIONS, EXTENSIONS);
    nameMap.put(DEBUG_CATEGORY_PASSWORD_POLICY, PASSWORD_POLICY);
    nameMap.put(DEBUG_CATEGORY_PLUGIN, PLUGIN);
    nameMap.put(DEBUG_CATEGORY_PROTOCOL_READ, PROTOCOL_READ);
    nameMap.put(DEBUG_CATEGORY_PROTOCOL_WRITE, PROTOCOL_WRITE);
    nameMap.put(DEBUG_CATEGORY_SASL_MECHANISM, SASL_MECHANISM);
    nameMap.put(DEBUG_CATEGORY_SCHEMA, SCHEMA);
    nameMap.put(DEBUG_CATEGORY_SHUTDOWN, SHUTDOWN);
    nameMap.put(DEBUG_CATEGORY_STARTUP, STARTUP);
    nameMap.put(DEBUG_CATEGORY_SYNCHRONIZATION, SYNCHRONIZATION);
    nameMap.put(DEBUG_CATEGORY_DATABASE_READ, DATABASE_READ);
    nameMap.put(DEBUG_CATEGORY_DATABASE_WRITE, DATABASE_WRITE);
    nameMap.put(DEBUG_CATEGORY_DATABASE_ACCESS, DATABASE_ACCESS);
  }



  /**
   * Creates a new debug log category with the specified ID.
   *
   * @param  categoryID  The unique ID that indicates the category for
   *                     the debug message.
   */
  private DebugLogCategory(int categoryID)
  {
    this.categoryID   = categoryID;
    this.categoryName = null;
  }



  /**
   * Retrieves the debug log category for the specified name.  The
   * name used must be the default English name for that category.
   *
   * @param  name  The name of the debug log category to retrieve.
   *
   * @return  The debug log category for the specified name, or
   *          <CODE>null</CODE> if no such category exists.
   */
  public static DebugLogCategory getByName(String name)
  {
    return nameMap.get(name);
  }



  /**
   * Retrieves the category ID for this debug log category.
   *
   * @return  The category ID for this debug log category.
   */
  public int getCategoryID()
  {
    return categoryID;
  }



  /**
   * Retrieves the category name for this debug log category.
   *
   * @return  The category name for this debug log category.
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
   * Retrieves a string representation of this debug log category.
   *
   * @return  A string representation of this debug log category.
   */
  public String toString()
  {
    return getCategoryName();
  }
}

