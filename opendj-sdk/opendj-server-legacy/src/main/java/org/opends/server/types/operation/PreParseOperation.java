/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */
package org.opends.server.types.operation;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.opends.server.types.AdditionalLogItem;
import org.opends.server.types.Control;

/**
 * This class defines a set of methods that are available for use by
 * pre-parse plugins for all types of operations.  Note that this
 * interface is intended only to define an API for use by plugins and
 * is not intended to be implemented by any custom classes.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public interface PreParseOperation
       extends PluginOperation
{
  /**
   * Adds the provided control to the set of request controls for this
   * operation.
   *
   * @param  control  The control to add to the set of request
   *                  controls for this operation.
   */
  void addRequestControl(Control control);



  /**
   * Adds the provided control to the set of controls to include in
   * the response to the client.
   *
   * @param  control  The control to add to the set of controls to
   *                  include in the response to the client.
   */
  void addResponseControl(Control control);



  /**
   * Removes the provided control from the set of controls to include
   * in the response to the client.
   *
   * @param  control  The control to remove from the set of controls
   *                  to include in the response to the client.
   */
  void removeResponseControl(Control control);



  /**
   * Retrieves the error message for this operation.  Its contents may
   * be altered by the caller.
   *
   * @return  The error message for this operation.
   */
  LocalizableMessageBuilder getErrorMessage();



  /**
   * Specifies the error message for this operation.
   *
   * @param  errorMessage  The error message for this operation.
   */
  void setErrorMessage(LocalizableMessageBuilder errorMessage);



  /**
   * Appends the provided message to the error message buffer.  If the
   * buffer has not yet been created, then this will create it first
   * and then add the provided message.
   *
   * @param  message  The message to append to the error message
   *                  buffer.
   */
  void appendErrorMessage(LocalizableMessage message);



  /**
   * Returns an unmodifiable list containing the additional log items for this
   * operation, which should be written to the log but not included in the
   * response to the client.
   *
   * @return An unmodifiable list containing the additional log items for this
   *         operation.
   */
  List<AdditionalLogItem> getAdditionalLogItems();



  /**
   * Adds an additional log item to this operation, which should be written to
   * the log but not included in the response to the client. This method may not
   * be called by post-response plugins.
   *
   * @param item
   *          The additional log item for this operation.
   */
  void addAdditionalLogItem(AdditionalLogItem item);
}

