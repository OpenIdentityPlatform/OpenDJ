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
import org.opends.server.core.AbandonOperation;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BindOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.InitializationException;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.UnbindOperation;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.IntermediateResponse;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;

import static org.opends.server.loggers.Debug.*;



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
   * @param  directoryServer  The reference to the Directory Server
   *                          instance in which the plugin will be
   *                          running.
   * @param  pluginTypes      The set of plugin types that indicate
   *                          the ways in which this plugin will be
   *                          invoked.
   * @param  configEntry      The entry containing the configuration
   *                          information for this plugin.
   *
   * @throws  ConfigException  If the provided entry does not contain
   *                           a valid configuration for this plugin.
   *
   * @throws  InitializationException  If a problem occurs while
   *                                   initializing the plugin that is
   *                                   not related to the server
   *                                   configuration.
   */
  public abstract void initializePlugin(
                            DirectoryServer directoryServer,
                            Set<PluginType> pluginTypes,
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
  public DN getPluginEntryDN()
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
  public Set<PluginType> getPluginTypes()
  {
    assert debugEnter(CLASS_NAME, "getPluginTypes");

    return pluginTypes;
  }



  /**
   * Performs any processing that should be done when the Directory
   * Server is in the process of starting.  This method will be called
   * after virtually all other initialization has been performed but
   * before other plugins have before the connection handlers are
   * started.
   *
   * @return  The result of the startup plugin processing.
   */
  public StartupPluginResult doStartup()
  {
    assert debugEnter(CLASS_NAME, "doStartup");

    // No implementation is required by default.
    return new StartupPluginResult();
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

    // No implementation is required by default.
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

    // No implementation is required by default.
    return new PostConnectPluginResult();
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

    // No implementation is required by default.
    return new PostDisconnectPluginResult();
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

    // No implementation is required by default.
    return new LDIFPluginResult();
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

    // No implementation is required by default.
    return new LDIFPluginResult();
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
  public PreParsePluginResult doPreParse(AbandonOperation
                                              abandonOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreParse",
                      String.valueOf(abandonOperation));

    // No implementation is required by default.
    return new PreParsePluginResult();
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
              doPostOperation(AbandonOperation abandonOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostOperation",
                      String.valueOf(abandonOperation));

    // No implementation is required by default.
    return new PostOperationPluginResult();
  }



  /**
   * Performs any necessary processing that should be done before the
   * Directory Server parses the elements of an add request.
   *
   * @param  addOperation  The add operation that has been requested.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreParsePluginResult doPreParse(AddOperation addOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreParse",
                      String.valueOf(addOperation));

    // No implementation is required by default.
    return new PreParsePluginResult();
  }



  /**
   * Performs any necessary processing that should be done just before
   * the Directory Server performs the core processing for an add
   * operation.
   *
   * @param  addOperation  The add operation to be processed.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreOperationPluginResult doPreOperation(AddOperation
                                                      addOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreOperation",
                      String.valueOf(addOperation));

    // No implementation is required by default.
    return new PreOperationPluginResult();
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
  public PostOperationPluginResult doPostOperation(AddOperation
                                                        addOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostOperation",
                      String.valueOf(addOperation));

    // No implementation is required by default.
    return new PostOperationPluginResult();
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
  public PostResponsePluginResult doPostResponse(AddOperation
                                                      addOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostResponse",
                      String.valueOf(addOperation));

    // No implementation is required by default.
    return new PostResponsePluginResult();
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
  public PreParsePluginResult doPreParse(BindOperation bindOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreParse",
                      String.valueOf(bindOperation));

    // No implementation is required by default.
    return new PreParsePluginResult();
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
  public PreOperationPluginResult doPreOperation(BindOperation
                                                      bindOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreOperation",
                      String.valueOf(bindOperation));

    // No implementation is required by default.
    return new PreOperationPluginResult();
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
  public PostOperationPluginResult doPostOperation(BindOperation
                                                        bindOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostOperation",
                      String.valueOf(bindOperation));

    // No implementation is required by default.
    return new PostOperationPluginResult();
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
  public PostResponsePluginResult doPostResponse(BindOperation
                                                      bindOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostResponse",
                      String.valueOf(bindOperation));

    // No implementation is required by default.
    return new PostResponsePluginResult();
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
  public PreParsePluginResult doPreParse(CompareOperation
                                              compareOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreParse",
                      String.valueOf(compareOperation));

    // No implementation is required by default.
    return new PreParsePluginResult();
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
              doPreOperation(CompareOperation compareOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreOperation",
                      String.valueOf(compareOperation));

    // No implementation is required by default.
    return new PreOperationPluginResult();
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
              doPostOperation(CompareOperation compareOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostOperation",
                      String.valueOf(compareOperation));

    // No implementation is required by default.
    return new PostOperationPluginResult();
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
              doPostResponse(CompareOperation compareOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostResponse",
                      String.valueOf(compareOperation));

    // No implementation is required by default.
    return new PostResponsePluginResult();
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
  public PreParsePluginResult doPreParse(DeleteOperation
                                              deleteOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreParse",
                      String.valueOf(deleteOperation));

    // No implementation is required by default.
    return new PreParsePluginResult();
  }



  /**
   * Performs any necessary processing that should be done just before
   * the Directory Server performs the core processing for a delete
   * operation.
   *
   * @param  deleteOperation  The delete operation to be processed.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreOperationPluginResult doPreOperation(DeleteOperation
                                                      deleteOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreOperation",
                      String.valueOf(deleteOperation));

    // No implementation is required by default.
    return new PreOperationPluginResult();
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
              doPostOperation(DeleteOperation deleteOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostOperation",
                      String.valueOf(deleteOperation));

    // No implementation is required by default.
    return new PostOperationPluginResult();
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
  public PostResponsePluginResult doPostResponse(DeleteOperation
                                                      deleteOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostResponse",
                      String.valueOf(deleteOperation));

    // No implementation is required by default.
    return new PostResponsePluginResult();
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
  public PreParsePluginResult doPreParse(ExtendedOperation
                                              extendedOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreParse",
                      String.valueOf(extendedOperation));

    // No implementation is required by default.
    return new PreParsePluginResult();
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
              doPreOperation(ExtendedOperation extendedOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreOperation",
                      String.valueOf(extendedOperation));

    // No implementation is required by default.
    return new PreOperationPluginResult();
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
              doPostOperation(ExtendedOperation extendedOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostOperation",
                      String.valueOf(extendedOperation));

    // No implementation is required by default.
    return new PostOperationPluginResult();
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
              doPostResponse(ExtendedOperation extendedOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostResponse",
                      String.valueOf(extendedOperation));

    // No implementation is required by default.
    return new PostResponsePluginResult();
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
  public PreParsePluginResult doPreParse(ModifyOperation
                                              modifyOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreParse",
                      String.valueOf(modifyOperation));

    // No implementation is required by default.
    return new PreParsePluginResult();
  }



  /**
   * Performs any necessary processing that should be done just before
   * the Directory Server performs the core processing for a modify
   * operation.
   *
   * @param  modifyOperation  The modify operation to be processed.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreOperationPluginResult doPreOperation(ModifyOperation
                                                      modifyOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreOperation",
                      String.valueOf(modifyOperation));

    // No implementation is required by default.
    return new PreOperationPluginResult();
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
              doPostOperation(ModifyOperation modifyOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostOperation",
                      String.valueOf(modifyOperation));

    // No implementation is required by default.
    return new PostOperationPluginResult();
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
  public PostResponsePluginResult doPostResponse(ModifyOperation
                                                      modifyOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostResponse",
                      String.valueOf(modifyOperation));

    // No implementation is required by default.
    return new PostResponsePluginResult();
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
  public PreParsePluginResult doPreParse(ModifyDNOperation
                                              modifyDNOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreParse",
                      String.valueOf(modifyDNOperation));

    // No implementation is required by default.
    return new PreParsePluginResult();
  }



  /**
   * Performs any necessary processing that should be done just before
   * the Directory Server performs the core processing for a modify DN
   * operation.
   *
   * @param  modifyDNOperation  The modify DN operation to be
   *                            processed.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreOperationPluginResult
              doPreOperation(ModifyDNOperation modifyDNOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreOperation",
                      String.valueOf(modifyDNOperation));

    // No implementation is required by default.
    return new PreOperationPluginResult();
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
              doPostOperation(ModifyDNOperation modifyDNOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostOperation",
                      String.valueOf(modifyDNOperation));

    // No implementation is required by default.
    return new PostOperationPluginResult();
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
              doPostResponse(ModifyDNOperation modifyDNOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostResponse",
                      String.valueOf(modifyDNOperation));

    // No implementation is required by default.
    return new PostResponsePluginResult();
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
  public PreParsePluginResult doPreParse(SearchOperation
                                              searchOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreParse",
                      String.valueOf(searchOperation));

    // No implementation is required by default.
    return new PreParsePluginResult();
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
  public PreOperationPluginResult doPreOperation(SearchOperation
                                                      searchOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreOperation",
                      String.valueOf(searchOperation));

    // No implementation is required by default.
    return new PreOperationPluginResult();
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
  public SearchEntryPluginResult processSearchEntry(
                                      SearchOperation searchOperation,
                                      SearchResultEntry searchEntry)
  {
    assert debugEnter(CLASS_NAME, "processSearchEntry",
                      String.valueOf(searchOperation),
                      String.valueOf(searchEntry));

    // No implementation is required by default.
    return new SearchEntryPluginResult();
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
              processSearchReference(SearchOperation searchOperation,
                   SearchResultReference searchReference)
  {
    assert debugEnter(CLASS_NAME, "processSearchReference",
                      String.valueOf(searchOperation),
                      String.valueOf(searchReference));

    // No implementation is required by default.
    return new SearchReferencePluginResult();
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
              doPostOperation(SearchOperation searchOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostOperation",
                      String.valueOf(searchOperation));

    // No implementation is required by default.
    return new PostOperationPluginResult();
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
  public PostResponsePluginResult doPostResponse(SearchOperation
                                                      searchOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostResponse",
                      String.valueOf(searchOperation));

    // No implementation is required by default.
    return new PostResponsePluginResult();
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
  public PreParsePluginResult doPreParse(UnbindOperation
                                              unbindOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreParse",
                      String.valueOf(unbindOperation));

    // No implementation is required by default.
    return new PreParsePluginResult();
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
              doPostOperation(UnbindOperation unbindOperation)
  {
    assert debugEnter(CLASS_NAME, "doPostOperation",
                      String.valueOf(unbindOperation));

    // No implementation is required by default.
    return new PostOperationPluginResult();
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

    // No implementation is required by default.
    return new IntermediateResponsePluginResult();
  }
}

