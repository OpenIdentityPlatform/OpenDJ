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
package org.opends.server.api.plugin;



import java.util.Set;

import org.opends.server.api.ClientConnection;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.IntermediateResponse;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.operation.*;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.PluginMessages.*;



/**
 * This class defines the set of methods and structures that are
 * available for use in Directory Server plugins.  This is a single
 * class that may be used for all types of plugins, and an individual
 * plugin only needs to implement the specific methods that are
 * applicable to that particular plugin type.
 */
public abstract class DirectoryServerPlugin
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.api.plugin.DirectoryServerPlugin";



  // The DN of the configuration entry for this plugin.
  private DN pluginDN;

  // The plugin types for which this plugin is registered.
  private Set<PluginType> pluginTypes;



  /**
   * Creates a new instance of this Directory Server plugin.  Every
   * plugin must implement a default constructor (it is the only one
   * that will be used to create plugins defined in the
   * configuration), and every plugin constructor must call
   * <CODE>super()</CODE> as its first element.
   */
  protected DirectoryServerPlugin()
  {
    assert debugConstructor(CLASS_NAME);
  }



  /**
   * Performs any initialization that should be done for all types of
   * plugins regardless of type.  This should only be called by the
   * core Directory Server code during the course of loading a plugin.
   *
   * @param  pluginDN     The DN of the plugin configuration entry.
   * @param  pluginTypes  The set of plugin types for which this
   *                      plugin is registered.
   */
  public final void initializeInternal(DN pluginDN,
                                       Set<PluginType> pluginTypes)
  {
    assert debugEnter(CLASS_NAME, "initializeInternal",
                      String.valueOf(pluginDN),
                      String.valueOf(pluginTypes));

    this.pluginDN    = pluginDN;
    this.pluginTypes = pluginTypes;
  }



  /**
   * Performs any initialization necessary for this plugin.  This will
   * be called as soon as the plugin has been loaded and before it is
   * registered with the server.
   *
   * @param  pluginTypes  The set of plugin types that indicate the
   *                      ways in which this plugin will be invoked.
   * @param  configEntry  The entry containing the configuration
   *                      information for this plugin.
   *
   * @throws  ConfigException  If the provided entry does not contain
   *                           a valid configuration for this plugin.
   *
   * @throws  InitializationException  If a problem occurs while
   *                                   initializing the plugin that is
   *                                   not related to the server
   *                                   configuration.
   */
  public abstract void initializePlugin(Set<PluginType> pluginTypes,
                                        ConfigEntry configEntry)
         throws ConfigException, InitializationException;



  /**
   * Performs any necessary finalization for this plugin.  This will
   * be called just after the plugin has been deregistered with the
   * server but before it has been unloaded.
   */
  public void finalizePlugin()
  {
    assert debugEnter(CLASS_NAME, "finalizePlugin");

    // No implementation is required by default.
  }



  /**
   * Retrieves the DN of the configuration entry for this plugin.
   *
   * @return  The DN of the configuration entry for this plugin.
   */
  public final DN getPluginEntryDN()
  {
    assert debugEnter(CLASS_NAME, "getPluginEntryDN");

    return pluginDN;
  }



  /**
   * Retrieves the plugin types for which this plugin is registered.
   * This set must not be modified.
   *
   * @return  The plugin types for which this plugin is registered.
   */
  public final Set<PluginType> getPluginTypes()
  {
    assert debugEnter(CLASS_NAME, "getPluginTypes");

    return pluginTypes;
  }



  /**
   * Performs any processing that should be done when the Directory
   * Server is in the process of starting.  This method will be called
   * after virtually all other initialization has been performed but
   * before the connection handlers are started.
   *
   * @return  The result of the startup plugin processing.
   */
  public StartupPluginResult doStartup()
  {
    assert debugEnter(CLASS_NAME, "doStartup");

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                                PluginType.STARTUP.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any processing that should be done when the Directory
   * Server is in the process of performing a graceful shutdown.  This
   * method will be called early in the shutdown process after the
   * connection handlers are stopped but before other finalization is
   * performed.
   */
  public void doShutdown()
  {
    assert debugEnter(CLASS_NAME, "doShutdown");

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                                PluginType.SHUTDOWN.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any processing that should be done when the Directory
   * Server accepts a new connection from a client.  This method will
   * be called after additional verification is performed to ensure
   * that the connection should be accepted.
   *
   * @param  clientConnection  The client connection that has been
   *                           accepted.
   *
   * @return  The result of the plugin processing.
   */
  public PostConnectPluginResult doPostConnect(ClientConnection
                                                    clientConnection)
  {
    assert debugEnter(CLASS_NAME, "doPostConnect",
                      String.valueOf(clientConnection));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                                PluginType.POST_CONNECT.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any processing that should be done whenever a client
   * connection is closed (regardless of whether the closure is
   * initiated by the client or the server).
   *
   * @param  clientConnection  The client connection that has been
   *                           closed.
   * @param  disconnectReason  The disconnect reason for the closure.
   * @param  messageID         The message ID for an additional
   *                           message for the closure, or a negative
   *                           value if there was no closure message.
   * @param  message           A message providing additional
   *                           information about the closure, or
   *                           <CODE>null</CODE> if there is none.
   *
   * @return  The result of the plugin processing.
   */
  public PostDisconnectPluginResult
              doPostDisconnect(ClientConnection clientConnection,
                               DisconnectReason disconnectReason,
                               int messageID, String message)
  {
    assert debugEnter(CLASS_NAME, "doPostDisconnect",
                      String.valueOf(clientConnection),
                      String.valueOf(disconnectReason),
                      String.valueOf(messageID),
                      String.valueOf(message));

    int    msgID = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String msg   = getMessage(msgID, String.valueOf(pluginDN),
                              PluginType.POST_DISCONNECT.getName());
    throw new UnsupportedOperationException(msg);
  }



  /**
   * Performs any necessary processing that should be done during an
   * LDIF import operation immediately after reading an entry and
   * confirming that it should be imported based on the provided
   * configuration.
   *
   * @param  importConfig  The configuration used for the LDIF import.
   * @param  entry         The entry that has been read to the LDIF
   *                       file.
   *
   * @return  The result of the plugin processing.
   */
  public LDIFPluginResult doLDIFImport(LDIFImportConfig importConfig,
                                       Entry entry)
  {
    assert debugEnter(CLASS_NAME, "doLDIFImport",
                      String.valueOf(importConfig),
                      String.valueOf(entry));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                                PluginType.LDIF_IMPORT.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done during an
   * LDIF export operation immediately after determining that the
   * provided entry should be included in the export.
   *
   * @param  exportConfig  The configuration used for the LDIF export.
   * @param  entry         The entry to be written to the LDIF file.
   *
   * @return  The result of the plugin processing.
   */
  public LDIFPluginResult doLDIFExport(LDIFExportConfig exportConfig,
                                       Entry entry)
  {
    assert debugEnter(CLASS_NAME, "doLDIFExport",
                      String.valueOf(exportConfig),
                      String.valueOf(entry));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                                PluginType.LDIF_EXPORT.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done before the
   * Directory Server parses the elements of an abandon request.
   *
   * @param  abandonOperation  The abandon operation that has been
   *                           requested.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreParsePluginResult
       doPreParse(PreParseAbandonOperation abandonOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreParse",
                      String.valueOf(abandonOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.PRE_PARSE_ABANDON.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done after the
   * Directory Server has completed processing for an abandon
   * operation.
   *
   * @param  abandonOperation  The abandon operation for which
   *                           processing has completed.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PostOperationPluginResult
       doPostOperation(PostOperationAbandonOperation abandonOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostOperation",
                      String.valueOf(abandonOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message =
         getMessage(msgID, String.valueOf(pluginDN),
                    PluginType.POST_OPERATION_ABANDON.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done before the
   * Directory Server parses the elements of an add request.
   *
   * @param  addOperation  The add operation that has been requested.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreParsePluginResult
       doPreParse(PreParseAddOperation addOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreParse",
                      String.valueOf(addOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                                PluginType.PRE_PARSE_ADD.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done just before
   * the Directory Server performs the core processing for an add
   * operation.
   * This method is not called when processing synchronization
   * operations.
   *
   * @param  addOperation  The add operation to be processed.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreOperationPluginResult
       doPreOperation(PreOperationAddOperation addOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreOperation",
                      String.valueOf(addOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.PRE_OPERATION_ADD.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done after the
   * Directory Server has completed the core processing for an add
   * operation but before the response has been sent to the client.
   *
   * @param  addOperation  The add operation for which processing has
   *                       completed but no response has yet been
   *                       sent.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PostOperationPluginResult
       doPostOperation(PostOperationAddOperation addOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostOperation",
                      String.valueOf(addOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.POST_OPERATION_ADD.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done after the
   * Directory Server has completed all processing for an add
   * operation and has sent the response to the client.
   *
   * @param  addOperation  The add operation for which processing has
   *                       completed and the response has been sent to
   *                       the client.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PostResponsePluginResult
       doPostResponse(PostResponseAddOperation addOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostResponse",
                      String.valueOf(addOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.POST_RESPONSE_ADD.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done before the
   * Directory Server parses the elements of a bind request.
   *
   * @param  bindOperation  The bind operation that has been
   *                        requested.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreParsePluginResult
       doPreParse(PreParseBindOperation bindOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreParse",
                      String.valueOf(bindOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                                PluginType.PRE_PARSE_BIND.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done just before
   * the Directory Server performs the core processing for a bind
   * operation.
   *
   * @param  bindOperation  The bind operation to be processed.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreOperationPluginResult
       doPreOperation(PreOperationBindOperation bindOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreOperation",
                      String.valueOf(bindOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.PRE_OPERATION_BIND.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done after the
   * Directory Server has completed the core processing for a bind
   * operation but before the response has been sent to the client.
   *
   * @param  bindOperation  The bind operation for which processing
   *                        has completed but no response has yet been
   *                        sent.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PostOperationPluginResult
       doPostOperation(PostOperationBindOperation bindOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostOperation",
                      String.valueOf(bindOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.POST_OPERATION_BIND.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done after the
   * Directory Server has completed all processing for a bind
   * operation and has sent the response to the client.
   *
   * @param  bindOperation  The bind operation for which processing
   *                        has completed and the response has been
   *                        sent to the client.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PostResponsePluginResult
       doPostResponse(PostResponseBindOperation bindOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostResponse",
                      String.valueOf(bindOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.POST_RESPONSE_BIND.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done before the
   * Directory Server parses the elements of a compare request.
   *
   * @param  compareOperation  The compare operation that has been
   *                           requested.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreParsePluginResult
       doPreParse(PreParseCompareOperation compareOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreParse",
                      String.valueOf(compareOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.PRE_PARSE_COMPARE.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done just before
   * the Directory Server performs the core processing for a compare
   * operation.
   *
   * @param  compareOperation  The compare operation to be processed.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreOperationPluginResult
       doPreOperation(PreOperationCompareOperation compareOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreOperation",
                      String.valueOf(compareOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.PRE_OPERATION_COMPARE.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done after the
   * Directory Server has completed the core processing for a compare
   * operation but before the response has been sent to the client.
   *
   * @param  compareOperation  The compare operation for which
   *                           processing has completed but no
   *                           response has yet been sent.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PostOperationPluginResult
       doPostOperation(PostOperationCompareOperation compareOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostOperation",
                      String.valueOf(compareOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message =
         getMessage(msgID, String.valueOf(pluginDN),
                    PluginType.POST_OPERATION_COMPARE.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done after the
   * Directory Server has completed all processing for a compare
   * operation and has sent the response to the client.
   *
   * @param  compareOperation  The compare operation for which
   *                           processing has completed and the
   *                           response has been sent to the client.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PostResponsePluginResult
       doPostResponse(PostResponseCompareOperation compareOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostResponse",
                      String.valueOf(compareOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.POST_RESPONSE_COMPARE.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done before the
   * Directory Server parses the elements of a delete request.
   *
   * @param  deleteOperation  The delete operation that has been
   *                          requested.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreParsePluginResult
       doPreParse(PreParseDeleteOperation deleteOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreParse",
                      String.valueOf(deleteOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.PRE_PARSE_DELETE.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done just before
   * the Directory Server performs the core processing for a delete
   * operation.
   * This method is not called when processing synchronization
   * operations.
   *
   * @param  deleteOperation  The delete operation to be processed.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreOperationPluginResult
       doPreOperation(PreOperationDeleteOperation deleteOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreOperation",
                      String.valueOf(deleteOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.PRE_OPERATION_DELETE.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done after the
   * Directory Server has completed the core processing for a delete
   * operation but before the response has been sent to the client.
   *
   * @param  deleteOperation  The delete operation for which
   *                          processing has completed but no
   *                          response has yet been sent.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PostOperationPluginResult
       doPostOperation(PostOperationDeleteOperation deleteOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostOperation",
                      String.valueOf(deleteOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.POST_OPERATION_DELETE.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done after the
   * Directory Server has completed all processing for a delete
   * operation and has sent the response to the client.
   *
   * @param  deleteOperation  The delete operation for which
   *                          processing has completed and the
   *                          response has been sent to the client.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PostResponsePluginResult
       doPostResponse(PostResponseDeleteOperation deleteOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostResponse",
                      String.valueOf(deleteOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.POST_RESPONSE_DELETE.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done before the
   * Directory Server parses the elements of an extended request.
   *
   * @param  extendedOperation  The extended operation that has been
   *                            requested.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreParsePluginResult
       doPreParse(PreParseExtendedOperation extendedOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreParse",
                      String.valueOf(extendedOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.PRE_PARSE_EXTENDED.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done just before
   * the Directory Server performs the core processing for an extended
   * operation.
   *
   * @param  extendedOperation  The extended operation to be
   *                            processed.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreOperationPluginResult
       doPreOperation(PreOperationExtendedOperation extendedOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreOperation",
                      String.valueOf(extendedOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message =
         getMessage(msgID, String.valueOf(pluginDN),
                    PluginType.PRE_OPERATION_EXTENDED.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done after the
   * Directory Server has completed the core processing for an
   * extended operation but before the response has been sent to the
   * client.
   *
   * @param  extendedOperation  The extended operation for which
   *                            processing has completed but no
   *                            response has yet been sent.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PostOperationPluginResult
       doPostOperation(PostOperationExtendedOperation
                            extendedOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostOperation",
                      String.valueOf(extendedOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message =
         getMessage(msgID, String.valueOf(pluginDN),
                    PluginType.POST_OPERATION_EXTENDED.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done after the
   * Directory Server has completed all processing for an extended
   * operation and has sent the response to the client.
   *
   * @param  extendedOperation  The extended operation for which
   *                            processing has completed and the
   *                            response has been sent to the client.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PostResponsePluginResult
       doPostResponse(PostResponseExtendedOperation extendedOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostResponse",
                      String.valueOf(extendedOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message =
         getMessage(msgID, String.valueOf(pluginDN),
                    PluginType.POST_RESPONSE_EXTENDED.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done before the
   * Directory Server parses the elements of a modify request.
   *
   * @param  modifyOperation  The modify operation that has been
   *                          requested.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreParsePluginResult
       doPreParse(PreParseModifyOperation modifyOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreParse",
                      String.valueOf(modifyOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.PRE_PARSE_MODIFY.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done just before
   * the Directory Server performs the core processing for a modify
   * operation.
   *
   * This method is not called when processing synchronization
   * operations.
   * @param  modifyOperation  The modify operation to be processed.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreOperationPluginResult
       doPreOperation(PreOperationModifyOperation modifyOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreOperation",
                      String.valueOf(modifyOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.PRE_OPERATION_MODIFY.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done after the
   * Directory Server has completed the core processing for a modify
   * operation but before the response has been sent to the client.
   *
   * @param  modifyOperation  The modify operation for which
   *                          processing has completed but no response
   *                          has yet been sent.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PostOperationPluginResult
       doPostOperation(PostOperationModifyOperation modifyOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostOperation",
                      String.valueOf(modifyOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.POST_OPERATION_MODIFY.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done after the
   * Directory Server has completed all processing for a modify
   * operation and has sent the response to the client.
   *
   * @param  modifyOperation  The modify operation for which
   *                          processing has completed and the
   *                          response has been sent to the client.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PostResponsePluginResult
       doPostResponse(PostResponseModifyOperation modifyOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostResponse",
                      String.valueOf(modifyOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.POST_RESPONSE_MODIFY.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done before the
   * Directory Server parses the elements of a modify DN request.
   *
   * @param  modifyDNOperation  The modify DN operation that has been
   *                            requested.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreParsePluginResult
       doPreParse(PreParseModifyDNOperation modifyDNOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreParse",
                      String.valueOf(modifyDNOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.PRE_PARSE_MODIFY_DN.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done just before
   * the Directory Server performs the core processing for a modify DN
   * operation.
   * This method is not called when processing synchronization
   * operations.
   *
   * @param  modifyDNOperation  The modify DN operation to be
   *                            processed.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreOperationPluginResult
       doPreOperation(PreOperationModifyDNOperation modifyDNOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreOperation",
                      String.valueOf(modifyDNOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message =
         getMessage(msgID, String.valueOf(pluginDN),
                    PluginType.PRE_OPERATION_MODIFY_DN.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done after the
   * Directory Server has completed the core processing for a modify
   * DN operation but before the response has been sent to the client.
   *
   * @param  modifyDNOperation  The modify DN operation for which
   *                            processing has completed but no
   *                            response has yet been sent.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PostOperationPluginResult
       doPostOperation(PostOperationModifyDNOperation
                            modifyDNOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostOperation",
                      String.valueOf(modifyDNOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message =
         getMessage(msgID, String.valueOf(pluginDN),
                    PluginType.POST_OPERATION_MODIFY_DN.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done after the
   * Directory Server has completed all processing for a modify DN
   * operation and has sent the response to the client.
   *
   * @param  modifyDNOperation  The modifyDN operation for which
   *                            processing has completed and the
   *                            response has been sent to the client.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PostResponsePluginResult
       doPostResponse(PostResponseModifyDNOperation modifyDNOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostResponse",
                      String.valueOf(modifyDNOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message =
         getMessage(msgID, String.valueOf(pluginDN),
                    PluginType.POST_RESPONSE_MODIFY_DN.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done before the
   * Directory Server parses the elements of a search request.
   *
   * @param  searchOperation  The search operation that has been
   *                          requested.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreParsePluginResult
       doPreParse(PreParseSearchOperation searchOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreParse",
                      String.valueOf(searchOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.PRE_PARSE_SEARCH.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done just before
   * the Directory Server performs the core processing for a search
   * operation.
   *
   * @param  searchOperation  The search operation to be processed.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreOperationPluginResult
       doPreOperation(PreOperationSearchOperation searchOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreOperation",
                      String.valueOf(searchOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.PRE_OPERATION_SEARCH.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done before a
   * search result entry is sent to a client.  This will be called
   * after it has been verified that the entry does actually match the
   * search criteria and after access control has been enforced to
   * ensure that the entry should be sent and/or to strip out
   * attributes/values that the user should not see.
   *
   * @param  searchOperation  The search operation with which the
   *                          search entry is associated.
   * @param  searchEntry      The search result entry that is to be
   *                          sent to the client.  Its contents may be
   *                          altered by the plugin if necessary.
   *
   * @return  Information about the result of the plugin processing.
   */
  public SearchEntryPluginResult
       processSearchEntry(SearchEntrySearchOperation searchOperation,
                          SearchResultEntry searchEntry)
  {
    assert debugEnter(CLASS_NAME, "processSearchEntry",
                      String.valueOf(searchOperation),
                      String.valueOf(searchEntry));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.SEARCH_RESULT_ENTRY.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done before a
   * search result reference is sent to a client.
   *
   * @param  searchOperation  The search operation with which the
   *                          search result reference is associated.
   * @param  searchReference  The search result reference that is to
   *                          be sent to the client.  Its contents may
   *                          be altered by the plugin if necessary.
   *
   * @return  Information about the result of the plugin processing.
   */
  public SearchReferencePluginResult
       processSearchReference(SearchReferenceSearchOperation
                                   searchOperation,
                              SearchResultReference searchReference)
  {
    assert debugEnter(CLASS_NAME, "processSearchReference",
                      String.valueOf(searchOperation),
                      String.valueOf(searchReference));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message =
         getMessage(msgID, String.valueOf(pluginDN),
                    PluginType.SEARCH_RESULT_REFERENCE.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done after the
   * Directory Server has completed the core processing for a search
   * operation but before the response has been sent to the client.
   *
   * @param  searchOperation  The search operation for which
   *                          processing has completed but no response
   *                          has yet been sent.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PostOperationPluginResult
       doPostOperation(PostOperationSearchOperation searchOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostOperation",
                      String.valueOf(searchOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.POST_OPERATION_SEARCH.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done after the
   * Directory Server has completed all processing for a search
   * operation and has sent the response to the client.
   *
   * @param  searchOperation  The search operation for which
   *                          processing has completed and the
   *                          response has been sent to the client.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PostResponsePluginResult
       doPostResponse(PostResponseSearchOperation searchOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostResponse",
                      String.valueOf(searchOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.POST_RESPONSE_SEARCH.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done before the
   * Directory Server parses the elements of an unbind request.
   *
   * @param  unbindOperation  The unbind operation that has been
   *                          requested.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreParsePluginResult
       doPreParse(PreParseUnbindOperation unbindOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreParse",
                      String.valueOf(unbindOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.PRE_PARSE_UNBIND.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done after the
   * Directory Server has completed processing for an unbind
   * operation.
   *
   * @param  unbindOperation  The unbind operation for which
   *                          processing has completed.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PostOperationPluginResult
       doPostOperation(PostOperationUnbindOperation unbindOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostOperation",
                      String.valueOf(unbindOperation));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.POST_OPERATION_UNBIND.getName());
    throw new UnsupportedOperationException(message);
  }



  /**
   * Performs any necessary processing that should be done before an
   * intermediate response message is sent to a client.
   *
   * @param  intermediateResponse  The intermediate response to be
   *                               sent to the client.
   *
   * @return  Information about the result of the plugin processing.
   */
  public IntermediateResponsePluginResult
              processIntermediateResponse(
                   IntermediateResponse intermediateResponse)
  {
    assert debugEnter(CLASS_NAME, "processIntermediateResponse",
                      String.valueOf(intermediateResponse));

    int    msgID   = MSGID_PLUGIN_TYPE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(pluginDN),
                          PluginType.INTERMEDIATE_RESPONSE.getName());
    throw new UnsupportedOperationException(message);
  }
}

