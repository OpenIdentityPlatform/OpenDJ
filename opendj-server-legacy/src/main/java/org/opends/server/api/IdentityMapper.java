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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.api;
import org.forgerock.i18n.LocalizableMessage;



import java.util.List;

import org.forgerock.opendj.server.config.server.IdentityMapperCfg;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;



/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server identity mapper.  An identity
 * mapper is used to identify exactly one user associated with a given
 * identification value.  This API may be used by a number of SASL
 * mechanisms to identify the user that is authenticating to the
 * server.  It may also be used in other areas, like in conjunction
 * with the proxied authorization control.
 *
 * @param  <T>  The type of configuration handled by this identity
 *              mapper.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=true)
public abstract class IdentityMapper
       <T extends IdentityMapperCfg>
{
  /**
   * Initializes this identity mapper based on the information in the
   * provided configuration entry.
   *
   * @param  configuration  The configuration for the identity mapper.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in
   *                           the process of performing the
   *                           initialization.
   *
   * @throws  InitializationException  If a problem occurs during
   *                                   initialization that is not
   *                                   related to the server
   *                                   configuration.
   */
  public abstract void initializeIdentityMapper(T configuration)
         throws ConfigException, InitializationException;



  /**
   * Indicates whether the provided configuration is acceptable for
   * this identity mapper.  It should be possible to call this method
   * on an uninitialized identity mapper instance in order to
   * determine whether the identity mapper would be able to use the
   * provided configuration.
   * <BR><BR>
   * Note that implementations which use a subclass of the provided
   * configuration class will likely need to cast the configuration
   * to the appropriate subclass type.
   *
   * @param  configuration        The identity mapper configuration
   *                              for which to make the determination.
   * @param  unacceptableReasons  A list that may be used to hold the
   *                              reasons that the provided
   *                              configuration is not acceptable.
   *
   * @return  {@code true} if the provided configuration is acceptable
   *          for this identity mapper, or {@code false} if not.
   */
  public boolean isConfigurationAcceptable(
                      IdentityMapperCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // This default implementation does not perform any special
    // validation.  It should be overridden by identity mapper
    // implementations that wish to perform more detailed validation.
    return true;
  }



  /**
   * Performs any finalization that may be necessary for this identity
   * mapper.  By default, no finalization is performed.
   */
  public void finalizeIdentityMapper()
  {
    // No implementation is required by default.
  }



  /**
   * Retrieves the user entry that was mapped to the provided
   * identification string.
   *
   * @param  id  The identification string that is to be mapped to a
   *             user.
   *
   * @return  The user entry that was mapped to the provided
   *          identification, or {@code null} if no users were found
   *          that could be mapped to the provided ID.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to map the given ID to a user entry,
   *                              or if there are multiple user
   *                              entries that could map to the
   *                              provided ID.
   */
  public abstract Entry getEntryForID(String id)
         throws DirectoryException;
}

