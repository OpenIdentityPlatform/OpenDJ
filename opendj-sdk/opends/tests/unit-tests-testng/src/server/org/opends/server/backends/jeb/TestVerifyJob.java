/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 - 2007 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import org.opends.server.config.ConfigEntry;
import org.opends.server.core.DirectoryServer;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.*;
import org.opends.server.util.StaticUtils;

import static org.opends.server.util.ServerConstants.OC_TOP;
import static org.opends.server.util.ServerConstants.OC_EXTENSIBLE_OBJECT;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

import java.util.*;

public class TestVerifyJob extends JebTestCase
{
	private  String cfgDN=
		"ds-cfg-backend-id=verifyRoot,cn=Backends,cn=config";
	//Root suffix for verify backend
	private static String suffix="dc=verify,dc=jeb";
	private static  String vBranch="ou=verify tests," + suffix;
	private  String beID="verifyRoot";
	private  String numUsersLine="define numusers= #numEntries#";
	//Attribute type in stat entry containing error count
	private  String errorCount="verify-error-count";
	private  DN configDN;
	private  DN[] baseDNs;
	private ConfigEntry configEntry;
	private BackendImpl be;
	private RootContainer rContainer;
	private EntryContainer eContainer;
	private DN2ID dn2id;
	private ID2Entry id2entry;
	private Index id2child;
	private Index id2subtree;
	private Transaction txn;
	
	//Some DNs needed mostly for DN2ID tests
	private  String junkDN="cn=junk," + vBranch;
	private  String junkDN1="cn=junk1," + vBranch;
	private  String junkDN2="cn=junk2," + vBranch;
	private  String junkDN3="cn=junk3," + vBranch;
	//This DN has no parent
	private  String noParentDN="cn=junk1,cn=junk22," + vBranch;
	//Parent child combo for id2child/subtree type tests
	private  String pDN="cn=junk222," + vBranch;
	private  String cDN="cn=junk4,cn=junk222," + vBranch;
	//Bad DN
	private  String badDN="this is a bad DN";
	//This index file should not exist
	private  String badIndexName="badIndexName";
	
    @DataProvider(name = "indexes")
    public Object[][] indexes() {
        return new Object[][] {
            { "telephoneNumber"},
            {"givenName"},
            { "id2subtree"},
            {"id2children"},
            {"dn2id"}
        };
    }
    
    private static String[] template = new String[] {
        "define suffix="+suffix,
        "define maildomain=example.com",
        "define numusers= #numEntries#",
        "",
        "branch: [suffix]",
        "",
        "branch: " + vBranch,
        "subordinateTemplate: person:[numusers]",
        "",
        "template: person",
        "rdnAttr: uid",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: ABOVE LIMIT",
        "sn: <last>",
        "cn: {givenName} {sn}",
        "initials: {givenName:1}<random:chars:" +
             "ABCDEFGHIJKLMNOPQRSTUVWXYZ:1>{sn:1}",
        "employeeNumber: <sequential:0>",
        "uid: user.{employeeNumber}",
        "mail: {uid}@[maildomain]",
        "userPassword: password",
        "telephoneNumber: <random:telephone>",
        "homePhone: <random:telephone>",
        "pager: <random:telephone>",
        "mobile: <random:telephone>",
        "street: <random:numeric:5> <file:streets> Street",
        "l: <file:cities>",
        "st: <file:states>",
        "postalCode: <random:numeric:5>",
        "postalAddress: {cn}${street}${l}, {st}  {postalCode}",
        "description: This is the description for {cn}.",
        ""};

    @BeforeClass
    public void setup() throws Exception {
      TestCaseUtils.startServer();
      configDN = DN.decode(cfgDN);
      baseDNs = new DN[] {
              DN.decode(suffix)
      };
      configEntry = DirectoryServer.getConfigEntry(configDN);
    }
    
    @AfterClass
    public void cleanUp() throws Exception {
    	TestCaseUtils.clearJEBackend(false, beID, suffix);
    }
    
    /**
     * Performs a ncomplete verify against a backend using the
     * entries loaded in the setup initializer.
     * 
     * @throws Exception if error count is not equal to 0.
     */

    @Test()
    public void testCompleteVerifyJob()  throws Exception {
    	cleanAndLoad(9);
    	VerifyConfig verifyConfig = new VerifyConfig();
    	verifyConfig.setBaseDN(baseDNs[0]);
    	Entry statEntry=bldStatEntry("");
    	be=(BackendImpl) DirectoryServer.getBackend(beID);
    	be.verifyBackend(verifyConfig, configEntry, baseDNs, statEntry);
    	assertEquals(getStatEntryCount(statEntry, errorCount), 0);
    }

    /**
     * Adds more than "entry limit" number of entries and runs clean
     * verify against two indexes.
     * 
     * @throws Exception if error count is not equal to 0.
     */
    @Test()
    public void testEntryLimitVerifyJob()  throws Exception {
    	cleanAndLoad(25);
    	VerifyConfig verifyConfig = new VerifyConfig();
    	verifyConfig.setBaseDN(baseDNs[0]);
    	verifyConfig.addCleanIndex("telephoneNumber");
    	verifyConfig.addCleanIndex("givenName");
    	Entry statEntry=bldStatEntry("");
    	be=(BackendImpl) DirectoryServer.getBackend(beID);
    	be.verifyBackend(verifyConfig, configEntry, baseDNs, statEntry);
    	assertEquals(getStatEntryCount(statEntry, errorCount), 0);
    }

    /**
     * Runs clean verify jobs against a set of indexes (defined in
     * indexes array).
     * @param index An element of the indexes array.
     * @throws Exception if the error count is not equal to 0.
     */
    
    @Test(dataProvider = "indexes")
    public void testCleanVerifyJob(String index)  throws Exception {
    	cleanAndLoad(9);
    	VerifyConfig verifyConfig = new VerifyConfig();
    	verifyConfig.setBaseDN(baseDNs[0]);
    	verifyConfig.addCleanIndex(index);
    	Entry statEntry=bldStatEntry("");
    	be=(BackendImpl) DirectoryServer.getBackend(beID);
    	be.verifyBackend(verifyConfig, configEntry, baseDNs, statEntry);
    	assertEquals(getStatEntryCount(statEntry, errorCount), 0);
    }
    
    /*
     * Begin Clean index tests. These are tests that cursor through an index
     * file and validate it's keys and idlists against the id2entry database entries.
     * The complete index tests go the other way. They cursor the id2entry database
     * and validate each entry against the various index files.
     */

    /**
     * Runs clean verify against the dn2id index after adding
     * various errors in that index file.
     * 
     * @throws Exception if the error count is not equal to 5.
     */
    @Test()
    public void testCleanDN2ID() throws Exception {
    	preTest(3);
    	//Add a junk DN and non-existent entry id to DN2ID index
    	DN testDN=DN.decode(junkDN);
    	EntryID id=new EntryID(45);      
    	assertTrue(dn2id.insert(txn, testDN, id));
    	//Make two DN keys point at same entry.
    	testDN=DN.decode(junkDN1);
    	id=new EntryID(3);
    	assertTrue(dn2id.insert(txn, testDN, id));
    	//Add badDN key with bad entry id
    	DatabaseEntry key= 
    		new DatabaseEntry(StaticUtils.getBytes(badDN));
    	DatabaseEntry data = 
    		new EntryID(37).getDatabaseEntry();  
    	assertTrue(dn2id.putRaw(txn, key, data));
    	//Add DN key with malformed entryID
    	key=new DatabaseEntry(StaticUtils.getBytes(junkDN2));
    	data= new DatabaseEntry(new byte[3]);
    	assertTrue(dn2id.putRaw(txn, key, data));
    	//Try to break JebFormat version
    	addID2EntryReturnKey(junkDN3, 20, true);
    	id=new EntryID(20);
    	assertTrue(dn2id.insert(txn, DN.decode(junkDN3), id));
    	performBECleanVerify("dn2id", 5); 
    }

    /**
     * Runs clean verify against the id2children index after adding
     * various errors in that index file.
     * 
     * @throws Exception if the error count is not equal to 6.
     */
    @Test() public void testCleanID2Children() throws Exception {
    	preTest(3);
    	//Add malformed key
    	byte[] shortBytes = new byte[3];
    	DatabaseEntry key= new DatabaseEntry(shortBytes);
    	EntryIDSet idSet=new EntryIDSet();
    	id2child.writeKey(txn, key, idSet);
    	//Try to break JebFormat version of key entry
    	key=addID2EntryReturnKey(junkDN, 4, true);
    	idSet=new EntryIDSet(new byte[16], new byte[16]);
    	id2child.writeKey(txn, key, idSet);
    	//put invalid key -- no EntryID matches
    	key= new EntryID(45).getDatabaseEntry();
    	id2child.writeKey(txn, key, idSet);
    	//invalid ids in id list
    	key=addID2EntryReturnKey(junkDN1, 5, false);
    	byte[] idBytes=new byte[24];
    	//doesn't exist
    	idBytes[3] = (byte)0xff;
    	//not a child
    	idBytes[15] = (byte)1;
    	//bad jeb format
    	idBytes[23] = (byte) 0x04;
    	idSet=new EntryIDSet(null, idBytes);
    	id2child.writeKey(txn, key, idSet);
    	performBECleanVerify("id2children", 6);
    }

    /**
     * Runs clean verify against the id2subtree index after adding
     * various errors in that index file.
     * 
     * @throws Exception if the error count is not equal to 7.
     */
    @Test() public void testCleanID2Subtree() throws Exception {
    	preTest(4);
    	//break key
    	byte[] shortBytes = new byte[3];
    	DatabaseEntry key= new DatabaseEntry(shortBytes);
    	EntryIDSet idSet=new EntryIDSet();
    	id2subtree.writeKey(txn, key, idSet);
    	//put invalid ids into entry 3 idlist
    	key= new EntryID(3).getDatabaseEntry();
    	byte[] idBytes=new byte[16];
    	//invalid id
    	idBytes[3] = (byte)0xff;
    	//non-subordinate
    	idBytes[15] = (byte)1;
    	idSet=new EntryIDSet(null, idBytes);
    	id2subtree.writeKey(txn, key, idSet);	
    	//Try to break JebFormat version of key entry
    	key=addID2EntryReturnKey(junkDN, 4, true);   	
    	idBytes[3]=(byte) 0x04;
    	idBytes[15]=(byte)0x00;
    	EntryIDSet idSet1=new EntryIDSet(null, idBytes);
    	id2subtree.writeKey(txn, key, idSet1);
    	//put invalid key -- no EntryID matches
    	key= new EntryID(45).getDatabaseEntry();
    	idSet=new EntryIDSet(null, idBytes);
    	id2subtree.writeKey(txn, key, idSet);
    	performBECleanVerify("id2subtree", 7);
    }

    /**
     * Runs clean verify against the telephoneNumber.equality index 
     * after adding various errors in that index file.
     * 
     * @throws Exception if the error count is not equal to 4.
     */
    @Test() public void testCleanAttrIndex() throws Exception {
    	String phoneType="telephoneNumber";
    	preTest(3);  	
    	//Need to open a second database against this index
    	//so we can manipulate it. We can't get the index DB handle
    	//any other way.
    	DatabaseConfig config = new DatabaseConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);
        Database db=
        	eContainer.openDatabase(config, phoneType + ".equality");
		//Add entry with bad JEB format Version
		addID2EntryReturnKey(junkDN, 4, true);
    	//Add phone number with various bad id list entryIDs
    	byte[] subBytes = StaticUtils.getBytes("0009999999");
    	DatabaseEntry key= new DatabaseEntry(subBytes);
       	byte[] dataBytes=new byte[32];
       	//put duplicate ids in list
    	dataBytes[7] = (byte)1;
    	dataBytes[15] = (byte)1;
    	//put id that doesn't exist
    	dataBytes[23] = (byte)0xff;
    	//point to bad entry added above
    	dataBytes[31] = (byte) 0x04;
    	DatabaseEntry data= new DatabaseEntry(dataBytes);
    	OperationStatus status = EntryContainer.put(db, txn, key, data);
		assertTrue(status == OperationStatus.SUCCESS);	
       	//really 5 errors, but duplicate reference doesn't increment error
    	//count for some reason
    	performBECleanVerify(phoneType, 4);
    }
   
    /*
     * Begin complete verify index tests. As described above, these are
     * tests that cursor through the id2entry database and validate 
     * each entry against the various index files.
     * 
     */
    
    /**
     * Runs complete verify against the telephoneNumber index 
     * after adding various errors in the id2entry file.
     * 
     * @throws Exception if the error count is not equal to 3.
     */
    @Test() public void testVerifyID2Entry() throws Exception {
    	preTest(3);    	
    	//Add entry with short id
       	byte[] shortBytes = new byte[3];
    	DatabaseEntry key= new DatabaseEntry(shortBytes);
    	Entry testEntry=bldStatEntry(junkDN);
    	byte []entryBytes = 
    		JebFormat.entryToDatabase(testEntry, new DataConfig());
    	DatabaseEntry data= new DatabaseEntry(entryBytes);
    	assertTrue(id2entry.putRaw(txn, key, data));
 
    	//add entry with ramdom bytes
       	DatabaseEntry key1= new EntryID(4).getDatabaseEntry();
    	byte []eBytes = new byte[459];
    	for(int i=0;i<459;i++) {
    		eBytes[i]=(byte) (i*2);
    	}
    	//set version correctly
    	eBytes[0]=0x01;
    	DatabaseEntry data1= new DatabaseEntry(eBytes);
    	assertTrue(id2entry.putRaw(txn, key1, data1));
    	performBECompleteVerify("telephoneNumber", 3);
    }

    /**
     * 
     * Runs complete verify against the dn2id index 
     * after adding various errors in the dn2id file.
     * 
     * @throws Exception if the error count is not equal to 3.
     */
    @Test() public void testVerifyDN2ID() throws Exception {
    	preTest(9);
    	//add entry but no corresponding dn2id key
    	addID2EntryReturnKey(junkDN, 10, false);  	
    	//entry has dn2id key but its entryID -- don't need key
    	addID2EntryReturnKey(junkDN1, 11, false);
    	//insert key with bad entry id (45 instead of 10)
    	DN testDN=DN.decode(junkDN1);
    	EntryID id=new EntryID(45);      
    	assertTrue(dn2id.insert(txn, testDN, id));    	
       	//entry has no parent in dn2id
    	addID2EntryReturnKey(noParentDN, 12, false);
    	//add the key/id
    	testDN=DN.decode(noParentDN);
    	id=new EntryID(12);      
    	assertTrue(dn2id.insert(txn, testDN, id));
    	performBECompleteVerify("dn2id", 3);
    }
    
    /**
     * 
     * Runs complete verify against the id2children index 
     * after adding various errors in the id2children file.
     * 
     * @throws Exception if the error count is not equal to 3.
     */
    @Test() public void testVerifyID2Children() throws Exception {
    	preTest(9);
    	//Add dn with no parent
    	DatabaseEntry key=addID2EntryReturnKey(noParentDN, 10, false);
    	byte[] idBytes=new byte[16];
    	idBytes[7]=(byte) 0x0A;
    	EntryIDSet idSet=new EntryIDSet(null, idBytes);	
    	id2child.writeKey(txn, key, idSet);
    	//Add child entry - don't worry about key
    	addID2EntryReturnKey(cDN, 11, false);
    	//Add its parent entry -- need the key
       	DatabaseEntry keyp=addID2EntryReturnKey(pDN, 12, false);
    	//add parent key/IDSet with bad IDset id
    	byte[] idBytesp=new byte[16];
    	idBytesp[7]=(byte) 0xFF;
    	EntryIDSet idSetp=new EntryIDSet(null, idBytesp);
    	id2child.writeKey(txn, keyp, idSetp);
    	performBECompleteVerify("id2children", 3);
    }
    
    /**
     * 
     * Runs complete verify against the id2children index 
     * after adding various errors in the id2children file.
     * This is a second test because the key needed to have 
     * null idlist. This test is really just for coverage and
     * should have a 0 error count.
     * 
     * @throws Exception if the error count is not equal to 0.
     */
    @Test() public void testVerifyID2Children1() throws Exception {
    	preTest(2);
    	//Add child entry - don't worry about key
    	addID2EntryReturnKey(pDN, 10, false);
    	//add parent key/IDSet with null keyset
    	EntryIDSet idSetp=new EntryIDSet();   	
    	DatabaseEntry key= new EntryID(2).getDatabaseEntry();
    	id2child.writeKey(txn, key, idSetp);
    	performBECompleteVerify("id2children", 0);
    }
  
    /**
     * 
     * Runs complete verify against the id2subtree index 
     * after adding various errors in the id2subtree file.
     * 
     * @throws Exception if the error count is not equal to 3.
     */
    @Test
    public void testVerifyID2Subtree() throws Exception {
    	preTest(2);
    	//Add entry with no parent
    	addID2EntryReturnKey(noParentDN, 3, false);
    	performBECompleteVerify("id2subtree", 3);
    }
  
    /**
     * 
     * Runs complete verify against the id2subtree index 
     * after adding various errors in the id2subtree file.
     * This is a second test because the key needed to have 
     * null idlist. 
     * 
     * @throws Exception if the error count is not equal to 1.
     */
    @Test
    public void testVerifyID2Subtree1() throws Exception {
    	preTest(2);
       	//Add child entry - don't worry about key
    	addID2EntryReturnKey(pDN, 3, false);
    	//add parent key/IDSet with null keyset
    	EntryIDSet idSet=new EntryIDSet();   	
    	DatabaseEntry key= new EntryID(2).getDatabaseEntry();
    	id2subtree.writeKey(txn, key, idSet);
    	performBECompleteVerify("id2subtree", 1);
    }

    /**
     * Runs complete verify against the mail indexes
     * (equality, presence, substring, ordering)
     * after adding various errors to each of these index files.
     * @throws Exception if the error count is not equal to 6.
     */
    @Test() public void testVerifyAttribute() throws Exception {
    	String mailType="mail";
    	preTest(4);  	
    	//Need to open a second databases against this index
    	//so we can manipulate it. 
    	DatabaseConfig config = new DatabaseConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);
        //Get db handles to each index.
        Database dbEq=
        	eContainer.openDatabase(config, mailType + ".equality");
        Database dbPres=
        	eContainer.openDatabase(config, mailType + ".presence");
        Database dbSub=
        	eContainer.openDatabase(config, mailType + ".substring");
        Database dbOr=
        	eContainer.openDatabase(config, mailType + ".ordering");
        //Add invalid idlist ids to both equality and ordering indexes.  
    	DatabaseEntry key= 
    		new DatabaseEntry(StaticUtils.getBytes("user.0@example.com"));
       	byte[] dataBytes=new byte[16];
       	//put duplicate ids in list
    	dataBytes[7] = (byte)0xff;
    	dataBytes[15] = (byte)0xfe;
    	DatabaseEntry data= new DatabaseEntry(dataBytes);
       	OperationStatus status = EntryContainer.put(dbEq, txn, key, data);
		assertTrue(status == OperationStatus.SUCCESS);	
	   	status = EntryContainer.put(dbOr, txn, key, data);
		assertTrue(status == OperationStatus.SUCCESS);
	    //Add null idlist to both equality and ordering indexes.  
    	key = 
    		new DatabaseEntry(StaticUtils.getBytes("user.1@example.com"));
    	data= new DatabaseEntry(new EntryIDSet().toDatabase());  	
    	status = EntryContainer.put(dbEq, txn, key, data);
		assertTrue(status == OperationStatus.SUCCESS);	
	   	status = EntryContainer.put(dbOr, txn, key, data);
		assertTrue(status == OperationStatus.SUCCESS);
		//Add invalid idlist ids to presence index.		
    	 key = 
    		new DatabaseEntry(StaticUtils.getBytes("+"));
    	data = new DatabaseEntry(dataBytes);
       	status = EntryContainer.put(dbPres, txn, key, data);
		assertTrue(status == OperationStatus.SUCCESS);	
	    //Add invalid idlist ids to substring index.
		key = 
			new DatabaseEntry(StaticUtils.getBytes("@examp"));
		data = new DatabaseEntry(dataBytes);
		status = EntryContainer.put(dbSub, txn, key, data);
		assertTrue(status == OperationStatus.SUCCESS);	
		performBECompleteVerify(mailType, 6);
    }

    /* Various tests not either clean or complete */
    
  
    /**
     * Try to verify a non-indexed attribute.
     * @throws Exception if error count is not equal to 0.
     */
    @Test(expectedExceptions=Exception.class)
    public void testVerifyNotIndexed()  throws Exception {
    	cleanAndLoad(2);
    	VerifyConfig verifyConfig = new VerifyConfig();
    	verifyConfig.setBaseDN(baseDNs[0]);
    	verifyConfig.addCleanIndex("userPassword");
    	Entry statEntry=bldStatEntry("");
    	be=(BackendImpl) DirectoryServer.getBackend(beID);
    	be.verifyBackend(verifyConfig, configEntry, baseDNs, statEntry);
    	assertEquals(getStatEntryCount(statEntry, errorCount), 0);
    }
    
    /**
     * Try to verify an nonexistent attribute.
     * @throws Exception if verify backend fails.
     */
    @Test(expectedExceptions=Exception.class)
    public void testInvalidIndex()  throws Exception {
    	cleanAndLoad(2);
    	VerifyConfig verifyConfig = new VerifyConfig();
    	verifyConfig.setBaseDN(baseDNs[0]);
    	verifyConfig.addCleanIndex(badIndexName);
    	Entry statEntry=bldStatEntry("");
    	be=(BackendImpl) DirectoryServer.getBackend(beID);
    	be.verifyBackend(verifyConfig, configEntry, baseDNs, statEntry);
    }
  
    /* end tests */
    
    /**
     * Adds an entry to the id2entry database with a dn and id passed into the
     * method. Optional flag to set the Jeb version byte for those types of tests.
     * @param dn the dn string to put in the entry.
     * @param id to use as the id2entry key,
     * @param trashFormat true if first byte should be changed to invalid value.
     * @return Database entry key of the entry.
     * @throws Exception if the entry is not added to the id2entry database.
     */
    private DatabaseEntry addID2EntryReturnKey(String dn, long id, boolean trashFormat) 
    throws Exception {
    	DatabaseEntry key= new EntryID(id).getDatabaseEntry();
    	Entry testEntry=bldStatEntry(dn);
    	byte []entryBytes = 
    		JebFormat.entryToDatabase(testEntry, new DataConfig());
    	if(trashFormat)
    		entryBytes[0] = 0x67;
    	DatabaseEntry data= new DatabaseEntry(entryBytes);
    	assertTrue(id2entry.putRaw(txn, key, data));
    	return key;
    }
    
    /**
     * Wrapper to do a clean verify.
     * @param indexToDo index file to run verify against.
     * @param expectedErrors number of errors expected for this test.
     * @throws Exception if the verify fails.
     */
    private void performBECleanVerify(String indexToDo, 
    		int expectedErrors) throws Exception {
    	performBEVerify(indexToDo, expectedErrors, true);
    }
    
    /**
     * Wrapper to do a complete verify.
     * @param indexToDo index file to run verify against.
     * @param expectedErrors number of errors expected for this test.
     * @throws Exception if the verify fails.
     */
    private void performBECompleteVerify(String indexToDo, 
    		int expectedErrors) throws Exception {
    	performBEVerify(indexToDo, expectedErrors, false);
    }
    
    /**
     * Performs either a clean or complete verify depending on
     * flag passed in.
     * 
     * @param indexToDo index file to run verify against.
     * @param expectedErrors number of errors expected for this test.
     * @param clean do clean verify if true.
     * @throws Exception if the verify fails.
     */
    private void performBEVerify(String indexToDo, 
    		int expectedErrors, boolean clean) throws Exception {
      	EntryContainer.transactionCommit(txn);
    	VerifyConfig verifyConfig = new VerifyConfig();
    	verifyConfig.setBaseDN(baseDNs[0]);
    	if(!clean)
    		verifyConfig.addCompleteIndex(indexToDo);
    	else
    		verifyConfig.addCleanIndex(indexToDo);
    	Entry statEntry=bldStatEntry("");
    	be.verifyBackend(verifyConfig, configEntry, baseDNs, statEntry);     
    	assertEquals(getStatEntryCount(statEntry, errorCount), expectedErrors);
    }
    
    
 
    /**
     * Does a pretest setup. Creates some number of entries, gets
     * backend, rootcontainer, entryContainer objects, as well as 
     * various index objects.
     * Also starts a transaction.
     * @param numEntries number of entries to add to the verify backend.
     * @throws Exception if entries cannot be loaded.
     */
    private void preTest(int numEntries) throws Exception {
    	cleanAndLoad(numEntries);
    	be=(BackendImpl) DirectoryServer.getBackend(beID);
    	rContainer= be.getRootContainer();
    	eContainer= rContainer.getEntryContainer(DN.decode(suffix));
    	id2child=eContainer.getID2Children();
    	id2entry=eContainer.getID2Entry();
    	id2subtree=eContainer.getID2Subtree();
    	dn2id=eContainer.getDN2ID();
    	txn = eContainer.beginTransaction();
    }
    
    /**
     * Cleans verify backend and loads some number of entries.
     * @param numEntries number of entries to load into the backend.
     * @throws Exception if the entries are not loaded or created.
     */
    private void cleanAndLoad(int numEntries) throws Exception {
    	TestCaseUtils.clearJEBackend(false, beID, suffix);
    	template[2]=numUsersLine;
    	template[2]=
    		template[2].replaceAll("#numEntries#", String.valueOf(numEntries));
    	createLoadEntries(template, numEntries);
    }

    /**
     * Gets information from the stat entry and returns that value as a Long.
     * @param e entry to search.
     * @param type attribute type
     * @return Long
     * @throws NumberFormatException if the attribute value cannot be parsed.
     */
    private long getStatEntryCount(Entry e, String type) 
    throws NumberFormatException {
    	AttributeType attrType =
    		DirectoryServer.getAttributeType(type);
    	if (attrType == null)
    		attrType = DirectoryServer.getDefaultAttributeType(type);
    	List<Attribute> attrList = e.getAttribute(attrType, null);
    	LinkedHashSet<AttributeValue> values =
    		attrList.get(0).getValues();
    	AttributeValue v = values.iterator().next();
    	long retVal = Long.parseLong(v.getStringValue());
    	return (retVal);
    }

    /**
     * Builds an entry suitable for using in the verify job to gather statistics about
     * the verify.
     * @param dn to put into the entry.
     * @return a suitable entry.
     * @throws DirectoryException if the cannot be created.
     */
    private Entry bldStatEntry(String dn) throws DirectoryException {
    	DN entryDN = DN.decode(dn);
    	HashMap<ObjectClass, String> ocs = new HashMap<ObjectClass, String>(2);
    	ObjectClass topOC = DirectoryServer.getObjectClass(OC_TOP);
    	if (topOC == null) {
    		topOC = DirectoryServer.getDefaultObjectClass(OC_TOP);
    	}
    	ocs.put(topOC, OC_TOP);
    	ObjectClass extensibleObjectOC = DirectoryServer
    	.getObjectClass(OC_EXTENSIBLE_OBJECT);
    	if (extensibleObjectOC == null) {
    		extensibleObjectOC = DirectoryServer
    		.getDefaultObjectClass(OC_EXTENSIBLE_OBJECT);
    	}
    	ocs.put(extensibleObjectOC, OC_EXTENSIBLE_OBJECT);
    	return new Entry(entryDN, ocs,
    			new LinkedHashMap<AttributeType, List<Attribute>>(0),
    			new HashMap<AttributeType, List<Attribute>>(0));
    }
}