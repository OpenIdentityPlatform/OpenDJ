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
 * Copyright 2023-2026 3A Systems, LLC.
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
import java.util.regex.Pattern;

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
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

public class CASStorage implements org.opends.server.backends.pluggable.spi.Storage, ConfigurationChangeListener<CASBackendCfg>{
	
	private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

	private CASBackendCfg config;

	//a cursor starts with a small page so that a point lookup does not transfer thousands of rows,
	//and doubles it while a scan keeps outrunning it (the driver default page is 5000 rows)
	final int initialPageSize=Math.max(1,Integer.getInteger("org.openidentityplatform.opendj.cassandra.fetchsize.initial",32));
	final int maxPageSize=Math.max(initialPageSize,Integer.getInteger("org.openidentityplatform.opendj.cassandra.fetchsize",1000));

	//CursorImpl.forwardInPage outcomes
	final static int POSITIONED=1;
	final static int MISSING=0;
	final static int NEEDS_SEEK=-1;
	final static int PAGE_OUT=-2;

	public CASStorage(CASBackendCfg cfg, ServerContext serverContext) {
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
			.withConfigLoader(DriverConfigLoader.fromDefaults(CASStorage.class.getClassLoader()))
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
		return "\""+System.getProperty("keyspace",config.getDBDirectory()).replaceAll("[^a-zA-Z0-9_]", "_")+"\"";
	}
	
	String getTableName() {
		return getKeyspaceName()+".\""+config.getBackendId().replaceAll("[^a-zA-Z0-9_]", "_")+"\"";
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
	// The wordings a server uses for a table or a keyspace that is not there. Matched rather than
	// the exception type, which covers the whole INVALID protocol code: see namesAnAbsentTable().
	// "Undefined column name ..." deliberately does not match - that table exists.
	static final Pattern ABSENT_TABLE=Pattern.compile(
		"unconfigured (table|columnfamily)|(table|keyspace)[^,]* does not exist|undefined (table|keyspace)",
		Pattern.CASE_INSENSITIVE);

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
		public boolean treeExists(TreeName treeName) {
			// Every tree of this backend is a partition of the one table named after the backend id,
			// so a tree has no existence apart from its rows: "exists" here means "holds at least one
			// record". A LIMIT 1 lookup rather than getRecordCount() to avoid the full partition scan
			// a count would run.
			try {
				return execute(
					prepared.get("SELECT key FROM "+getTableName()+" WHERE baseDN=:baseDN and indexId=:indexId LIMIT 1").bind()
						.setString("baseDN", treeName.getBaseDN()).setString("indexId", treeName.getIndexId())
				).one()!=null;
			}catch (InvalidQueryException e) {
				// The backend's own table has not been created yet - a read-only open of a backend
				// that was never written, where openTree() creates nothing - so none of its trees
				// can exist either. The driver reports it from prepare() as much as from execute().
				//
				// Narrowly, twice over, because calling a populated tree absent would have the
				// compressed schema restart its token allocation from zero and overwrite the
				// definitions the entries already written were encoded with (#873):
				//
				// - InvalidQueryException carries the whole INVALID protocol code, so the rejection
				//   has to name an absent table or keyspace. An undefined column, say, means the
				//   table is there with a shape this backend did not write, and answering "absent"
				//   for it would be that same corruption;
				// - and only a read-only open may answer it at all. A writeable open has just run
				//   CREATE TABLE IF NOT EXISTS through openTree(), so a rejection there is a fault
				//   and must fail the open, as it did before this method existed and loadTrees()
				//   opened its cursor unconditionally. This is also where a table a coordinator has
				//   not caught up with lands - what a rolling upgrade produces, since schema
				//   agreement is never reached in a mixed-version cluster - and it fails loudly
				//   rather than being taken for a table that was never created.
				if (accessMode.isWriteable() || !namesAnAbsentTable(e)) {
					throw e;
				}
				return false;
			}
		}

		/**
		 * Whether a rejected query says that the table or the keyspace is not there, as opposed to
		 * anything else the INVALID protocol code covers. The driver offers nothing finer than the
		 * server's own message - the code is one value for the whole bucket - so the wordings of
		 * the server are matched, across the versions that changed them ("unconfigured
		 * columnfamily" became "unconfigured table", and a keyspace is reported as not existing).
		 * A wording that is not among them is not read as an absent table: it fails the open, which
		 * is the side to err on.
		 */
		private boolean namesAnAbsentTable(InvalidQueryException e) {
			return e.getMessage()!=null && ABSENT_TABLE.matcher(e.getMessage()).find();
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
	
	// Iterates the (baseDN,indexId) partition in key order.
	// A ResultSet can only be consumed once: rc.iterator() always returns the same iterator,
	// so "restarting" it never rewinds. Every repositioning that cannot be served by moving
	// forward within the already-fetched rows therefore runs a new server-side slice query
	// on the "key" clustering column instead.
	// Pages are sized by the cursor rather than by the driver default of 5000 rows: a point
	// lookup must not transfer thousands of entries, so a cursor starts with a small page and
	// doubles it (up to maxPageSize) every time a scan outruns it. Scanning past the end of a
	// page runs the next slice from the current key instead of letting the driver page with the
	// size the cursor started with.
	final class CursorImpl implements Cursor<ByteString, ByteString> {
		final TreeName treeName;
		final TransactionImpl tx;
		final String tableName=getTableName();

		//visible for tests
		long queryCount;
		int pageSize=initialPageSize;

		ResultSet rc;
		Iterator<Row> iterator;
		//the row the cursor is on, or - when defined is false - the row a failed positionToKey
		//stopped just before, which is where next() resumes (same as pdb)
		Row current=null;
		boolean defined=false;
		//rc holds a DESC page: it must never serve a forward move
		boolean descending=false;
		boolean closed=false;

		public CursorImpl(TransactionImpl tx,TreeName treeName) {
			this.treeName=treeName;
			this.tx=tx;
			//lazy: the first navigation decides which query to run, a seek must not pay for a partition scan
		}

		ResultSet select(String condition,ByteSequence key,int rows){
			queryCount++;
			BoundStatement statement=prepared.get("SELECT key,value FROM "+tableName+" WHERE baseDN=:baseDN and indexId=:indexId"+condition).bind()
				.setString("baseDN", treeName.getBaseDN()).setString("indexId", treeName.getIndexId())
				.setPageSize(rows);
			if (key!=null) {
				statement=statement.setByteBuffer("key", ByteBuffer.wrap(key.toByteArray()));
			}
			return execute(statement);
		}

		//runs a new page and positions the cursor on its first row
		boolean slice(String condition,ByteSequence key){
			rc=select(condition,key,pageSize);
			iterator=rc.iterator();
			descending=false;
			if (iterator.hasNext()) {
				current=iterator.next();
				defined=true;
				return true;
			}
			current=null;
			defined=false;
			return false;
		}

		boolean seek(ByteSequence key){
			return slice(" and key>=:key ORDER BY key",key);
		}

		void growPage() {
			pageSize=(int)Math.min(maxPageSize,2L*pageSize);
		}

		//serves a forward repositioning from the rows the driver already fetched: they are the
		//sorted rows following the current one (blob clustering collates in unsigned byte order)
		int forwardInPage(ByteSequence key,boolean exactMatch) {
			if (!defined || descending || rc==null) {
				return NEEDS_SEEK;
			}
			int cmp=key.compareTo(getKey());
			if (cmp==0) {
				return POSITIONED;
			}
			if (cmp<0) { //backward: only the server can rewind
				return NEEDS_SEEK;
			}
			while (rc.getAvailableWithoutFetching()>0) {
				current=iterator.next();
				cmp=key.compareTo(getKey());
				if (cmp==0) {
					return POSITIONED;
				}
				if (cmp<0) { //walked past the key
					if (exactMatch) {
						defined=false; //the cursor stops just before this row
						return MISSING;
					}
					return POSITIONED;
				}
			}
			return PAGE_OUT;
		}

		@Override
		public boolean next() {
			if (closed) {
				return false;
			}
			if (current!=null && !defined) { //a failed positionToKey stopped just before this row
				defined=true;
				return true;
			}
			if (rc==null) { //lazy cursor: the first navigation runs the scan
				return slice(" ORDER BY key",null);
			}
			if (!descending && rc.getAvailableWithoutFetching()>0) {
				current=iterator.next();
				defined=true;
				return true;
			}
			if (current==null) { //exhausted or explicitly undefined: stay there
				return false;
			}
			if (!descending && rc.isFullyFetched()) { //the server has nothing left either
				current=null;
				defined=false;
				return false;
			}
			//page boundary: continue with a bigger slice starting right after the current key
			growPage();
			return slice(" and key>:key ORDER BY key",getKey());
		}

		@Override
		public boolean isDefined() {
			return defined;
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

		//a closed cursor is undefined and every navigation on it returns false, like EmptyCursor
		@Override
		public void close() {
			closed=true;
			iterator=Collections.emptyIterator();
			current=null;
			defined=false;
			descending=false;
			rc=null;
		}


		@Override
		public boolean positionToKeyOrNext(ByteSequence key) {
			if (closed) {
				return false;
			}
			final int served=forwardInPage(key,false);
			if (served>=0) {
				return served==POSITIONED;
			}
			if (served==PAGE_OUT) { //the scan outran its page: the next slice should be bigger
				growPage();
			}
			return seek(key);
		}

		@Override
		public boolean positionToKey(ByteSequence key) {
			if (closed) {
				return false;
			}
			final int served=forwardInPage(key,true);
			if (served>=0) {
				return served==POSITIONED;
			}
			if (served==PAGE_OUT) {
				growPage();
			}
			if (seek(key) && key.compareTo(getKey())==0) {
				return true;
			}
			//like jeb/pdb a miss leaves the cursor undefined; the row the seek landed on is the
			//first key after the missing one, so next() resumes there instead of skipping it
			defined=false;
			return false;
		}


		@Override
		public boolean positionToLastKey() {
			if (closed) {
				return false;
			}
			rc=select(" ORDER BY key DESC LIMIT 1",null,1);
			iterator=rc.iterator();
			descending=true;
			if (iterator.hasNext()) {
				current=iterator.next(); //nothing follows the last key
				defined=true;
				return true;
			}
			current=null;
			defined=false;
			return false;
		}

		@Override
		public boolean positionToIndex(int index) {
			if (closed) {
				return false;
			}
			if (index<0) {
				rc=null; //an invalid index resets the cursor: next() starts the scan again
				iterator=null;
				current=null;
				defined=false;
				descending=false;
				return false;
			}
			//CQL has no offset clause: restart from the first row and skip. The rows that have to
			//be walked are asked for in one page instead of one round trip per page
			rc=select(" ORDER BY key",null,(int)Math.min(maxPageSize,Math.max(pageSize,index+1L)));
			iterator=rc.iterator();
			descending=false;
			for (int ct=0;ct<=index;ct++) {
				if (!iterator.hasNext()) {
					current=null;
					defined=false;
					return false;
				}
				current=iterator.next();
			}
			defined=true;
			return true;
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
				CASStorage.this.close();
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
