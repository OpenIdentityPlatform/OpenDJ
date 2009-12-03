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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk.schema;



import com.sun.opends.sdk.util.Message;
import org.opends.sdk.util.LocalizedIllegalArgumentException;



/**
 * Thrown when a schema query fails because the requested schema element
 * could not be found or is ambiguous.
 */
@SuppressWarnings("serial")
public class UnknownSchemaElementException extends
    LocalizedIllegalArgumentException
{
  /**
   * Creates a new unknown schema element exception with the provided
   * message.
   * 
   * @param message
   *          The message that explains the problem that occurred.
   */
  public UnknownSchemaElementException(Message message)
  {
    super(message);
  }
}
