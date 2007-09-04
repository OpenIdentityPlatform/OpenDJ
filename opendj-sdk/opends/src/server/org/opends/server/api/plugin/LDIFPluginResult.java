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



import org.opends.messages.Message;



/**
 * This class defines a data structure that holds information about
 * the result of processing an LDIF import or export plugin.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public class LDIFPluginResult
{
  /**
   * An LDIF plugin result instance that indicates all processing was
   * successful.
   */
  public static final LDIFPluginResult SUCCESS =
       new LDIFPluginResult();



  // Indicates whether any further LDIF import/export plugins should
  // be invoked for the associated entry.
  private final boolean continuePluginProcessing;

  // Indicates whether the associated entry should still be
  // imported/exported.
  private final boolean continueEntryProcessing;

  // A message explaining why the entry was rejected.
  private final Message rejectMessage;



  /**
   * Creates a new LDIF plugin result with the default settings.  In
   * this case, it will indicate that all processing should continue
   * as normal.
   */
  private LDIFPluginResult()
  {
    this(true, true, null);
  }



  /**
   * Creates a new pre-operation plugin result with the provided
   * information.
   *
   * @param  continuePluginProcessing  Indicates whether any further
   *                                   LDIF import/export plugins
   *                                   should be invoked for the
   *                                   associated entry.
   * @param  continueEntryProcessing   Indicates whether the
   *                                   associated entry should still
   *                                   be imported/exported.
   */
  public LDIFPluginResult(boolean continuePluginProcessing,
                          boolean continueEntryProcessing)
  {
    this(continuePluginProcessing, continueEntryProcessing, null);
  }



  /**
   * Creates a new pre-operation plugin result with the provided
   * information.
   *
   * @param  continuePluginProcessing  Indicates whether any further
   *                                   LDIF import/export plugins
   *                                   should be invoked for the
   *                                   associated entry.
   * @param  continueEntryProcessing   Indicates whether the
   *                                   associated entry should still
   *                                   be imported/exported.
   * @param  rejectMessage             A message explaining why the
   *                                   entry should not be
   *                                   imported/exported.
   */
  public LDIFPluginResult(boolean continuePluginProcessing,
                          boolean continueEntryProcessing,
                          Message rejectMessage)
  {
    this.continuePluginProcessing = continuePluginProcessing;
    this.continueEntryProcessing  = continueEntryProcessing;
    this.rejectMessage            = rejectMessage;
  }



  /**
   * Indicates whether any further LDIF import/export plugins should
   * be invoked for the associated entry.
   *
   * @return  {@code true} if any further LDIF import/export plugins
   *          should be invoked for the associated entry, or
   *          {@code false} if not.
   */
  public boolean continuePluginProcessing()
  {
    return continuePluginProcessing;
  }



  /**
   * Indicates whether the associated entry should still be
   * imported/exported.
   *
   * @return  {@code true} if the associated entry should still be
   *          imported/exported, or {@code false} if not.
   */
  public boolean continueEntryProcessing()
  {
    return continueEntryProcessing;
  }



  /**
   * Retrieves a message explaining why the entry should not be
   * imported/exported, if one was provided.
   *
   * @return  A message explaining why the entry should not be
   *          imported/exported, or {@code null} if none was provided.
   */
  public Message getRejectMessage()
  {
    return rejectMessage;
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
    buffer.append("LDIFPluginResult(continuePluginProcessing=");
    buffer.append(continuePluginProcessing);
    buffer.append(", continueEntryProcessing=");
    buffer.append(continueEntryProcessing);

    if (rejectMessage != null)
    {
      buffer.append(", rejectMessage=\"");
      buffer.append(rejectMessage);
      buffer.append("\"");
    }

    buffer.append(")");
  }
}

