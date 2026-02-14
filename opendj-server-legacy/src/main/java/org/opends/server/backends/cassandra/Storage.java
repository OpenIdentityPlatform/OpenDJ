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
 * Copyright 2023-2024 3A Systems, LLC.
 */
package org.opends.server.backends.cassandra;

import static org.opends.server.backends.pluggable.spi.StorageUtils.addErrorMessage;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
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
import com.datastax.oss.driver.api.core.cql.Statement;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

public class Storage implements org.opends.server.backends.pluggable.spi.Storage, ConfigurationChangeListener<CASBackendCfg>{
	
	private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

	private CASBackendCfg config;
	
	public Storage(CASBackendCfg cfg, ServerContext serverContext) {
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

	CqlSession session=null;

	final LoadingCache<String,PreparedStatement> prepared = Caffeine.newBuilder()
		.expireAfterAccess(Duration.ofMinutes(10))
		.maximumSize(4096)
		.build(query -> session.prepare(query));

	ResultSet execute(Statement<?> statement) {
		if (logger.isTraceEnabled()) {
			final ResultSet res=session.execute(statement.setTracing(true));
			logger.trace(LocalizableMessage.raw(
					"cassandra: %s"
					,res.getExecutionInfo().getQueryTrace().getParameters()
				)
			);
			return res;
		}
		return session.execute(statement);
	}
	
	AccessMode accessMode=null;
	@Override
	public void open(AccessMode accessMode) throws Exception {
		this.accessMode=accessMode;
		session=CqlSession.builder()
			.withApplicationName("OpenDJ "+getKeyspaceName()+"."+config.getBackendId())
			.withConfigLoader(DriverConfigLoader.fromDefaults(Storage.class.getClassLoader()))
			.build();
		if (AccessMode.READ_WRITE.equals(accessMode)) {
			execute(prepared.get("CREATE KEYSPACE IF NOT EXISTS "+getKeyspaceName()+" WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'};").bind().setExecutionProfileName(profile));
		}
		storageStatus = StorageStatus.working();
	}

	private StorageStatus storageStatus = StorageStatus.lockedDown(LocalizableMessage.raw("closed"));
	@Override
	public StorageStatus getStorageStatus() {
		return storageStatus;
	}
	
	@Override
	public void close() {
		storageStatus = StorageStatus.lockedDown(LocalizableMessage.raw("closed"));
		if (session!=null && !session.isClosed()) {
			session.close();
		}
		session=null;
	}

	String getKeyspaceName() {
		return "\""+System.getProperty("keyspace",config.getDBDirectory()).replaceAll("[^a-zA-z0-9_]", "_")+"\"";
	}
	
	String getTableName() {
		return getKeyspaceName()+".\""+config.getBackendId().replaceAll("[^a-zA-z0-9_]", "_")+"\"";
	}
	
	@Override
	public void removeStorageFiles() throws StorageRuntimeException {
		final boolean isOpen=getStorageStatus().isWorking();
		if (!isOpen) {
			try {
				open(AccessMode.READ_WRITE);
			}catch (Exception e) {
				throw new StorageRuntimeException(e);
			}
		}
		try {
			execute(prepared.get("TRUNCATE TABLE "+getTableName()+";").bind().setExecutionProfileName(profile));
		}catch (Throwable e) {}
		if (!isOpen) {
			close();
		}
	}
	
	//operation
	@Override
	public <T> T read(ReadOperation<T> readOperation) throws Exception {
		return readOperation.run(new TransactionImpl(AccessMode.READ_ONLY));
	}

	@Override
	public void write(WriteOperation writeOperation) throws Exception {
		writeOperation.run(new TransactionImpl(accessMode));
	}

	final static String profile="ddl";
	static {
		if (System.getProperty("datastax-java-driver.basic.request.timeout")==null) {
			System.setProperty("datastax-java-driver.basic.request.timeout", "10 seconds");
		}
		if (System.getProperty("datastax-java-driver.profiles."+profile+".basic.request.timeout")==null) {
			System.setProperty("datastax-java-driver.profiles."+profile+".basic.request.timeout", "30 seconds");
		}
	}
	private final class TransactionImpl implements ReadableTransaction,WriteableTransaction {
		
		final AccessMode accessMode;
		public TransactionImpl(AccessMode accessMode) {
			super();
			this.accessMode=accessMode;
		}

		@Override
		public void openTree(TreeName name, boolean createOnDemand) {
			if (createOnDemand) {
				execute(prepared.get("CREATE TABLE IF NOT EXISTS "+getTableName()+" (baseDN text,indexId text,key blob,value blob,PRIMARY KEY ((baseDN,indexId),key));").bind().setExecutionProfileName(profile));
			}
		}
		
		public void clearTree(TreeName treeName) {
			checkReadOnly();
			deleteTree(treeName);
		}
		
		@Override
		public ByteString read(TreeName treeName, ByteSequence key) {
			final Row row=execute(
				prepared.get("SELECT value FROM "+getTableName()+" WHERE baseDN=:baseDN and indexId=:indexId and key=:key").bind()
					.setString("baseDN", treeName.getBaseDN()).setString("indexId", treeName.getIndexId())
					.setByteBuffer("key", ByteBuffer.wrap(key.toByteArray()))
			).one();
			return row==null?null:ByteString.wrap(row.getByteBuffer("value").array());
		}

		@Override
		public Cursor<ByteString, ByteString> openCursor(TreeName treeName) {
			return new CursorImpl(this,treeName);
		}

		@Override
		public long getRecordCount(TreeName treeName) {
			return execute(
				prepared.get("SELECT count(*) FROM "+getTableName()+" WHERE baseDN=:baseDN and indexId=:indexId").bind()
					.setString("baseDN", treeName.getBaseDN()).setString("indexId", treeName.getIndexId())
			).one().getLong(0);
		}

		@Override
		public void deleteTree(TreeName treeName) {
			checkReadOnly();
			openTree(treeName,true);
			execute(
				prepared.get("DELETE FROM "+getTableName()+" WHERE baseDN=:baseDN and indexId=:indexId").bind()
					.setString("baseDN", treeName.getBaseDN()).setString("indexId", treeName.getIndexId())
			);
		}

		@Override
		public void put(TreeName treeName, ByteSequence key, ByteSequence value) {
			checkReadOnly();
			execute(
				prepared.get("INSERT INTO "+getTableName()+" (baseDN,indexId,key,value) VALUES (:baseDN,:indexId,:key,:value)").bind()
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
			execute(
				prepared.get("DELETE FROM "+getTableName()+" WHERE baseDN=:baseDN and indexId=:indexId and key=:key").bind()
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
			rc=full();
			iterator=rc.iterator();
		}

		ResultSet full(){
			return execute(
				prepared.get("SELECT key,value FROM "+getTableName()+" WHERE baseDN=:baseDN and indexId=:indexId ORDER BY key").bind()
					.setString("baseDN", treeName.getBaseDN()).setString("indexId", treeName.getIndexId())
			);
		}
		
		@Override
		public boolean next() {
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
			if (!isDefined()) {
				throw new NoSuchElementException();
			}
			return ByteString.wrap(current.getByteBuffer("key").array());
		}

		@Override
		public ByteString getValue() throws NoSuchElementException {
			if (!isDefined()) {
				throw new NoSuchElementException();
			}
			return ByteString.wrap(current.getByteBuffer("value").array());
		}

		@Override
		public void delete() throws NoSuchElementException, UnsupportedOperationException {
			if (!isDefined()) {
				throw new NoSuchElementException();
			}
			tx.delete(treeName, getKey());
		}

		@Override
		public void close() {
			iterator=null;
			current=null;
			rc=null;
		}


		@Override
		public boolean positionToKeyOrNext(ByteSequence key) {
			if (!isDefined() || key.compareTo(getKey())<0) { //restart iterator
				iterator=rc.iterator();
			}
			while (iterator.hasNext()) {
				current=iterator.next();
				if (key.compareTo(getKey())<=0) {
					return true;
				}
			}
			current=null;
			return false;
		}
		
		@Override
		public boolean positionToKey(ByteSequence key) {
			if (!isDefined() || key.compareTo(getKey())<0) {  //restart iterator
				iterator=rc.iterator();
			}
			if (isDefined() && key.compareTo(getKey())==0) {
				return true;
			}
			while (iterator.hasNext()) {
				current=iterator.next();
				if (key.compareTo(getKey())==0) {
					return true;
				}
			}
			current=null;
			return false;
		}

		
		@Override
		public boolean positionToLastKey() {
			while (iterator.hasNext()) {
				current=iterator.next();
			}
			if (current!=null) {
				return true;
			}
			return false;
		}

		@Override
		public boolean positionToIndex(int index) {
			iterator=rc.iterator();  //restart iterator
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
		
		final Boolean isOpen;
		
		public ImporterImpl() {
			isOpen=getStorageStatus().isWorking();
			if (!isOpen) {
				try {
					open(AccessMode.READ_WRITE);
				}catch (Exception e) {
					throw new StorageRuntimeException(e);
				}
			}
			tx=new TransactionImpl(accessMode);
		}
		
		@Override
		public void close() {
			if (!isOpen) {
				Storage.this.close();
			}
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
