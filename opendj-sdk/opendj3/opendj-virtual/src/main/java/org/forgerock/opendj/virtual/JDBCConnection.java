package org.forgerock.opendj.virtual;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionEventListener;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.ErrorResultIOException;
import org.forgerock.opendj.ldap.FutureResult;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ResultHandler;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchResultReferenceIOException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.GenericExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldif.ChangeRecord;
import org.forgerock.opendj.ldif.ConnectionEntryReader;

public final class JDBCConnection implements Connection {
    private final String driverName = "com.mysql.jdbc.Driver";
	private java.sql.Connection connection;
	private String connectionUrl;
	private String userName;
	private String userPass;
	private JDBCMapper jdbcm;
	private MappingConfigurationManager mcm;
	/**
     * Creates a new JDBC connection.
     *
     * @param connection
     *            The SQL Connection using JDBC Driver.
     */
    JDBCConnection(final String connectionURL) {
    	this.connectionUrl = connectionURL;
    }   
    
    public void initializeMapper(JDBCMapper jdbcmapper) throws SQLException, ErrorResultException, ErrorResultIOException, SearchResultReferenceIOException{
    	jdbcm = jdbcmapper;
    	jdbcm.fillMaps();
		mcm = new MappingConfigurationManager(jdbcm);
		jdbcm.loadMappingConfig(mcm.loadMapping());
    }
    
    public java.sql.Connection getSqlConnection(){
    	return connection;
    }

	@Override
	public FutureResult<Void> abandonAsync(AbandonRequest request) {
		// TODO Auto-generated method stub		
		return null;
	}
	
	private Map<String, Object> getValuesMap(AddRequest request, String tableName, String OUName){
		Iterable<Attribute> attributesCollection = request.getAllAttributes();
		Iterator<Attribute> attributeIter = attributesCollection.iterator();
		Map<String, Object> map = new HashMap<String, Object>();
		
		
		while(attributeIter.hasNext()){
			Attribute att = attributeIter.next();
			Iterator<ByteString> valueIter = att.iterator();
			String attributeName = att.getAttributeDescriptionAsString();
			String columnName = jdbcm.getColumnNameFromMapping(tableName, OUName, attributeName);
			String columnValue = "";
			
			if (columnName == null) continue;
			
			while(valueIter.hasNext()){
				columnValue = columnValue.concat(valueIter.next().toString());
			}
			map.put(columnName, columnValue);
		}
		return map;
	}
	
	private ArrayList<String> getSQLVariablesStrings(String tableName, Map columnValuesMap){
		ArrayList<String>columnList = null;
		try {
			columnList = jdbcm.getTableColumns(tableName);
		} catch (SQLException e1) {
			e1.printStackTrace();
		}
		String columnNamesString = " (";
		String columnValuesString = " (";
		
		for(int i = 0; i < columnList.size(); i++){
			if (i > 0){
				columnNamesString = columnNamesString.concat(", ");
				columnValuesString = columnValuesString.concat(", ");
			}
			String columnName = columnList.get(i);
			Object columnValue = columnValuesMap.get(columnName);
			Object dataType = jdbcm.getTableColumnDataType(tableName, columnName);
			if(columnValue == null){
				if(dataType.equals(Integer.class)) columnValue = "0";
				else columnValue = "Default Value";
			}
			if(dataType.equals(Integer.class)) columnValue = Integer.parseInt(columnValue.toString());
			
			columnNamesString = columnNamesString.concat(columnName);
			columnValuesString = columnValuesString.concat("'" + columnValue + "'");
		}
		columnNamesString = columnNamesString.concat(")");
		columnValuesString = columnValuesString.concat(")");
		
		ArrayList<String> newlist = new ArrayList<String>();
		newlist.add(columnNamesString);
		newlist.add(columnValuesString);
		
		return newlist;
	}
	
	private Result addOperation(AddRequest request){
		Result r;
		try {
			final String DN = request.getName().toString();
			String[] stringSplitter = DN.split("ou=");
			stringSplitter = stringSplitter[1].split(",");
			final String organizationalUnitName = stringSplitter[0];
			final String tableName = jdbcm.getTableNameFromMapping(organizationalUnitName);
			final Map<String, Object> columnValuesMap = getValuesMap(request, tableName, organizationalUnitName);
			final ArrayList<String> SQLStringList = getSQLVariablesStrings(tableName, columnValuesMap);
			String columnNamesString = SQLStringList.get(0), columnValuesString = SQLStringList.get(1);
			
			Statement st = connection.createStatement();
			String sql = "INSERT INTO " + tableName + columnNamesString + " VALUES" + columnValuesString;
			st.executeUpdate(sql);
			r = Responses.newResult(ResultCode.SUCCESS);
		} catch (SQLException e) {
			System.out.println(e.toString());
			r = Responses.newResult(ResultCode.UNWILLING_TO_PERFORM);
		}
		return r;
	}
	
	@Override
	public Result add(AddRequest request) throws ErrorResultException { 
		return addOperation(request);
	}

	@Override
	public Result add(Entry entry) throws ErrorResultException {
		AddRequest addRequest = Requests.newAddRequest(entry);
		return addOperation(addRequest);
	}

	@Override
	public Result add(String... ldifLines) throws ErrorResultException {
		AddRequest addRequest = Requests.newAddRequest(ldifLines);
		return addOperation(addRequest);
	}

	@Override
	public FutureResult<Result> addAsync(AddRequest request,
			IntermediateResponseHandler intermediateResponseHandler,
			ResultHandler<? super Result> resultHandler) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addConnectionEventListener(ConnectionEventListener listener) {
		// TODO Auto-generated method stub
	}

	@Override
	public Result applyChange(ChangeRecord request) throws ErrorResultException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public FutureResult<Result> applyChangeAsync(ChangeRecord request,
			IntermediateResponseHandler intermediateResponseHandler,
			ResultHandler<? super Result> resultHandler) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public BindResult bind(BindRequest request) throws ErrorResultException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public BindResult bind(String name, char[] password)
			throws ErrorResultException {
		BindResult r;
		this.userName = name;
		this.userPass = new String(password);
		try {
        	Class.forName(driverName);
        	this.connection = DriverManager
			        .getConnection(this.connectionUrl,this.userName,this.userPass);
        	} catch (ClassNotFoundException e) {
        	System.out.println(e.toString());
        	r = Responses.newBindResult(ResultCode.OTHER);
        	return r;
        } catch (SQLException e) {
        	System.out.println(e.toString());
        	r = Responses.newBindResult(ResultCode.CLIENT_SIDE_CONNECT_ERROR);
			return r;
		}
		r = Responses.newBindResult(ResultCode.SUCCESS);
		return r;
	}

	@Override
	public FutureResult<BindResult> bindAsync(BindRequest request,
			IntermediateResponseHandler intermediateResponseHandler,
			ResultHandler<? super BindResult> resultHandler) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
	}

	@Override
	public void close(UnbindRequest request, String reason) {
		// TODO Auto-generated method stub
	}

	@Override
	public CompareResult compare(CompareRequest request)
			throws ErrorResultException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompareResult compare(String name, String attributeDescription,
			String assertionValue) throws ErrorResultException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public FutureResult<CompareResult> compareAsync(CompareRequest request,
			IntermediateResponseHandler intermediateResponseHandler,
			ResultHandler<? super CompareResult> resultHandler) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Result delete(DeleteRequest request) throws ErrorResultException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Result delete(String name) throws ErrorResultException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public FutureResult<Result> deleteAsync(DeleteRequest request,
			IntermediateResponseHandler intermediateResponseHandler,
			ResultHandler<? super Result> resultHandler) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <R extends ExtendedResult> R extendedRequest(
			ExtendedRequest<R> request) throws ErrorResultException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <R extends ExtendedResult> R extendedRequest(
			ExtendedRequest<R> request, IntermediateResponseHandler handler)
			throws ErrorResultException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public GenericExtendedResult extendedRequest(String requestName,
			ByteString requestValue) throws ErrorResultException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <R extends ExtendedResult> FutureResult<R> extendedRequestAsync(
			ExtendedRequest<R> request,
			IntermediateResponseHandler intermediateResponseHandler,
			ResultHandler<? super R> resultHandler) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isClosed() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isValid() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Result modify(ModifyRequest request) throws ErrorResultException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Result modify(String... ldifLines) throws ErrorResultException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public FutureResult<Result> modifyAsync(ModifyRequest request,
			IntermediateResponseHandler intermediateResponseHandler,
			ResultHandler<? super Result> resultHandler) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Result modifyDN(ModifyDNRequest request) throws ErrorResultException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Result modifyDN(String name, String newRDN)
			throws ErrorResultException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public FutureResult<Result> modifyDNAsync(ModifyDNRequest request,
			IntermediateResponseHandler intermediateResponseHandler,
			ResultHandler<? super Result> resultHandler) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SearchResultEntry readEntry(DN name, String... attributeDescriptions)
			throws ErrorResultException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SearchResultEntry readEntry(String name,
			String... attributeDescriptions) throws ErrorResultException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public FutureResult<SearchResultEntry> readEntryAsync(DN name,
			Collection<String> attributeDescriptions,
			ResultHandler<? super SearchResultEntry> handler) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void removeConnectionEventListener(ConnectionEventListener listener) {
		// TODO Auto-generated method stub
	}

	@Override
	public ConnectionEntryReader search(SearchRequest request) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Result search(SearchRequest request,
			Collection<? super SearchResultEntry> entries)
			throws ErrorResultException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Result search(SearchRequest request,
			Collection<? super SearchResultEntry> entries,
			Collection<? super SearchResultReference> references)
			throws ErrorResultException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Result search(SearchRequest request, SearchResultHandler handler)
			throws ErrorResultException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ConnectionEntryReader search(String baseObject, SearchScope scope,
			String filter, String... attributeDescriptions) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public FutureResult<Result> searchAsync(SearchRequest request,
			IntermediateResponseHandler intermediateResponseHandler,
			SearchResultHandler resultHandler) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SearchResultEntry searchSingleEntry(SearchRequest request)
			throws ErrorResultException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SearchResultEntry searchSingleEntry(String baseObject,
			SearchScope scope, String filter, String... attributeDescriptions)
			throws ErrorResultException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public FutureResult<SearchResultEntry> searchSingleEntryAsync(
			SearchRequest request,
			ResultHandler<? super SearchResultEntry> handler) {
		// TODO Auto-generated method stub
		return null;
	}

  @Override
  public Result deleteSubtree(String name) throws ErrorResultException
  {
    // TODO Auto-generated method stub
    return null;
  }
}
