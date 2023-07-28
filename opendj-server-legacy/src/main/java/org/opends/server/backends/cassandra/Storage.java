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
 * Copyright 2023 3A Systems, LLC.
 */
package org.opends.server.backends.cassandra;


import static org.opends.server.backends.pluggable.spi.StorageUtils.addErrorMessage;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.server.config.server.CASBackendCfg;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadOnlyStorageException;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.SequentialCursor;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.StorageStatus;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.UpdateFunction;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.ServerContext;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.RestoreConfig;
import org.opends.server.util.BackupManager;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class Storage implements org.opends.server.backends.pluggable.spi.Storage, ConfigurationChangeListener<CASBackendCfg>{
	
	//private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

	//private final ServerContext serverContext;
	private CASBackendCfg config;
	  
	public Storage(CASBackendCfg cfg, ServerContext serverContext) {
		//this.serverContext = serverContext;
		this.config = cfg;
	    cfg.addCASChangeListener(this);
	}

	//config
	@Override
	public boolean isConfigurationChangeAcceptable(CASBackendCfg configuration,List<LocalizableMessage> unacceptableReasons) {
		return true;
	}

	@Override
	public ConfigChangeResult applyConfigurationChange(CASBackendCfg cfg) {
		final ConfigChangeResult ccr = new ConfigChangeResult();
	    try
	    {
	    	this.config = cfg;
	    }
	    catch (Exception e)
	    {
	      addErrorMessage(ccr, LocalizableMessage.raw(stackTraceToSingleLineString(e)));
	    }
	    return ccr;
	}

	TransactionImpl tx=null;
	
	@Override
	public void open(AccessMode accessMode) throws Exception {
		tx=new TransactionImpl(config,accessMode);
		storageStatus = StorageStatus.working();
	}

	private StorageStatus storageStatus = StorageStatus.lockedDown(LocalizableMessage.raw("closed"));
	@Override
	public StorageStatus getStorageStatus() {
		return storageStatus;
	}
	
	@Override
	public void close() {
		if (tx!=null) {
			tx.close();
			tx=null;
		}
		storageStatus = StorageStatus.lockedDown(LocalizableMessage.raw("closed"));
	}

	String getKeyspaceName() {
		return "\""+config.getDBDirectory().replaceAll("[^a-zA-z0-9_]", "_")+"\"";
	}
	
	String getTableName() {
		return getKeyspaceName()+".\""+config.getBackendId().replaceAll("[^a-zA-z0-9_]", "_")+"\"";
	}
	
	@Override
	public void removeStorageFiles() throws StorageRuntimeException {	
		final TransactionImpl tx=new TransactionImpl(config,AccessMode.READ_WRITE);
		tx.session.execute(tx.prepared.getUnchecked("DROP TABLE IF EXISTS "+getTableName()+";").bind().setExecutionProfileName(profile));
		tx.close();
	}
	
	//operation
	@Override
	public <T> T read(ReadOperation<T> readOperation) throws Exception {
		return readOperation.run(tx);
	}

	@Override
	public void write(WriteOperation writeOperation) throws Exception {
		writeOperation.run(tx);
	}

	final static String profile="ddl";
	static {
		if (System.getProperty("datastax-java-driver.profiles."+profile+".basic.request.timeout")==null) {
			System.setProperty("datastax-java-driver.profiles."+profile+".basic.request.timeout", "15 seconds");
		}
	}
	private final class TransactionImpl implements ReadableTransaction,WriteableTransaction,Closeable {

		CqlSession session=null;
		
		final LoadingCache<String,PreparedStatement> prepared=CacheBuilder.newBuilder()
				.expireAfterAccess(Duration.ofMinutes(10))
				.maximumSize(4096)
				.build(new CacheLoader<String,PreparedStatement>(){
					@Override
					public PreparedStatement load(String query) throws Exception {
						return session.prepare(query);
					}
				});
		
		final AccessMode accessMode;
		public TransactionImpl(CASBackendCfg config, AccessMode accessMode) {
			super();
			this.accessMode=accessMode;
			session=CqlSession.builder()
					.withApplicationName("OpenDJ "+config.getDBDirectory()+"."+config.getBackendId())
					.withConfigLoader(DriverConfigLoader.fromDefaults(Storage.class.getClassLoader()))
					.build();
			session.execute(prepared.getUnchecked("CREATE KEYSPACE IF NOT EXISTS "+getKeyspaceName()+" WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'};").bind().setExecutionProfileName(profile));
			session.execute(prepared.getUnchecked("USE "+getKeyspaceName()+";").bind().setExecutionProfileName(profile));
		}

		@Override
		public void close() {
			if (session!=null && !session.isClosed()) {
				session.close();
			}
			session=null;
			storageStatus = StorageStatus.lockedDown(LocalizableMessage.raw("closed"));
		}
		
		@Override
		public void openTree(TreeName name, boolean createOnDemand) {
			if (createOnDemand) {
				session.execute(prepared.getUnchecked("CREATE TABLE IF NOT EXISTS "+getTableName()+" (baseDN text,indexId text,key blob,value blob,PRIMARY KEY ((baseDN,indexId),key));").bind().setExecutionProfileName(profile));
			}
		}
		
		public void clearTree(TreeName treeName) {
			checkReadOnly();
			deleteTree(treeName);
		}
		
		@Override
		public ByteString read(TreeName treeName, ByteSequence key) {
			final Row row=session.execute(
					prepared.getUnchecked("SELECT value FROM "+getTableName()+" WHERE baseDN=:baseDN and indexId=:indexId and key=:key").bind()
						.setString("baseDN", treeName.getBaseDN()).setString("indexId", treeName.getIndexId()) 
						.setByteBuffer("key", ByteBuffer.wrap(key.toByteArray())) 
					).one();
			return row==null?null:ByteString.valueOfBytes(row.getByteBuffer("value").array());
		}

		@Override
		public Cursor<ByteString, ByteString> openCursor(TreeName treeName) {
			return new CursorImpl(this,treeName);
		}

		@Override
		public long getRecordCount(TreeName treeName) {
			return session.execute(
					prepared.getUnchecked("SELECT count(*) FROM "+getTableName()+" WHERE baseDN=:baseDN and indexId=:indexId").bind()
						.setString("baseDN", treeName.getBaseDN()).setString("indexId", treeName.getIndexId()) 
					).one().getLong(0);
		}

		@Override
		public void deleteTree(TreeName treeName) {
			checkReadOnly();
			openTree(treeName,true);
			session.execute(
					prepared.getUnchecked("DELETE FROM "+getTableName()+" WHERE baseDN=:baseDN and indexId=:indexId").bind()
						.setString("baseDN", treeName.getBaseDN()).setString("indexId", treeName.getIndexId()) 
					);
		}

		@Override
		public void put(TreeName treeName, ByteSequence key, ByteSequence value) {
			checkReadOnly();
			session.execute(
				prepared.getUnchecked("INSERT INTO "+getTableName()+" (baseDN,indexId,key,value) VALUES (:baseDN,:indexId,:key,:value)").bind()
					.setString("baseDN", treeName.getBaseDN()).setString("indexId", treeName.getIndexId()) 
					.setByteBuffer("key", ByteBuffer.wrap(key.toByteArray()))
					.setByteBuffer("value",ByteBuffer.wrap(value.toByteArray()))
				);
		}

		@Override
		public boolean update(TreeName treeName, ByteSequence key, UpdateFunction f) {
			checkReadOnly();
			final ByteString oldValue=read(treeName,key);
			final ByteSequence newValue=f.computeNewValue(oldValue);
			if (Objects.equals(newValue, oldValue))
	        {
				return false;
	        }
	        if (newValue == null)
	        {
	        	delete(treeName, key);
	        	return true;
	        }
	        put(treeName,key,newValue);
			return true;
		}

		@Override
		public boolean delete(TreeName treeName, ByteSequence key) {
			checkReadOnly();
			session.execute(
					prepared.getUnchecked("DELETE FROM "+getTableName()+" WHERE baseDN=:baseDN and indexId=:indexId and key=:key").bind()
						.setString("baseDN", treeName.getBaseDN()).setString("indexId", treeName.getIndexId()) 
						.setByteBuffer("key", ByteBuffer.wrap(key.toByteArray()))
					);
			return true;
		}
		
		void checkReadOnly() {
			if (AccessMode.READ_ONLY.equals(accessMode)) {
				throw new ReadOnlyStorageException();
			}
		}
	}
	
	private final class CursorImpl implements Cursor<ByteString, ByteString> {
		final TreeName treeName;
		final TransactionImpl tx;

		ResultSet rc;
		Iterator<Row> iterator;
		Row current=null;
		
		public CursorImpl(TransactionImpl tx,TreeName treeName) {
			this.treeName=treeName;
			this.tx=tx;
		}

		ResultSet full(){
			return tx.session.execute(
						tx.prepared.getUnchecked("SELECT key,value FROM "+getTableName()+" WHERE baseDN=:baseDN and indexId=:indexId ORDER BY key").bind()
							.setString("baseDN", treeName.getBaseDN()).setString("indexId", treeName.getIndexId()) 
						);
		}
		
		@Override
		public boolean next() {
			if (iterator==null) {
				rc=full();
				iterator=rc.iterator();
			}
			try {
				current=iterator.next();
				return true;
			}catch (NoSuchElementException e) {
				current=null;
			}
			return false;
		}

		@Override
		public boolean isDefined() {
			return current!=null;
		}

		@Override
		public ByteString getKey() throws NoSuchElementException {
			if (current==null) {
				throw new NoSuchElementException();
			}
			return ByteString.valueOfBytes(current.getByteBuffer("key").array());
		}

		@Override
		public ByteString getValue() throws NoSuchElementException {
			if (current==null) {
				throw new NoSuchElementException();
			}
			return ByteString.valueOfBytes(current.getByteBuffer("value").array());
		}

		@Override
		public void delete() throws NoSuchElementException, UnsupportedOperationException {
			tx.delete(treeName, getKey());
		}

		@Override
		public void close() {
			iterator=null;
			current=null;
		}

		ResultSet full(ByteSequence key){
			return tx.session.execute(
						tx.prepared.getUnchecked("SELECT key,value FROM "+getTableName()+" WHERE baseDN=:baseDN and indexId=:indexId and key>=:key ORDER BY key").bind()
							.setString("baseDN", treeName.getBaseDN()).setString("indexId", treeName.getIndexId()) 
							.setByteBuffer("key", ByteBuffer.wrap(key.toByteArray()))
						);
		}
		@Override
		public boolean positionToKeyOrNext(ByteSequence key) {
			rc=full(key); // start iterator from key key>=:key 
			iterator=rc.iterator();
			if (iterator.hasNext()) {
				current=iterator.next();
				return true;
			}
			current=null;
			return false;
		}
		
		@Override
		public boolean positionToKey(ByteSequence key) {
			if (positionToKeyOrNext(key) && key.equals(getKey())){
				return true;
			}
			current=null;
			return false;
		}

		ResultSet last(){
			return tx.session.execute(
						tx.prepared.getUnchecked("SELECT key,value FROM "+getTableName()+" WHERE baseDN=:baseDN and indexId=:indexId ORDER BY key DESC LIMIT 1").bind()
							.setString("baseDN", treeName.getBaseDN()).setString("indexId", treeName.getIndexId()) 
						);
		}
		
		@Override
		public boolean positionToLastKey() {
			rc=last(); 
			iterator=rc.iterator();
			if (iterator.hasNext()) {
				current=iterator.next();
				return true;
			}
			current=null;
			return false;
		}

		@Override
		public boolean positionToIndex(int index) {
			iterator=rc.iterator(); //reset position
			int ct=0;
			while(iterator.hasNext()){
				current=iterator.next();
				if (ct==index) {
					return true;
				}
				ct++;
		    }
			current=null;
			return false;
		}
	}
	
	@Override
	public Set<TreeName> listTrees() {
		// TODO Auto-generated method stub
		return Collections.emptySet();
	}
	

	private final class ImporterImpl implements Importer {
		final TransactionImpl tx;
		
		public ImporterImpl() {
			tx=new TransactionImpl(config,AccessMode.READ_WRITE);
		}
		
		@Override
		public void close() {
			tx.close();
		}
		
		@Override
		public void clearTree(TreeName name) {
			tx.clearTree(name);
		}
		
		@Override
		public void put(TreeName treeName, ByteSequence key, ByteSequence value) {
			tx.put(treeName, key, value);
		}
		
		@Override
		public ByteString read(TreeName treeName, ByteSequence key) {
			return tx.read(treeName, key);
		}
		
		@Override
		public SequentialCursor<ByteString, ByteString> openCursor(TreeName treeName) {
			return tx.openCursor(treeName);
		}
	}
	
	//import
	@Override
	public Importer startImport() throws ConfigException, StorageRuntimeException {
		return new ImporterImpl();
	}
	
	//backup
	@Override
	public boolean supportsBackupAndRestore() {
		return true;
	}

	@Override
	public void createBackup(BackupConfig backupConfig) throws DirectoryException
	{
		// TODO backup over snapshot or cassandra export
		//new BackupManager(config.getBackendId()).createBackup(this, backupConfig);
	}

	@Override
	public void removeBackup(BackupDirectory backupDirectory, String backupID) throws DirectoryException
	{
		new BackupManager(config.getBackendId()).removeBackup(backupDirectory, backupID);
	}

	@Override
	public void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException
	{
		// TODO restore over snapshot or cassandra export
		//new BackupManager(config.getBackendId()).restoreBackup(this, restoreConfig);
	}

}
