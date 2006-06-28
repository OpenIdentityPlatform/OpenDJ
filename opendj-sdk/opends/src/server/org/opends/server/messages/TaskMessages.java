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
package org.opends.server.messages;



import static org.opends.server.messages.MessageHandler.*;



/**
 * This class defines the set of message IDs and default format strings for
 * messages associated with the tools.
 */
public class TaskMessages
{
  /**
   * The message ID of the message that will be used if a backend could not be
   * enabled.
   */
  public static final int MSGID_TASK_CANNOT_ENABLE_BACKEND =
       CATEGORY_MASK_TASK | SEVERITY_MASK_SEVERE_ERROR | 1;



  /**
   * The message ID of the message that will be used if a backend could not be
   * disabled.
   */
  public static final int MSGID_TASK_CANNOT_DISABLE_BACKEND =
       CATEGORY_MASK_TASK | SEVERITY_MASK_SEVERE_ERROR | 2;



  /**
   * The message ID for the shutdown message that will be used if the Directory
   * Server shutdown process is initiated by a task that does not include its
   * own shutdown message.
   */
  public static final int MSGID_TASK_SHUTDOWN_DEFAULT_MESSAGE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 3;



  /**
   * The message ID for the shutdown message that will be used if the Directory
   * Server shutdown process is initiated by a task that contains a custom
   * shutdown message.
   */
  public static final int MSGID_TASK_SHUTDOWN_CUSTOM_MESSAGE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 4;



  /**
   * Associates a set of generic messages with the message IDs defined in this
   * class.
   */
  public static void registerMessages()
  {
    registerMessage(MSGID_TASK_CANNOT_ENABLE_BACKEND,
                    "The task could not enable a backend: %s");
    registerMessage(MSGID_TASK_CANNOT_DISABLE_BACKEND,
                    "The task could not disable a backend: %s");


    registerMessage(MSGID_TASK_SHUTDOWN_DEFAULT_MESSAGE,
                    "The Directory Server shutdown process has been " +
                    "initiated by task %s.");
    registerMessage(MSGID_TASK_SHUTDOWN_CUSTOM_MESSAGE,
                    "The Directory Server shutdown process has been " +
                    "initiated by task %s:  %s");
  }
}
