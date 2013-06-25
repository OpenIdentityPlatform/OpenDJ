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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.datamodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.types.OpenDsException;

/**
 * The exception that occurs when the user is editing an entry and some of the
 * provided data is not valid.
 *
 */
public class CheckEntrySyntaxException extends OpenDsException
{
  private static final long serialVersionUID = 8145911071581212822L;
  private List<Message> errors;
  /**
   * Constructor of the exception.
   * @param errors the list of error description that were found.
   */
  public CheckEntrySyntaxException(List<Message> errors)
  {
    super(getMessage(errors));
    this.errors = Collections.unmodifiableList(errors);
  }

  /**
   * Returns the list of errors that were encountered.
   * @return the list of errors that were encountered.
   */
  public List<Message> getErrors()
  {
    return errors;
  }

  /**
   * Returns a single message using the provided messages.  This method assumes
   * that the messages have HTML format.
   * @param errors the list of errors.
   * @return a single message using the provided messages.
   */
  private static Message getMessage(List<Message> errors)
  {
    ArrayList<String> s = new ArrayList<String>();
    for (Message error : errors)
    {
      s.add(error.toString());
    }
    return Message.raw(Utilities.getStringFromCollection(s, "<br>"));
  }
}
