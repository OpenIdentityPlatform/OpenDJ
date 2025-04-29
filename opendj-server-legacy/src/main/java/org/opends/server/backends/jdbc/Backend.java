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
 * Copyright 2024 3A Systems, LLC.
 */
package org.opends.server.backends.jdbc;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.server.config.server.JDBCBackendCfg;
import org.opends.server.backends.pluggable.BackendImpl;
import org.opends.server.core.ServerContext;

public class Backend extends BackendImpl<JDBCBackendCfg>{
	
	  @Override
	  protected Storage configureStorage(JDBCBackendCfg cfg, ServerContext serverContext) throws ConfigException
	  {
	    return new Storage(cfg, serverContext);
	  }

}
