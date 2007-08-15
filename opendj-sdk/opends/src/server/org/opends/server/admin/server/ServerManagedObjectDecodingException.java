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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.admin.server;
import org.opends.messages.Message;



import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

import org.opends.server.admin.DecodingException;
import org.opends.server.admin.PropertyException;



/**
 * The requested server managed object was found but one or more of its
 * properties could not be decoded successfully.
 */
public class ServerManagedObjectDecodingException extends DecodingException {

  /**
   * Version ID required by serializable classes.
   */
  private static final long serialVersionUID = 1598401431084729853L;

  // The partially created server managed object.
  private final ServerManagedObject<?> partialManagedObject;

  // The exception(s) that caused this decoding exception.
  private final Collection<PropertyException> causes;



  /**
   * Create a new property decoding exception.
   *
   * @param partialManagedObject
   *          The partially created server managed object containing properties
   *          which were successfully decoded and empty properties for those
   *          which were not (this may include empty mandatory properties).
   * @param causes
   *          The exception(s) that caused this decoding exception.
   */
  public ServerManagedObjectDecodingException(
      ServerManagedObject<?> partialManagedObject,
      Collection<PropertyException> causes) {
    this.partialManagedObject = partialManagedObject;
    this.causes = Collections
        .unmodifiableList(new LinkedList<PropertyException>(causes));
  }



  /**
   * Get an unmodifiable collection view of the causes of this exception.
   *
   * @return Returns an unmodifiable collection view of the causes of this
   *         exception.
   */
  public Collection<PropertyException> getCauses() {
    return causes;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Message getMessageObject() {
    StringBuilder builder = new StringBuilder();
    builder.append("The managed object could not be decoded due"
        + " to the following property exceptions: ");
    // FIXME: better formatting.
    builder.append(causes.toString());
    return Message.raw(builder.toString());  // TODO: i18n?
  }



  /**
   * Get the partially created server managed object containing properties which
   * were successfully decoded and empty properties for those which were not
   * (this may include empty mandatory properties).
   *
   * @return Returns the partially created server managed object containing
   *         properties which were successfully decoded and empty properties for
   *         those which were not (this may include empty mandatory properties).
   */
  public ServerManagedObject<?> getPartialManagedObject() {
    return partialManagedObject;
  }

}
