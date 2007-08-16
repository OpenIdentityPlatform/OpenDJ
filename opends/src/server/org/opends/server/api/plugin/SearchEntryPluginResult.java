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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.api.plugin;



/**
 * This class defines a data structure that holds information about
 * the result of processing by a search result entry plugin.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public class SearchEntryPluginResult
{
  /**
   * A search entry plugin result instance that indicates all
   * processing was successful.
   */
  public static final SearchEntryPluginResult SUCCESS =
       new SearchEntryPluginResult();



  // Indicates whether any further search result entry plugins should
  // be invoked for this operation.
  private final boolean continuePluginProcessing;

  // Indicates whether processing should continue for the associated
  // search operation.
  private final boolean continueSearch;

  // Indicates whether the search result entry plugin terminated the
  // client connection.
  private final boolean connectionTerminated;

  // Indicates whether the associated entry should be sent to the
  // client.
  private final boolean sendEntry;



  /**
   * Creates a new search entry plugin result with the default
   * settings.  In this case, it will indicate that the connection has
   * not been terminated, that further search entry plugin processing
   * should continue, that the entry should be returned to the client,
   * and that processing on the search operation should continue.
   */
  private SearchEntryPluginResult()
  {
    this(false, true, true, true);
  }



  /**
   * Creates a new search entry plugin result with the provided
   * information.
   *
   * @param  connectionTerminated      Indicates whether the search
   *                                   entry plugin terminated the
   *                                   client connection.
   * @param  continuePluginProcessing  Indicates whether any further
   *                                   search entry plugins should be
   *                                   invoked for this operation.
   * @param  sendEntry                 Indicates whether the entry
   *                                   should be sent to the client.
   * @param  continueSearch            Indicates whether the server
   *                                   should continue processing the
   *                                   search operation.
   */
  public SearchEntryPluginResult(boolean connectionTerminated,
                                 boolean continuePluginProcessing,
                                 boolean sendEntry,
                                 boolean continueSearch)
  {
    this.connectionTerminated     = connectionTerminated;
    this.continuePluginProcessing = continuePluginProcessing;
    this.sendEntry                = sendEntry;
    this.continueSearch           = continueSearch;
  }



  /**
   * Indicates whether the search result entry plugin terminated the
   * client connection.
   *
   * @return  {@code true} if the search result entry plugin
   *          terminated the client connection, or {@code false} if
   *          not.
   */
  public boolean connectionTerminated()
  {
    return connectionTerminated;
  }



  /**
   * Indicates whether any further search result entry plugins should
   * be invoked for this operation.
   *
   * @return  {@code true} if any further search result entry plugins
   *          should be invoked for this operation, or {@code false}
   *          if not.
   */
  public boolean continuePluginProcessing()
  {
    return continuePluginProcessing;
  }



  /**
   * Indicates whether the associated search result entry should be
   * sent to the client.
   *
   * @return  {@code true} if the associated search result entry
   *          should be sent to the client, or {@code false} if not.
   */
  public boolean sendEntry()
  {
    return sendEntry;
  }



  /**
   * Indicates whether processing should continue for the associated
   * search operation (i.e., if it should continue looking for more
   * matching entries).
   *
   * @return  {@code true} if processing on the search operation
   *          should continue, or {@code false} if not.
   */
  public boolean continueSearch()
  {
    return continueSearch;
  }



  /**
   * Retrieves a string representation of this search result entry
   * plugin result.
   *
   * @return  A string representation of this search result entry
   *          plugin result.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this search result entry
   * plugin result to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("SearchEntryPluginResult(connectionTerminated=");
    buffer.append(connectionTerminated);
    buffer.append(", continuePluginProcessing=");
    buffer.append(continuePluginProcessing);
    buffer.append(", sendEntry=");
    buffer.append(sendEntry);
    buffer.append(", continueSearch=");
    buffer.append(continueSearch);
    buffer.append(")");
  }
}

