/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014 ForgeRock AS.
 */
package org.opends.server.config;
import org.forgerock.i18n.LocalizableMessage;



import org.opends.server.types.IdentifiedException;



/**
 * This class defines an exception that may be thrown during the course of
 * interactions with the Directory Server configuration.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class ConfigException
       extends IdentifiedException
{
  /**
   * The serial version identifier required to satisfy the compiler because this
   * class extends <CODE>java.lang.Exception</CODE>, which implements the
   * <CODE>java.io.Serializable</CODE> interface.  This value was generated
   * using the <CODE>serialver</CODE> command-line utility included with the
   * Java SDK.
   */
  private static final long serialVersionUID = 3135563348838654570L;





  /**
   * Creates a new configuration exception with the provided message.
   *
   * @param  message    The message to use for this configuration exception.
   */
  public ConfigException(LocalizableMessage message)
  {
    super(message);
  }



  /**
   * Creates a new configuration exception with the provided message and
   * underlying cause.
   *
   * @param  message    The message to use for this configuration exception.
   * @param  cause      The underlying cause that triggered this configuration
   *                    exception.
   */
  public ConfigException(LocalizableMessage message, Throwable cause)
  {
    super(message, cause);
  }



}

