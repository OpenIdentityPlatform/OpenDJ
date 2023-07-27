package org.opends.server.backends.cassandra;


import static org.opends.server.backends.pluggable.spi.StorageUtils.addErrorMessage;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.Closeable;
import java.nio.ByteBuffer;
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

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;

public class Storage implements org.opends.server.backends.pluggable.spi.Storage, ConfigurationChangeListener<CASBackendCfg>{
	
	private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

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
		tx=new TransactionImpl(config);
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
		}
		storageStatus = StorageStatus.lockedDown(LocalizableMessage.raw("closed"));
	}

	String getTableName(TreeName name) {
		return "\""+config.getDBDirectory()+"\".\""+config.getBackendId()+"_"+name.toString().replaceAll("[^a-zA-z0-9_]", "_")+"\"";
	}
	
	@Override
	public void removeStorageFiles() throws StorageRuntimeException {
		final CqlSession session=CqlSession.builder()
				.withApplicationName("OpenDJ removeStorageFiles "+config.getDBDirectory()+"."+config.getBackendId())
				.withConfigLoader(DriverConfigLoader.fromDefaults(Storage.class.getClassLoader()))
				.build();
		final ResultSet rc=session.execute(
				session.prepare("SELECT table_name FROM system_schema.tables WHERE keyspace_name = :keyspace_name").bind()
					.setString("keyspace_name", config.getDBDirectory())
				);
		for (Row row : rc) {
			if (row.getString("table_name").startsWith(config.getBackendId().replaceAll("[^a-zA-z0-9_]", "_"))) {
				session.execute(session.prepare("DROP TABLE IF EXISTS \""+config.getDBDirectory()+"\".\""+row.getString("table_name")+"\";").bind().setExecutionProfileName(profile));
			}
		}
		session.close();
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
		
		public TransactionImpl(CASBackendCfg config) {
			super();
			session=CqlSession.builder()
					.withApplicationName("OpenDJ "+config.getDBDirectory()+"."+config.getBackendId())
					.withConfigLoader(DriverConfigLoader.fromDefaults(Storage.class.getClassLoader()))
					.build();
			session.execute(session.prepare("CREATE KEYSPACE IF NOT EXISTS \""+config.getDBDirectory()+"\" WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'};").bind().setExecutionProfileName(profile));
			session.execute(session.prepare("USE \""+config.getDBDirectory()+"\";").bind().setExecutionProfileName(profile));
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
				session.execute(session.prepare("CREATE TABLE IF NOT EXISTS "+getTableName(name)+" (key blob,value blob,PRIMARY KEY (key));").bind().setExecutionProfileName(profile));
			}
		}
		
		public void clearTree(TreeName name) {
			openTree(name,true);
			session.execute(session.prepare("TRUNCATE TABLE "+getTableName(name)+";").bind().setExecutionProfileName(profile));
		}
		
		@Override
		public ByteString read(TreeName treeName, ByteSequence key) {
			final Row row=session.execute(
					session.prepare("SELECT value FROM "+getTableName(treeName)+" WHERE key=:key").bind()
						.setByteBuffer("key",  ByteBuffer.wrap(key.isEmpty()?new byte[] {Byte.MIN_VALUE}:key.toByteArray())) 
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
						session.prepare("SELECT count(*) FROM "+getTableName(treeName)).bind()
					).one().getLong(0);
		}

		@Override
		public void deleteTree(TreeName treeName) {
			session.execute(
					session.prepare("DROP TABLE IF EXISTS "+getTableName(treeName)+" ").bind()
					);
		}

		@Override
		public void put(TreeName treeName, ByteSequence key, ByteSequence value) {
			session.execute(
				session.prepare("INSERT INTO "+getTableName(treeName)+" (key,value) VALUES (:key,:value)").bind()
					.setByteBuffer("key",  ByteBuffer.wrap(key.isEmpty()?new byte[] {Byte.MIN_VALUE}:key.toByteArray()))
					.setByteBuffer("value", ByteBuffer.wrap(value.toByteArray()))
				);
		}

		@Override
		public boolean update(TreeName treeName, ByteSequence key, UpdateFunction f) {
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
			session.execute(
					session.prepare("DELETE FROM "+getTableName(treeName)+" WHERE key=:key").bind()
						.setByteBuffer("key",  ByteBuffer.wrap(key.isEmpty()?new byte[] {Byte.MIN_VALUE}:key.toByteArray()))
					);
			return true;
		}
	}
	
	private final class CursorImpl implements Cursor<ByteString, ByteString> {
		final TreeName treeName;
		final TransactionImpl tx;

		Iterator<Row> iterator;
		Row current=null;
		
		public CursorImpl(TransactionImpl tx,TreeName treeName) {
			this.treeName=treeName;
			this.tx=tx;
			iterator=full();
		}

		Iterator<Row> full(){
			return tx.session.execute(
						tx.session.prepare("SELECT key,value FROM "+getTableName(treeName)).bind()
					).iterator();
		}
		
		@Override
		public boolean next() {
			if (iterator!=null) {
				try {
					current=iterator.next();
					return true;
				}catch (NoSuchElementException e) {
					current=null;
				}
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
			return ByteString.valueOfBytes(current.getByteBuffer("key").equals(ByteBuffer.wrap(new byte[] {Byte.MIN_VALUE}))?new byte[] {}: current.getByteBuffer("key").array());
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
		}

		@Override
		public boolean positionToKey(ByteSequence key) {
			iterator=full();
			while(iterator.hasNext()){
				current=iterator.next();
				if (getKey().compareTo(key)==0) {
					return true;
				}
		    }
			current=null;
			return false;
		}

		@Override
		public boolean positionToKeyOrNext(ByteSequence key) {
			iterator=full();
			while(iterator.hasNext()){
				current=iterator.next();
				if (getKey().compareTo(key)>=0) {
					return true;  
				}
		    }
			current=null;
			return false;
		}

		@Override
		public boolean positionToLastKey() {
			iterator=full();
			current=null;
			while(iterator.hasNext()){
				current=iterator.next();
		    }
			return current!=null;
		}

		@Override
		public boolean positionToIndex(int index) {
			iterator=full();
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
		return null;
	}
	

	private final class ImporterImpl implements Importer {
		final TransactionImpl tx;
		
		public ImporterImpl() {
			tx=new TransactionImpl(config);
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
	public void createBackup(BackupConfig backupConfig) throws DirectoryException {
		// TODO Auto-generated method stub
	}

	@Override
	public void removeBackup(BackupDirectory backupDirectory, String backupID) throws DirectoryException {
		// TODO Auto-generated method stub
	}

	@Override
	public void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException {
		// TODO Auto-generated method stub
	}
}
