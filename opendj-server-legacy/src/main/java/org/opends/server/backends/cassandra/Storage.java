package org.opends.server.backends.cassandra;

import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.CASBackendCfg;
import org.forgerock.opendj.server.config.server.JEBackendCfg;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.StorageStatus;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.RestoreConfig;

public class Storage implements org.opends.server.backends.pluggable.spi.Storage, ConfigurationChangeListener<JEBackendCfg>{
	
	private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

	public Storage(CASBackendCfg cfg, ServerContext serverContext) {
		// TODO Auto-generated constructor stub
	}

	//config
	@Override
	public boolean isConfigurationChangeAcceptable(JEBackendCfg configuration,List<LocalizableMessage> unacceptableReasons) {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public ConfigChangeResult applyConfigurationChange(JEBackendCfg configuration) {
		// TODO Auto-generated method stub
		return null;
	}

	
	//status
	@Override
	public void open(AccessMode accessMode) throws Exception {
		storageStatus = StorageStatus.working();
		
	}

	private StorageStatus storageStatus = StorageStatus.lockedDown(LocalizableMessage.raw("closed"));
	@Override
	public StorageStatus getStorageStatus() {
		return storageStatus;
	}
	
	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void removeStorageFiles() throws StorageRuntimeException {
		// TODO Auto-generated method stub
		
	}
	
	//operation
	@Override
	public <T> T read(ReadOperation<T> readOperation) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void write(WriteOperation writeOperation) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Set<TreeName> listTrees() {
		// TODO Auto-generated method stub
		return null;
	}
	

	//import
	@Override
	public Importer startImport() throws ConfigException, StorageRuntimeException {
		// TODO Auto-generated method stub
		return null;
	}
	
	//backup
	@Override
	public boolean supportsBackupAndRestore() {
		return false;
	}

	@Override
	public void createBackup(BackupConfig backupConfig) throws DirectoryException {
	}

	@Override
	public void removeBackup(BackupDirectory backupDirectory, String backupID) throws DirectoryException {
	}

	@Override
	public void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException {
	}
}
