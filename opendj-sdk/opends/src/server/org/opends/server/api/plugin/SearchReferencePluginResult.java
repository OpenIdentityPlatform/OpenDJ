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
 * the result of processing by a search result reference plugin.
 */
public class SearchReferencePluginResult
{
  /**
   * A search reference plugin result instance that indicates all
   * processing was successful.
   */
  public static final SearchReferencePluginResult SUCCESS =
       new SearchReferencePluginResult();



  // Indicates whether any further search result reference plugins
  // should be invoked for this operation.
  private final boolean continuePluginProcessing;

  // Indicates whether processing should continue for the associated
  // search operation.
  private final boolean continueSearch;

  // Indicates whether the search result reference plugin terminated
  // the client connection.
  private final boolean connectionTerminated;

  // Indicates whether the associated reference should be sent to the
  // client.
  private final boolean sendReference;



  /**
   * Creates a new search reference plugin result with the default
   * settings.  In this case, it will indicate that the connection has
   * not been terminated, that further search reference plugin
   * processing should continue, that the reference should be returned
   * to the client, and that processing on the search operation should
   * continue.
   */
  private SearchReferencePluginResult()
  {
    this(false, true, true, true);
  }



  /**
   * Creates a new search reference plugin result with the provided
   * information.
   *
   * @param  connectionTerminated      Indicates whether the search
   *                                   reference plugin terminated the
   *                                   client connection.
   * @param  continuePluginProcessing  Indicates whether any further
   *                                   search reference plugins should
   *                                   be invoked for this operation.
   * @param  sendReference             Indicates whether the reference
   *                                   should be sent to the client.
   * @param  continueSearch            Indicates whether the server
   *                                   should continue processing the
   *                                   search operation.
   */
  public SearchReferencePluginResult(boolean connectionTerminated,
                                     boolean continuePluginProcessing,
                                     boolean sendReference,
                                     boolean continueSearch)
  {
    this.connectionTerminated     = connectionTerminated;
    this.continuePluginProcessing = continuePluginProcessing;
    this.sendReference            = sendReference;
    this.continueSearch           = continueSearch;
  }



  /**
   * Indicates whether the search result reference plugin terminated
   * the client connection.
   *
   * @return  {@code true} if the search result reference plugin
   *          terminated the client connection, or {@code false} if
   *          not.
   */
  public boolean connectionTerminated()
  {
    return connectionTerminated;
  }



  /**
   * Indicates whether any further search result reference plugins
   * should be invoked for this operation.
   *
   * @return  {@code true} if any further search result reference
   *          plugins should be invoked for this operation, or
   *          {@code false} if not.
   */
  public boolean continuePluginProcessing()
  {
    return continuePluginProcessing;
  }



  /**
   * Indicates whether the associated search result reference should
   * be sent to the client.
   *
   * @return  {@code true} if the associated search result reference
   *          should be sent to the client, or {@code false} if not.
   */
  public boolean sendReference()
  {
    return sendReference;
  }



  /**
   * Indicates whether processing should continue for the associated
   * search operation (i.e., if it should continue looking for more
   * matching entries).
   *
   * @return  {@code true} if processing on the search operation
   *          continue, or {@code false} if not.
   */
  public boolean continueSearch()
  {
    return continueSearch;
  }



  /**
   * Retrieves a string representation of this search result reference
   * plugin result.
   *
   * @return  A string representation of this search result reference
   *          plugin result.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this search result reference
   * plugin result to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("SearchReferencePluginResult(" +
                  "connectionTerminated=");
    buffer.append(connectionTerminated);
    buffer.append(", continuePluginProcessing=");
    buffer.append(continuePluginProcessing);
    buffer.append(", sendReference=");
    buffer.append(sendReference);
    buffer.append(", continueSearch=");
    buffer.append(continueSearch);
    buffer.append(")");
  }
}

