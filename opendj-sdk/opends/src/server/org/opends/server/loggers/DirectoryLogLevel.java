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
package org.opends.server.loggers;


import java.util.logging.Level;

import org.opends.server.messages.CoreMessages;



/**
 * This class extends the JDK logging levels with directory server
 * defined levels.
 */
public class DirectoryLogLevel extends Level
{
    private static final long serialVersionUID = 3509000330296517508L;



    /**
     * The log level that will be used for fatal error messages.
     */
    public static final Level FATAL_ERROR = new DirectoryLogLevel(
         "FATAL_ERROR", CoreMessages.MSGID_ERROR_SEVERITY_FATAL_ERROR);



    /**
     * The log level that will be used for debugging messages.
     */
    public static final Level GENERIC_DEBUG = new DirectoryLogLevel(
         "DEBUG", CoreMessages.MSGID_ERROR_SEVERITY_GENERIC_DEBUG);



    /**
     * The log level that will be used for informational messages.
     */
    public static final Level INFORMATIONAL = new DirectoryLogLevel(
         "INFO", CoreMessages.MSGID_ERROR_SEVERITY_INFORMATIONAL);



    /**
     * The log level that will be used for mild error messages.
     */
    public static final Level MILD_ERROR = new DirectoryLogLevel(
         "MILD_ERROR", CoreMessages.MSGID_ERROR_SEVERITY_MILD_ERROR);



    /**
     * The log level that will be used for mild warning messages.
     */
    public static final Level MILD_WARNING = new DirectoryLogLevel(
         "MILD_WARNING", CoreMessages.MSGID_ERROR_SEVERITY_MILD_WARNING);



    /**
     * The log level that will be used for severe error messages.
     */
    public static final Level SEVERE_ERROR = new DirectoryLogLevel(
         "SEVERE_ERROR", CoreMessages.MSGID_ERROR_SEVERITY_SEVERE_ERROR);



    /**
     * The log level that will be used for severe warning messages.
     */
    public static final Level SEVERE_WARNING = new DirectoryLogLevel(
         "SEVERE_WARNING", CoreMessages.MSGID_ERROR_SEVERITY_SEVERE_WARNING);



    /**
     * The log level that will be used for messages related to the Directory
     * Server shutdown process.
     */
    public static final Level SHUTDOWN_DEBUG = new DirectoryLogLevel(
         "SHUTDOWN", CoreMessages.MSGID_ERROR_SEVERITY_SHUTDOWN_DEBUG);



    /**
     * The log level that will be used for messages related to the Directory
     * Server startup process.
     */
    public static final Level STARTUP_DEBUG = new DirectoryLogLevel(
         "STARTUP", CoreMessages.MSGID_ERROR_SEVERITY_STARTUP_DEBUG);


    /**
    * Constructor for the DirectoryLogLevel class.
    *
    * @param  name  The name of the level.
    * @param  value The value of the level.
    */
    public DirectoryLogLevel(String name, int value)
    {
      super(name, value);
    }

}

