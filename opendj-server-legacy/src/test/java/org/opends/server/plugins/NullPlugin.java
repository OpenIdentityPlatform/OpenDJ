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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.plugins;



import java.util.Set;

import org.forgerock.opendj.server.config.server.PluginCfg;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginType;



/**
 * This class defines a Directory Server plugin that doesn't do anything.  It
 * just passes through all non-abstract methods to the superclass
 * implementation (which will throw exceptions for all plugin operations).
 */
public class NullPlugin
       extends DirectoryServerPlugin<PluginCfg>
{
  /**
   * Creates a new instance of this Directory Server plugin.  Every
   * plugin must implement a default constructor (it is the only one
   * that will be used to create plugins defined in the
   * configuration), and every plugin constructor must call
   * <CODE>super()</CODE> as its first element.
   */
  public NullPlugin()
  {
    super();
  }



  /** {@inheritDoc} */
  @Override
  public void initializePlugin(Set<PluginType> pluginTypes,
                               PluginCfg configuration)
  {
    // No implementation required.
  }
}

