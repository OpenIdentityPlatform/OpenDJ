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
 * the result of processing by a post-response plugin.
 */
public class PostResponsePluginResult
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.api.plugin.PostResponsePluginResult";



  // Indicates whether any further post-response plugins should be
  // invoked for this operation.
  private boolean continuePluginProcessing;

  // Indicates whether the post-response plugin terminated the client
  // connection.
  private boolean connectionTerminated;



  /**
   * Creates a new post-response plugin result with the default
   * settings.  In this case, it will indicate that the connection has
   * not been terminated and that further post-response plugin
   * processing should be allowed.
   */
  public PostResponsePluginResult()
  {
    assert debugConstructor(CLASS_NAME);

    this.connectionTerminated     = false;
    this.continuePluginProcessing = true;
  }



  /**
   * Creates a new post-response plugin result with the provided
   * information.
   *
   * @param  connectionTerminated      Indicates whether the
   *                                   post-response plugin terminated
   *                                   the client connection.
   * @param  continuePluginProcessing  Indicates whether any further
   *                                   post-response plugins should be
   *                                   invoked for this operation.
   */
  public PostResponsePluginResult(boolean connectionTerminated,
                                  boolean continuePluginProcessing)
  {
    assert debugConstructor(CLASS_NAME,
                            String.valueOf(connectionTerminated),
                            String.valueOf(continuePluginProcessing));

    this.connectionTerminated     = connectionTerminated;
    this.continuePluginProcessing = continuePluginProcessing;
  }



  /**
   * Indicates whether any further post-response plugins should be
   * invoked for this operation.
   *
   * @return  <CODE>true</CODE> if any further post-response plugins
   *          should be invoked for this operation, or
   *          <CODE>false</CODE> if not.
   */
  public boolean continuePluginProcessing()
  {
    assert debugEnter(CLASS_NAME, "continuePluginProcessing");

    return continuePluginProcessing;
  }



  /**
   * Specifies whether any further post-response plugins should be
   * invoked for this operation.
   *
   * @param  continuePluginProcessing  Specifies whether any further
   *                                   post-response plugins should be
   *                                   invoked for this operation.
   */
  public void setContinuePluginProcessing(
                   boolean continuePluginProcessing)
  {
    assert debugEnter(CLASS_NAME, "setContinuePluginProcessing",
                      String.valueOf(continuePluginProcessing));

    this.continuePluginProcessing = continuePluginProcessing;
  }



  /**
   * Indicates whether the post-response plugin terminated the client
   * connection.
   *
   * @return  <CODE>true</CODE> if the post-response plugin terminated
   *          the client connection, or <CODE>false</CODE> if not.
   */
  public boolean connectionTerminated()
  {
    assert debugEnter(CLASS_NAME, "connectionTerminated");

    return connectionTerminated;
  }



  /**
   * Specifies whether the post-response plugin terminated the client
   * connection.
   *
   * @param  connectionTerminated  Specifies whether the post-response
   *                               plugin terminated the client
   *                               connection.
   */
  public void setConnectionTerminated(boolean connectionTerminated)
  {
    assert debugEnter(CLASS_NAME, "setConnectionTerminated",
                      String.valueOf(connectionTerminated));

    this.connectionTerminated = connectionTerminated;
  }



  /**
   * Retrieves a string representation of this post-response plugin
   * result.
   *
   * @return  A string representation of this post-response plugin
   *          result.
   */
  public String toString()
  {
    assert debugEnter(CLASS_NAME, "toString");

    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this post-response plugin
   * result to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toString(StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toString",
                      "java.lang.StringBuilder");

    buffer.append("PostResponsePluginResult(connectionTerminated=");
    buffer.append(connectionTerminated);
    buffer.append(", continuePluginProcessing=");
    buffer.append(continuePluginProcessing);
    buffer.append(")");
  }
}

