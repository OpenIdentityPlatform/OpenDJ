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
 * the result of processing by a post-connect plugin.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public class PostConnectPluginResult
{
  /**
   * A post-connect plugin result instance that indicates all
   * processing was  successful.
   */
  public static final PostConnectPluginResult SUCCESS =
       new PostConnectPluginResult();



  // Indicates whether any further post-connect plugins should be
  // invoked for this connection.
  private final boolean continuePluginProcessing;

  // Indicates whether the post-connect plugin terminated the client
  // connection.
  private final boolean connectionTerminated;



  /**
   * Creates a new post-connect plugin result with the default
   * settings.  In this case, it will indicate that the connection has
   * not been terminated and that further post-connect plugin
   * processing should be allowed.
   */
  private PostConnectPluginResult()
  {
    this(false, true);
  }



  /**
   * Creates a new post-connect plugin result with the provided
   * information.
   *
   * @param  connectionTerminated      Indicates whether the
   *                                   post-connect plugin terminated
   *                                   the client connection.
   * @param  continuePluginProcessing  Indicates whether any further
   *                                   post-connect plugins should be
   *                                   invoked for this connection.
   */
  public PostConnectPluginResult(boolean connectionTerminated,
                                 boolean continuePluginProcessing)
  {
    this.connectionTerminated     = connectionTerminated;
    this.continuePluginProcessing = continuePluginProcessing;
  }



  /**
   * Indicates whether the post-connect plugin terminated the client
   * connection.
   *
   * @return  {@code true} if the post-connect plugin terminated the
   *          client connection, or {@code false} if not.
   */
  public boolean connectionTerminated()
  {
    return connectionTerminated;
  }



  /**
   * Indicates whether any further post-connect plugins should be
   * invoked for this connection.
   *
   * @return  {@code true} if any further post-connect plugins should
   *          be invoked for this connection, or {@code false} if not.
   */
  public boolean continuePluginProcessing()
  {
    return continuePluginProcessing;
  }



  /**
   * Retrieves a string representation of this post-connect plugin
   * result.
   *
   * @return  A string representation of this post-connect plugin
   *          result.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this post-connect plugin
   * result to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("PostConnectPluginResult(connectionTerminated=");
    buffer.append(connectionTerminated);
    buffer.append(", continuePluginProcessing=");
    buffer.append(continuePluginProcessing);
    buffer.append(")");
  }
}

