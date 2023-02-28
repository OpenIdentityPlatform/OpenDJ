package org.opends.server.backends.cassandra;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.server.config.server.CASBackendCfg;
import org.opends.server.backends.pluggable.BackendImpl;
import org.opends.server.core.ServerContext;

public class Backend extends BackendImpl<CASBackendCfg>{
	
	  @Override
	  protected Storage configureStorage(CASBackendCfg cfg, ServerContext serverContext) throws ConfigException
	  {
	    return new Storage(cfg, serverContext);
	  }

}
