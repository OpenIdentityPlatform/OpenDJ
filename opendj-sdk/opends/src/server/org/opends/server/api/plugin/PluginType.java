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
package org.opends.server.api.plugin;



import java.util.HashMap;
import java.util.Map;
import java.util.Set;



/**
 * This class defines an enumeration containing the types of plugins
 * that are supported for use in the Directory Server.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public enum PluginType
{
  /**
   * The plugin type for plugins that are invoked when the Directory
   * Server is starting up.
   */
  STARTUP("startup"),



  /**
   * The plugin type for plugins that are invoked when the Directory
   * Server is performing a graceful shutdown.
   */
  SHUTDOWN("shutdown"),



  /**
   * The plugin type for plugins that are to be invoked whenever a new
   * client connection is established.
   */
  POST_CONNECT("postconnect"),



  /**
   * The plugin type for plugins that are to be invoked whenever a
   * client connection is closed.
   */
  POST_DISCONNECT("postdisconnect"),



  /**
   * The plugin type for plugins that are to be invoked for each entry
   * read during an LDIF import.
   */
  LDIF_IMPORT("ldifimport"),



  /**
   * The plugin type for plugins that are to be invoked for each entry
   * written during an LDIF export.
   */
  LDIF_EXPORT("ldifexport"),



  /**
   * The plugin type for plugins that are to be invoked before
   * processing begins on an abandon operation.
   */
  PRE_PARSE_ABANDON("preparseabandon"),



  /**
   * The plugin type for plugins that are to be invoked before
   * processing begins on an add operation.
   */
  PRE_PARSE_ADD("preparseadd"),



  /**
   * The plugin type for plugins that are to be invoked before
   * processing begins on a bind operation.
   */
  PRE_PARSE_BIND("preparsebind"),



  /**
   * The plugin type for plugins that are to be invoked before
   * processing begins on a compare operation.
   */
  PRE_PARSE_COMPARE("preparsecompare"),



  /**
   * The plugin type for plugins that are to be invoked before
   * processing begins on a delete operation.
   */
  PRE_PARSE_DELETE("preparsedelete"),



  /**
   * The plugin type for plugins that are to be invoked before
   * processing begins on an extended operation.
   */
  PRE_PARSE_EXTENDED("preparseextended"),



  /**
   * The plugin type for plugins that are to be invoked before
   * processing begins on a modify operation.
   */
  PRE_PARSE_MODIFY("preparsemodify"),



  /**
   * The plugin type for plugins that are to be invoked before
   * processing begins on a modify DN operation.
   */
  PRE_PARSE_MODIFY_DN("preparsemodifydn"),



  /**
   * The plugin type for plugins that are to be invoked before
   * processing begins on a search operation.
   */
  PRE_PARSE_SEARCH("preparsesearch"),



  /**
   * The plugin type for plugins that are to be invoked before
   * processing begins on an unbind operation.
   */
  PRE_PARSE_UNBIND("preparseunbind"),



  /**
   * The plugin type for plugins that are to be invoked just before
   * the core processing for an add operation.
   */
  PRE_OPERATION_ADD("preoperationadd"),



  /**
   * The plugin type for plugins that are to be invoked just before
   * the core processing for a bind operation.
   */
  PRE_OPERATION_BIND("preoperationbind"),



  /**
   * The plugin type for plugins that are to be invoked just before
   * the core processing for a compare operation.
   */
  PRE_OPERATION_COMPARE("preoperationcompare"),



  /**
   * The plugin type for plugins that are to be invoked just before
   * the core processing for a delete operation.
   */
  PRE_OPERATION_DELETE("preoperationdelete"),



  /**
   * The plugin type for plugins that are to be invoked just before
   * the core processing for an extended operation.
   */
  PRE_OPERATION_EXTENDED("preoperationextended"),



  /**
   * The plugin type for plugins that are to be invoked just before
   * the core processing for a modify operation.
   */
  PRE_OPERATION_MODIFY("preoperationmodify"),



  /**
   * The plugin type for plugins that are to be invoked just before
   * the core processing for a modify DN operation.
   */
  PRE_OPERATION_MODIFY_DN("preoperationmodifydn"),



  /**
   * The plugin type for plugins that are to be invoked just before
   * the core processing for a search operation.
   */
  PRE_OPERATION_SEARCH("preoperationsearch"),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * core processing for an abandon operation.
   */
  POST_OPERATION_ABANDON("postoperationabandon"),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * core processing for an add operation.
   */
  POST_OPERATION_ADD("postoperationadd"),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * core processing for a bind operation.
   */
  POST_OPERATION_BIND("postoperationbind"),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * core processing for a compare operation.
   */
  POST_OPERATION_COMPARE("postoperationcompare"),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * core processing for a delete operation.
   */
  POST_OPERATION_DELETE("postoperationdelete"),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * core processing for an extended operation.
   */
  POST_OPERATION_EXTENDED("postoperationextended"),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * core processing for a modify operation.
   */
  POST_OPERATION_MODIFY("postoperationmodify"),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * core processing for a modify DN operation.
   */
  POST_OPERATION_MODIFY_DN("postoperationmodifydn"),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * core processing for a search operation.
   */
  POST_OPERATION_SEARCH("postoperationsearch"),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * core processing for an unbind operation.
   */
  POST_OPERATION_UNBIND("postoperationunbind"),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * response is sent for an add operation.
   */
  POST_RESPONSE_ADD("postresponseadd"),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * response is sent for a bind operation.
   */
  POST_RESPONSE_BIND("postresponsebind"),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * response is sent for a compare operation.
   */
  POST_RESPONSE_COMPARE("postresponsecompare"),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * response is sent for a delete operation.
   */
  POST_RESPONSE_DELETE("postresponsedelete"),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * response is sent for an extended operation.
   */
  POST_RESPONSE_EXTENDED("postresponseextended"),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * response is sent for a modify operation.
   */
  POST_RESPONSE_MODIFY("postresponsemodify"),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * response is sent for a modify DN operation.
   */
  POST_RESPONSE_MODIFY_DN("postresponsemodifydn"),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * response is sent for a search operation.
   */
  POST_RESPONSE_SEARCH("postresponsesearch"),



  /**
   * The plugin type for plugins that are to be invoked just after an
   * add operation has been completed via synchronization.
   */
  POST_SYNCHRONIZATION_ADD("postsynchronizationadd"),



  /**
   * The plugin type for plugins that are to be invoked just after a
   * delete operation has been completed via synchronization.
   */
  POST_SYNCHRONIZATION_DELETE(
       "postsynchronizationdelete"),



  /**
   * The plugin type for plugins that are to be invoked just after a
   * modify operation has been completed via synchronization.
   */
  POST_SYNCHRONIZATION_MODIFY(
       "postsynchronizationmodify"),



  /**
   * The plugin type for plugins that are to be invoked just after a
   * modify DN operation has been completed via synchronization.
   */
  POST_SYNCHRONIZATION_MODIFY_DN(
       "postsynchronizationmodifydn"),



  /**
   * The plugin type for plugins that are to be invoked before each
   * search result entry is sent to a client.
   */
  SEARCH_RESULT_ENTRY("searchresultentry"),



  /**
   * The plugin type for plugins that are to be invoked before each
   * search result reference is sent to a client.
   */
  SEARCH_RESULT_REFERENCE("searchresultreference"),



  /**
   * The plugin type for plugins that are to be invoked on each
   * subordinate entry that is moved or renamed as part of a modify DN
   * operation.
   */
  SUBORDINATE_MODIFY_DN("subordinatemodifydn"),



  /**
   * The plugin type for plugins that are to be invoked before each
   * intermediate response message is sent to a client.
   */
  INTERMEDIATE_RESPONSE("intermediateresponse");



  // A hash map that relates the plugin type names to the
  // corresponding plugin type.
  private static final Map<String, PluginType> PLUGIN_TYPE_MAP;
  static
  {
    PLUGIN_TYPE_MAP =
        new HashMap<String, PluginType>(PluginType.values().length);
    for (PluginType type : PluginType.values())
    {
      PLUGIN_TYPE_MAP.put(type.name, type);
    }
  }



  // The name for this plugin type.
  private String name;



  /**
   * Creates a new plugin type instance with the specified name.
   *
   * @param  name  The name to use for this plugin type.
   */
  private PluginType(String name)
  {
    this.name = name;
  }



  /**
   * Retrieves the name for this plugin type.
   *
   * @return  The name for this plugin type.
   */
  public String getName()
  {
    return name;
  }



  /**
   * Retrieves a string representation of this plugin type.
   *
   * @return  A string representation of this plugin type.
   */
  @Override
  public String toString()
  {
    return name;
  }



  /**
   * Retrieves a hash set containing the names of all the plugin
   * types.
   *
   * @return  A hash set containing the names of all the plugin types.
   */
  public static Set<String> getPluginTypeNames()
  {
    return PLUGIN_TYPE_MAP.keySet();
  }



  /**
   * Retrieves the plugin type for the plugin with the specified name.
   *
   * @param  lowerName  The name of the plugin type to retrieve,
   *                    formatted in all lowercase characters.
   *
   * @return  The requested plugin type, or {@code null} if there is
   *          no type for the provided name.
   */
  public static PluginType forName(String lowerName)
  {
    return PLUGIN_TYPE_MAP.get(lowerName);
  }
}

