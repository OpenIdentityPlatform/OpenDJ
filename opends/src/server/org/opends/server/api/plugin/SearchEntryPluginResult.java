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



import static org.opends.server.loggers.Debug.*;



/**
 * This class defines a data structure that holds information about
 * the result of processing by a search result entry plugin.
 */
public class SearchEntryPluginResult
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.api.plugin.SearchEntryPluginResult";



  // Indicates whether any further search result entry plugins should
  // be invoked for this operation.
  private boolean continuePluginProcessing;

  // Indicates whether processing should continue for the associated
  // search operation.
  private boolean continueSearch;

  // Indicates whether the search result entry plugin terminated the
  // client connection.
  private boolean connectionTerminated;

  // Indicates whether the associated entry should be sent to the
  // client.
  private boolean sendEntry;



  /**
   * Creates a new search entry plugin result with the default
   * settings.  In this case, it will indicate that the connection has
   * not been terminated, that further search entry plugin processing
   * should continue, that the entry should be returned to the client,
   * and that processing on the search operation should continue.
   */
  public SearchEntryPluginResult()
  {
    assert debugConstructor(CLASS_NAME);

    this.connectionTerminated     = false;
    this.continuePluginProcessing = true;
    this.sendEntry                = true;
    this.continueSearch           = true;
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
    assert debugConstructor(CLASS_NAME,
                            String.valueOf(connectionTerminated),
                            String.valueOf(continuePluginProcessing),
                            String.valueOf(sendEntry),
                            String.valueOf(continueSearch));

    this.connectionTerminated     = connectionTerminated;
    this.continuePluginProcessing = continuePluginProcessing;
    this.sendEntry                = sendEntry;
    this.continueSearch           = continueSearch;
  }



  /**
   * Indicates whether the search result entry plugin terminated the
   * client connection.
   *
   * @return  <CODE>true</CODE> if the search result entry plugin
   *          terminated the client connection, or <CODE>false</CODE>
   *          if not.
   */
  public boolean connectionTerminated()
  {
    assert debugEnter(CLASS_NAME, "connectionTerminated");

    return connectionTerminated;
  }



  /**
   * Specifies whether the search result entry plugin terminated the
   * client connection.
   *
   * @param  connectionTerminated  Specifies whether the search result
   *                               entry plugin terminated the client
   *                               connection.
   */
  public void setConnectionTerminated(boolean connectionTerminated)
  {
    assert debugEnter(CLASS_NAME, "setConnectionTerminated",
                      String.valueOf(connectionTerminated));

    this.connectionTerminated = connectionTerminated;
  }



  /**
   * Indicates whether any further search result entry plugins should
   * be invoked for this operation.
   *
   * @return  <CODE>true</CODE> if any further search result entry
   *          plugins should be invoked for this operation, or
   *          <CODE>false</CODE> if not.
   */
  public boolean continuePluginProcessing()
  {
    assert debugEnter(CLASS_NAME, "continuePluginProcessing");

    return continuePluginProcessing;
  }



  /**
   * Specifies whether any further search result entry plugins should
   * be invoked for this operation.
   *
   * @param  continuePluginProcessing  Specifies whether any further
   *                                   search result entry plugins
   *                                   should be invoked for this
   *                                   operation.
   */
  public void setContinuePluginProcessing(
                   boolean continuePluginProcessing)
  {
    assert debugEnter(CLASS_NAME, "setContinuePluginProcessing",
                      String.valueOf(continuePluginProcessing));

    this.continuePluginProcessing = continuePluginProcessing;
  }



  /**
   * Indicates whether the associated search result entry should be
   * sent to the client.
   *
   * @return  <CODE>true</CODE> if the associated search result entry
   *          should be sent to the client, or <CODE>false</CODE> if
   *          not.
   */
  public boolean sendEntry()
  {
    assert debugEnter(CLASS_NAME, "sendEntry");

    return sendEntry;
  }



  /**
   * Specifies whether the associated search result entry should be
   * sent to the client.
   *
   * @param  sendEntry  Specifies whether the associated search result
   *                    entry should be sent to the client.
   */
  public void setSendEntry(boolean sendEntry)
  {
    assert debugEnter(CLASS_NAME, "setSendEntry",
                      String.valueOf(sendEntry));

    this.sendEntry = sendEntry;
  }



  /**
   * Indicates whether processing should continue for the associated
   * search operation (i.e., if it should continue looking for more
   * matching entries).
   *
   * @return  <CODE>true</CODE> if processing on the search operation
   *          should continue, or <CODE>false</CODE> if not.
   */
  public boolean continueSearch()
  {
    assert debugEnter(CLASS_NAME, "continueSearch");

    return continueSearch;
  }



  /**
   * Specifies whether processing should continue for the associated
   * search operation.
   *
   * @param  continueSearch  Specifies whether processing should
   *                         continue for the associated search
   *                         operation.
   */
  public void setContinueSearch(boolean continueSearch)
  {
    assert debugEnter(CLASS_NAME, "setContinueSearch",
                      String.valueOf(continueSearch));

    this.continueSearch = continueSearch;
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
    assert debugEnter(CLASS_NAME, "toString");

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
    assert debugEnter(CLASS_NAME, "toString",
                      "java.lang.StringBuilder");

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

