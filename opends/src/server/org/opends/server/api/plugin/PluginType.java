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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.api.plugin;



import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;



/**
 * This class defines an enumeration containing the types of plugins
 * that are supported for use in the Directory Server.
 */
public enum PluginType
{
  /**
   * The plugin type for plugins that are invoked when the Directory
   * Server is starting up.
   */
  STARTUP(PluginType.NAME_STARTUP),



  /**
   * The plugin type for plugins that are invoked when the Directory
   * Server is performing a graceful shutdown.
   */
  SHUTDOWN(PluginType.NAME_SHUTDOWN),



  /**
   * The plugin type for plugins that are to be invoked whenever a new
   * client connection is established.
   */
  POST_CONNECT(PluginType.NAME_POST_CONNECT),



  /**
   * The plugin type for plugins that are to be invoked whenever a
   * client connection is closed.
   */
  POST_DISCONNECT(PluginType.NAME_POST_DISCONNECT),



  /**
   * The plugin type for plugins that are to be invoked for each entry
   * read during an LDIF import.
   */
  LDIF_IMPORT(PluginType.NAME_LDIF_IMPORT),



  /**
   * The plugin type for plugins that are to be invoked for each entry
   * written during an LDIF export.
   */
  LDIF_EXPORT(PluginType.NAME_LDIF_EXPORT),



  /**
   * The plugin type for plugins that are to be invoked before
   * processing begins on an abandon operation.
   */
  PRE_PARSE_ABANDON(PluginType.NAME_PRE_PARSE_ABANDON),



  /**
   * The plugin type for plugins that are to be invoked before
   * processing begins on an add operation.
   */
  PRE_PARSE_ADD(PluginType.NAME_PRE_PARSE_ADD),



  /**
   * The plugin type for plugins that are to be invoked before
   * processing begins on a bind operation.
   */
  PRE_PARSE_BIND(PluginType.NAME_PRE_PARSE_BIND),



  /**
   * The plugin type for plugins that are to be invoked before
   * processing begins on a compare operation.
   */
  PRE_PARSE_COMPARE(PluginType.NAME_PRE_PARSE_COMPARE),



  /**
   * The plugin type for plugins that are to be invoked before
   * processing begins on a delete operation.
   */
  PRE_PARSE_DELETE(PluginType.NAME_PRE_PARSE_DELETE),



  /**
   * The plugin type for plugins that are to be invoked before
   * processing begins on an extended operation.
   */
  PRE_PARSE_EXTENDED(PluginType.NAME_PRE_PARSE_EXTENDED),



  /**
   * The plugin type for plugins that are to be invoked before
   * processing begins on a modify operation.
   */
  PRE_PARSE_MODIFY(PluginType.NAME_PRE_PARSE_MODIFY),



  /**
   * The plugin type for plugins that are to be invoked before
   * processing begins on a modify DN operation.
   */
  PRE_PARSE_MODIFY_DN(PluginType.NAME_PRE_PARSE_MODIFY_DN),



  /**
   * The plugin type for plugins that are to be invoked before
   * processing begins on a search operation.
   */
  PRE_PARSE_SEARCH(PluginType.NAME_PRE_PARSE_SEARCH),



  /**
   * The plugin type for plugins that are to be invoked before
   * processing begins on an unbind operation.
   */
  PRE_PARSE_UNBIND(PluginType.NAME_PRE_PARSE_UNBIND),



  /**
   * The plugin type for plugins that are to be invoked just before
   * the core processing for an add operation.
   */
  PRE_OPERATION_ADD(PluginType.NAME_PRE_OPERATION_ADD),



  /**
   * The plugin type for plugins that are to be invoked just before
   * the core processing for a bind operation.
   */
  PRE_OPERATION_BIND(PluginType.NAME_PRE_OPERATION_BIND),



  /**
   * The plugin type for plugins that are to be invoked just before
   * the core processing for a compare operation.
   */
  PRE_OPERATION_COMPARE(PluginType.NAME_PRE_OPERATION_COMPARE),



  /**
   * The plugin type for plugins that are to be invoked just before
   * the core processing for a delete operation.
   */
  PRE_OPERATION_DELETE(PluginType.NAME_PRE_OPERATION_DELETE),



  /**
   * The plugin type for plugins that are to be invoked just before
   * the core processing for an extended operation.
   */
  PRE_OPERATION_EXTENDED(PluginType.NAME_PRE_OPERATION_EXTENDED),



  /**
   * The plugin type for plugins that are to be invoked just before
   * the core processing for a modify operation.
   */
  PRE_OPERATION_MODIFY(PluginType.NAME_PRE_OPERATION_MODIFY),



  /**
   * The plugin type for plugins that are to be invoked just before
   * the core processing for a modify DN operation.
   */
  PRE_OPERATION_MODIFY_DN(PluginType.NAME_PRE_OPERATION_MODIFY_DN),



  /**
   * The plugin type for plugins that are to be invoked just before
   * the core processing for a search operation.
   */
  PRE_OPERATION_SEARCH(PluginType.NAME_PRE_OPERATION_SEARCH),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * core processing for an abandon operation.
   */
  POST_OPERATION_ABANDON(PluginType.NAME_POST_OPERATION_ABANDON),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * core processing for an add operation.
   */
  POST_OPERATION_ADD(PluginType.NAME_POST_OPERATION_ADD),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * core processing for a bind operation.
   */
  POST_OPERATION_BIND(PluginType.NAME_POST_OPERATION_BIND),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * core processing for a compare operation.
   */
  POST_OPERATION_COMPARE(PluginType.NAME_POST_OPERATION_COMPARE),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * core processing for a delete operation.
   */
  POST_OPERATION_DELETE(PluginType.NAME_POST_OPERATION_DELETE),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * core processing for an extended operation.
   */
  POST_OPERATION_EXTENDED(PluginType.NAME_POST_OPERATION_EXTENDED),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * core processing for a modify operation.
   */
  POST_OPERATION_MODIFY(PluginType.NAME_POST_OPERATION_MODIFY),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * core processing for a modify DN operation.
   */
  POST_OPERATION_MODIFY_DN(PluginType.NAME_POST_OPERATION_MODIFY_DN),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * core processing for a search operation.
   */
  POST_OPERATION_SEARCH(PluginType.NAME_POST_OPERATION_SEARCH),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * core processing for an unbind operation.
   */
  POST_OPERATION_UNBIND(PluginType.NAME_POST_OPERATION_UNBIND),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * response is sent for an add operation.
   */
  POST_RESPONSE_ADD(PluginType.NAME_POST_RESPONSE_ADD),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * response is sent for a bind operation.
   */
  POST_RESPONSE_BIND(PluginType.NAME_POST_RESPONSE_BIND),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * response is sent for a compare operation.
   */
  POST_RESPONSE_COMPARE(PluginType.NAME_POST_RESPONSE_COMPARE),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * response is sent for a delete operation.
   */
  POST_RESPONSE_DELETE(PluginType.NAME_POST_RESPONSE_DELETE),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * response is sent for an extended operation.
   */
  POST_RESPONSE_EXTENDED(PluginType.NAME_POST_RESPONSE_EXTENDED),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * response is sent for a modify operation.
   */
  POST_RESPONSE_MODIFY(PluginType.NAME_POST_RESPONSE_MODIFY),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * response is sent for a modify DN operation.
   */
  POST_RESPONSE_MODIFY_DN(PluginType.NAME_POST_RESPONSE_MODIFY_DN),



  /**
   * The plugin type for plugins that are to be invoked just after the
   * response is sent for a search operation.
   */
  POST_RESPONSE_SEARCH(PluginType.NAME_POST_RESPONSE_SEARCH),



  /**
   * The plugin type for plugins that are to be invoked before each
   * search result entry is sent to a client.
   */
  SEARCH_RESULT_ENTRY(PluginType.NAME_SEARCH_ENTRY),



  /**
   * The plugin type for plugins that are to be invoked before each
   * search result reference is sent to a client.
   */
  SEARCH_RESULT_REFERENCE(PluginType.NAME_SEARCH_REFERENCE),



  /**
   * The plugin type for plugins that are to be invoked before each
   * intermediate response message is sent to a client.
   */
  INTERMEDIATE_RESPONSE(PluginType.NAME_INTERMEDIATE_RESPONSE);



  /**
   * The name that will be used for startup plugins.
   */
  private static final String NAME_STARTUP = "startup";



  /**
   * The name that will be used for shutdown plugins.
   */
  private static final String NAME_SHUTDOWN = "shutdown";



  /**
   * The name that will be used for post-connect plugins.
   */
  private static final String NAME_POST_CONNECT = "postconnect";



  /**
   * The name that will be used for post-disconnect plugins.
   */
  private static final String NAME_POST_DISCONNECT = "postdisconnect";



  /**
   * The name that will be used for LDIF import plugins.
   */
  private static final String NAME_LDIF_IMPORT = "ldifimport";



  /**
   * The name that will be used for LDIF export plugins.
   */
  private static final String NAME_LDIF_EXPORT = "ldifexport";



  /**
   * The name that will be used for pre-parse abandon plugins.
   */
  private static final String NAME_PRE_PARSE_ABANDON =
       "preparseabandon";



  /**
   * The name that will be used for pre-parse add plugins.
   */
  private static final String NAME_PRE_PARSE_ADD = "preparseadd";



  /**
   * The name that will be used for pre-parse bind plugins.
   */
  private static final String NAME_PRE_PARSE_BIND = "preparsebind";



  /**
   * The name that will be used for pre-parse compare plugins.
   */
  private static final String NAME_PRE_PARSE_COMPARE =
       "preparsecompare";



  /**
   * The name that will be used for pre-parse delete plugins.
   */
  private static final String NAME_PRE_PARSE_DELETE =
       "preparsedelete";



  /**
   * The name that will be used for pre-parse extended plugins.
   */
  private static final String NAME_PRE_PARSE_EXTENDED =
       "preparseextended";



  /**
   * The name that will be used for pre-parse modify plugins.
   */
  private static final String NAME_PRE_PARSE_MODIFY =
       "preparsemodify";



  /**
   * The name that will be used for pre-parse modify DN plugins.
   */
  private static final String NAME_PRE_PARSE_MODIFY_DN =
       "preparsemodifydn";



  /**
   * The name that will be used for pre-parse search plugins.
   */
  private static final String NAME_PRE_PARSE_SEARCH =
       "preparsesearch";



  /**
   * The name that will be used for pre-parse unbind plugins.
   */
  private static final String NAME_PRE_PARSE_UNBIND =
       "preparseunbind";



  /**
   * The name that will be used for pre-operation add plugins.
   */
  private static final String NAME_PRE_OPERATION_ADD =
       "preoperationadd";



  /**
   * The name that will be used for pre-operation bind plugins.
   */
  private static final String NAME_PRE_OPERATION_BIND =
       "preoperationbind";



  /**
   * The name that will be used for pre-operation compare plugins.
   */
  private static final String NAME_PRE_OPERATION_COMPARE =
       "preoperationcompare";



  /**
   * The name that will be used for pre-operation delete plugins.
   */
  private static final String NAME_PRE_OPERATION_DELETE =
       "preoperationdelete";



  /**
   * The name that will be used for pre-operation extended plugins.
   */
  private static final String NAME_PRE_OPERATION_EXTENDED =
       "preoperationextended";



  /**
   * The name that will be used for pre-operation modify plugins.
   */
  private static final String NAME_PRE_OPERATION_MODIFY =
       "preoperationmodify";



  /**
   * The name that will be used for pre-operation modify DN plugins.
   */
  private static final String NAME_PRE_OPERATION_MODIFY_DN =
       "preoperationmodifydn";



  /**
   * The name that will be used for pre-operation search plugins.
   */
  private static final String NAME_PRE_OPERATION_SEARCH =
       "preoperationsearch";



  /**
   * The name that will be used for post-operation abandon plugins.
   */
  private static final String NAME_POST_OPERATION_ABANDON =
       "postoperationabandon";



  /**
   * The name that will be used for post-operation add plugins.
   */
  private static final String NAME_POST_OPERATION_ADD =
       "postoperationadd";



  /**
   * The name that will be used for post-operation bind plugins.
   */
  private static final String NAME_POST_OPERATION_BIND =
       "postoperationbind";



  /**
   * The name that will be used for post-operation compare plugins.
   */
  private static final String NAME_POST_OPERATION_COMPARE =
       "postoperationcompare";



  /**
   * The name that will be used for post-operation delete plugins.
   */
  private static final String NAME_POST_OPERATION_DELETE =
       "postoperationdelete";



  /**
   * The name that will be used for post-operation extended plugins.
   */
  private static final String NAME_POST_OPERATION_EXTENDED =
       "postoperationextended";



  /**
   * The name that will be used for post-operation modify plugins.
   */
  private static final String NAME_POST_OPERATION_MODIFY =
       "postoperationmodify";



  /**
   * The name that will be used for post-operation modify DN plugins.
   */
  private static final String NAME_POST_OPERATION_MODIFY_DN =
       "postoperationmodifydn";



  /**
   * The name that will be used for post-operation search plugins.
   */
  private static final String NAME_POST_OPERATION_SEARCH =
       "postoperationsearch";



  /**
   * The name that will be used for post-operation unbind plugins.
   */
  private static final String NAME_POST_OPERATION_UNBIND =
       "postoperationunbind";



  /**
   * The name that will be used for post-response add plugins.
   */
  private static final String NAME_POST_RESPONSE_ADD =
       "postresponseadd";



  /**
   * The name that will be used for post-response bind plugins.
   */
  private static final String NAME_POST_RESPONSE_BIND =
       "postresponsebind";



  /**
   * The name that will be used for post-response compare plugins.
   */
  private static final String NAME_POST_RESPONSE_COMPARE =
       "postresponsecompare";



  /**
   * The name that will be used for post-response delete plugins.
   */
  private static final String NAME_POST_RESPONSE_DELETE =
       "postresponsedelete";



  /**
   * The name that will be used for post-response extended plugins.
   */
  private static final String NAME_POST_RESPONSE_EXTENDED =
       "postresponseextended";



  /**
   * The name that will be used for post-response modify plugins.
   */
  private static final String NAME_POST_RESPONSE_MODIFY =
       "postresponsemodify";



  /**
   * The name that will be used for post-response modify DN plugins.
   */
  private static final String NAME_POST_RESPONSE_MODIFY_DN =
       "postresponsemodifydn";



  /**
   * The name that will be used for post-response search plugins.
   */
  private static final String NAME_POST_RESPONSE_SEARCH =
       "postresponsesearch";



  /**
   * The name that will be used for search result entry plugins.
   */
  private static final String NAME_SEARCH_ENTRY = "searchresultentry";



  /**
   * The name that will be used for search result reference plugins.
   */
  private static final String NAME_SEARCH_REFERENCE =
      "searchresultreference";



  /**
   * The name that will be used for intermediate response plugins.
   */
  private static final String NAME_INTERMEDIATE_RESPONSE =
       "intermediateresponse";



  /**
   * A hash set containing the names of all the available plugin
   * types.
   */
  private static final Set<String> PLUGIN_TYPE_NAMES =
       new HashSet<String>(45);



  /**
   * A hash map that relates the plugin type names to the
   * corresponding plugin type.
   */
  private static final Map<String,PluginType> PLUGIN_TYPE_MAP =
       new HashMap<String,PluginType>(45);



  static
  {
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_STARTUP);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_SHUTDOWN);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_POST_CONNECT);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_POST_DISCONNECT);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_LDIF_IMPORT);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_LDIF_EXPORT);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_PRE_PARSE_ABANDON);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_PRE_PARSE_ADD);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_PRE_PARSE_BIND);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_PRE_PARSE_COMPARE);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_PRE_PARSE_DELETE);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_PRE_PARSE_EXTENDED);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_PRE_PARSE_MODIFY);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_PRE_PARSE_MODIFY_DN);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_PRE_PARSE_SEARCH);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_PRE_PARSE_UNBIND);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_PRE_OPERATION_ADD);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_PRE_OPERATION_BIND);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_PRE_OPERATION_COMPARE);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_PRE_OPERATION_DELETE);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_PRE_OPERATION_EXTENDED);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_PRE_OPERATION_MODIFY);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_PRE_OPERATION_MODIFY_DN);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_PRE_OPERATION_SEARCH);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_POST_OPERATION_ABANDON);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_POST_OPERATION_ADD);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_POST_OPERATION_BIND);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_POST_OPERATION_COMPARE);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_POST_OPERATION_DELETE);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_POST_OPERATION_EXTENDED);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_POST_OPERATION_MODIFY);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_POST_OPERATION_MODIFY_DN);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_POST_OPERATION_SEARCH);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_POST_OPERATION_UNBIND);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_POST_RESPONSE_ADD);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_POST_RESPONSE_BIND);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_POST_RESPONSE_COMPARE);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_POST_RESPONSE_DELETE);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_POST_RESPONSE_EXTENDED);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_POST_RESPONSE_MODIFY);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_POST_RESPONSE_MODIFY_DN);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_POST_RESPONSE_SEARCH);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_SEARCH_ENTRY);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_SEARCH_REFERENCE);
    PLUGIN_TYPE_NAMES.add(PluginType.NAME_INTERMEDIATE_RESPONSE);

    PLUGIN_TYPE_MAP.put(PluginType.NAME_STARTUP, PluginType.STARTUP);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_SHUTDOWN,
                        PluginType.SHUTDOWN);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_POST_CONNECT,
                        PluginType.POST_CONNECT);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_POST_DISCONNECT,
                        PluginType.POST_DISCONNECT);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_LDIF_IMPORT,
                        PluginType.LDIF_IMPORT);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_LDIF_EXPORT,
                        PluginType.LDIF_EXPORT);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_PRE_PARSE_ABANDON,
                        PluginType.PRE_PARSE_ABANDON);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_PRE_PARSE_ADD,
                        PluginType.PRE_PARSE_ADD);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_PRE_PARSE_BIND,
                        PluginType.PRE_PARSE_BIND);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_PRE_PARSE_COMPARE,
                        PluginType.PRE_PARSE_COMPARE);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_PRE_PARSE_DELETE,
                        PluginType.PRE_PARSE_DELETE);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_PRE_PARSE_EXTENDED,
                        PluginType.PRE_PARSE_EXTENDED);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_PRE_PARSE_MODIFY,
                        PluginType.PRE_PARSE_MODIFY);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_PRE_PARSE_MODIFY_DN,
                        PluginType.PRE_PARSE_MODIFY_DN);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_PRE_PARSE_SEARCH,
                        PluginType.PRE_PARSE_SEARCH);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_PRE_PARSE_UNBIND,
                        PluginType.PRE_PARSE_UNBIND);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_PRE_OPERATION_ADD,
                        PluginType.PRE_OPERATION_ADD);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_PRE_OPERATION_BIND,
                        PluginType.PRE_OPERATION_BIND);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_PRE_OPERATION_COMPARE,
                        PluginType.PRE_OPERATION_COMPARE);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_PRE_OPERATION_DELETE,
                        PluginType.PRE_OPERATION_DELETE);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_PRE_OPERATION_EXTENDED,
                        PluginType.PRE_OPERATION_EXTENDED);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_PRE_OPERATION_MODIFY,
                        PluginType.PRE_OPERATION_MODIFY);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_PRE_OPERATION_MODIFY_DN,
                        PluginType.PRE_OPERATION_MODIFY_DN);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_PRE_OPERATION_SEARCH,
                        PluginType.PRE_OPERATION_SEARCH);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_POST_OPERATION_ABANDON,
                        PluginType.POST_OPERATION_ABANDON);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_POST_OPERATION_ADD,
                        PluginType.POST_OPERATION_ADD);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_POST_OPERATION_BIND,
                        PluginType.POST_OPERATION_BIND);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_POST_OPERATION_COMPARE,
                        PluginType.POST_OPERATION_COMPARE);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_POST_OPERATION_DELETE,
                        PluginType.POST_OPERATION_DELETE);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_POST_OPERATION_EXTENDED,
                        PluginType.POST_OPERATION_EXTENDED);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_POST_OPERATION_MODIFY,
                        PluginType.POST_OPERATION_MODIFY);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_POST_OPERATION_MODIFY_DN,
                        PluginType.POST_OPERATION_MODIFY_DN);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_POST_OPERATION_SEARCH,
                        PluginType.POST_OPERATION_SEARCH);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_POST_OPERATION_UNBIND,
                        PluginType.POST_OPERATION_UNBIND);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_POST_RESPONSE_ADD,
                        PluginType.POST_RESPONSE_ADD);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_POST_RESPONSE_BIND,
                        PluginType.POST_RESPONSE_BIND);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_POST_RESPONSE_COMPARE,
                        PluginType.POST_RESPONSE_COMPARE);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_POST_RESPONSE_DELETE,
                        PluginType.POST_RESPONSE_DELETE);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_POST_RESPONSE_EXTENDED,
                        PluginType.POST_RESPONSE_EXTENDED);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_POST_RESPONSE_MODIFY,
                        PluginType.POST_RESPONSE_MODIFY);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_POST_RESPONSE_MODIFY_DN,
                        PluginType.POST_RESPONSE_MODIFY_DN);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_POST_RESPONSE_SEARCH,
                        PluginType.POST_RESPONSE_SEARCH);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_SEARCH_ENTRY,
                        PluginType.SEARCH_RESULT_ENTRY);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_SEARCH_REFERENCE,
                        PluginType.SEARCH_RESULT_REFERENCE);
    PLUGIN_TYPE_MAP.put(PluginType.NAME_INTERMEDIATE_RESPONSE,
                        PluginType.INTERMEDIATE_RESPONSE);
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
    return PLUGIN_TYPE_NAMES;
  }



  /**
   * Retrieves the plugin type for the plugin with the specified name.
   *
   * @param  lowerName  The name of the plugin type to retrieve,
   *                    formatted in all lowercase characters.
   *
   * @return  The requested plugin type, or <CODE>null</CODE> if there
   *          is no type for the provided name.
   */
  public static PluginType forName(String lowerName)
  {
    return PLUGIN_TYPE_MAP.get(lowerName);
  }
}

