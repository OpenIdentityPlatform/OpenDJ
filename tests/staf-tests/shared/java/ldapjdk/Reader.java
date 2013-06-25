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
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 */

import netscape.ldap.*;
import netscape.ldap.util.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.io.*;

class Reader extends Thread {

    BlockingQueue<Change> queue;
    ArrayList<Server> masters;
    
    public Reader(BlockingQueue<Change> q, ArrayList<Server> masters) {
        this.queue = q;
        this.masters = masters;
    }
    
    public void run() {
        // master number in the array list
        int masterN = 0;

        // ECL "draft" mode index
        int changeNumber = 0;

        // ECL "opends" mode index
        String eclCookie = EclReadAndPlay.INITIAL_COOKIE;


        try {
            Server master = null;
            LDAPConnection masterConnection = new LDAPConnection();
            LDAPSearchResults results = null;
            LDAPEntry entry = null;
            LDAPAttribute attr = null;
            int idleTime = 0;

            while (true) {
                try {
                    master = masters.get(masterN);
                    
                    // Connect to the Directory master
                    EclReadAndPlay.println("INFO", "Connecting to master " + master.host + ":" + master.port + " ......");
                    masterConnection.connect( master.host, master.port );
                    masterConnection.authenticate( 3, EclReadAndPlay.bindDn, EclReadAndPlay.bindPwd );
                    EclReadAndPlay.println("INFO", "...... Connected to master " + master.host + ":" + master.port );

                    // Set changenumber


                    // Try to retrieve the ECL index (changenumber|changelogcookie) of the last update read 
                    // ---> use the CSN stored in the file under "db" directory 
                    for ( CSN csn:  EclReadAndPlay.RUV.values() ) {
                        String filter = "(& (objectclass=changelogentry)(replicationCSN=" 
                                        + csn.getValue() + ") )";
                        results = masterConnection.search( "cn=changelog", LDAPv3.SCOPE_SUB, filter, 
                                                           new String[] {"changeNumber", "changeLogCookie"} ,
                                                           false );
                        entry = results.next();

                        if ( EclReadAndPlay.eclMode.equals("draft") ) {
                            if ( entry != null ) {
                                attr = entry.getAttribute("changeNumber");
                                if (attr != null) {
                                    String changeNumberString = attr.getStringValueArray()[0];
                                    EclReadAndPlay.println("DEBUG", "Found changeNumber " + changeNumberString 
                                                 + " for csn " + csn.getValue() );

                                    int c = Integer.parseInt(changeNumberString);
                                    if ( ( changeNumber == 0 ) || ( changeNumber > c ) ) {
                                        EclReadAndPlay.println("DEBUG", "Setting changeNumber to " + ++c );
                                        changeNumber = c;
                                    }
                                } else {
                                    EclReadAndPlay.println("WARNING", "Cannot find changenumber, setting it to 1");
                                    changeNumber = EclReadAndPlay.INITIAL_CHANGENUMBER;
                                }
                            } else {
                                EclReadAndPlay.println("WARNING", "Cannot find a changelog entry for csn " + csn );
                                EclReadAndPlay.println("WARNING", "Will start from the first changelog entry");
                            
                                results = masterConnection.search( "", LDAPv3.SCOPE_BASE, "(objectclass=*)", 
                                                                   new String[]{"firstChangeNumber"} , false );
                                entry = results.next();
                                attr = entry.getAttribute("firstChangeNumber");
                                if ( attr != null ) {
                                    String changeNumberString = attr.getStringValueArray()[0];
                                    EclReadAndPlay.println("DEBUG", "Found firstChangeNumber " + changeNumberString);

                                    int c = Integer.parseInt(changeNumberString);
                                    if ( ( changeNumber == 0 ) || ( changeNumber > c ) ) {
                                        EclReadAndPlay.println("DEBUG", "Setting changeNumber to " + c );
                                        changeNumber = c;
                                    }
                                } else {
                                    EclReadAndPlay.println("WARNING", "Cannot find firstChangeNumber, setting it to 1");
                                    changeNumber = EclReadAndPlay.INITIAL_CHANGENUMBER;
                                }
                            }
                        } else if ( EclReadAndPlay.eclMode.equals("opends") ) {
                            if ( entry != null ) {
                                attr = entry.getAttribute("changeLogCookie");
                                if ( attr!= null ) {
                                    eclCookie = attr.getStringValueArray()[0];
                                    EclReadAndPlay.println("DEBUG", "Found changeLogCookie " + eclCookie 
                                                 + " for csn " + csn.getValue() );
                                } else {
                                    EclReadAndPlay.println("WARNING", "Cannot find a changelog entry for csn " + csn );
                                    EclReadAndPlay.println("WARNING", "Will start from the first changelog entry");
                                    eclCookie = EclReadAndPlay.INITIAL_COOKIE;
                                }
                            } else {
                                EclReadAndPlay.println("WARNING", "Cannot find a changelog entry for csn " + csn );
                                EclReadAndPlay.println("WARNING", "Will start from the first changelog entry");
                                eclCookie = EclReadAndPlay.INITIAL_COOKIE;
                            }
                        }
                    } /* for (CSN csn: ...) */

                    synchronized (EclReadAndPlay.lock) {
                        EclReadAndPlay.lock.notifyAll();
                    }
                    
                    String[] attributes = new String[] {"replicationCSN", "replicaIdentifier", "targetDN",
                                                        "targetEntryUUID", "changeType", "changes",
                                                        "deleteOldRDN", "newRDN", "newSuperior",
                                                        "changeNumber", "changeHasReplFixupOp",
                                                        "changeLogCookie"};

                    while (idleTime < EclReadAndPlay.MAX_IDLE_TIME) {
                        if ( EclReadAndPlay.eclMode.equals("draft") ) {
                            int limit = changeNumber + (EclReadAndPlay.queueSize - 1);

                            String filter = "(& (changeNumber>=" + changeNumber + ")(changeNumber<=" 
                                            + limit + ") )";

                            EclReadAndPlay.println("DEBUG", "Getting changes " + changeNumber + " to " + limit);
                            results = masterConnection.search("cn=changelog", LDAPv3.SCOPE_SUB, filter,
                                                              attributes , false );

                        } else if ( EclReadAndPlay.eclMode.equals("opends") ) {
                            // --control "1.3.6.1.4.1.26027.1.5.4:false:;"
                            String filter = "changetype=*";
                            LDAPSearchConstraints controls = new LDAPSearchConstraints();
                            LDAPControl eclControl = new LDAPControl("1.3.6.1.4.1.26027.1.5.4", false,
                                                                     eclCookie.getBytes());
                            controls.setMaxResults(199);
                            controls.setServerControls(eclControl);

                            EclReadAndPlay.println("DEBUG", "Getting changes from cookie:  " + eclCookie);
                            results = masterConnection.search("cn=changelog", LDAPv3.SCOPE_SUB, filter,
                                                              attributes , false, controls );
                        }


                        if ( ! results.hasMoreElements() ) {
                            // No new change found in retrocl => sleep 100 ms.
                            sleep(100);
                            idleTime += 100;
                            EclReadAndPlay.println("DEBUG", "No new change found in ECL => have slept for 100ms");
                        } else {
                            idleTime = 0;

                            // Forward  all the results found to the application
                            while ( results.hasMoreElements() ) {
                                EclReadAndPlay.println("DEBUG", "Going through change entries found in the ECL.");
                                try {
                                   entry = results.next();
                                } catch (LDAPException ldapEx) {
                                   if ( ldapEx.getLDAPResultCode() == LDAPException.SIZE_LIMIT_EXCEEDED )
                                      continue;
                                   else 
                                      throw ldapEx;
                                }
                                //EclReadAndPlay.println("DEBUG", "Changelog entry: " + entry.toString());
                                try {
                                    // Write the change in the queue
                                    Change change = new Change(entry);
                                    queue.put(change);
                                } catch (Exception e) {
                                    EclReadAndPlay.println("DEBUG", "Ignoring change " + entry.getDN() );
                                    if ( EclReadAndPlay.eclMode.equals("draft") )
                                        EclReadAndPlay.inc_ignored(changeNumber);
                                    else if ( EclReadAndPlay.eclMode.equals("opends") )
                                        EclReadAndPlay.inc_ignored(eclCookie);
                                }

                                if ( EclReadAndPlay.eclMode.equals("draft") ) {
                                    changeNumber++;
                                    EclReadAndPlay.println("DEBUG", "change=" + entry.getDN() + ", changenumber = " 
                                                 + changeNumber + ", count =" + results.getCount());
                                } else if ( EclReadAndPlay.eclMode.equals("opends") ) {
                                    attr = entry.getAttribute("changeLogCookie");
                                    if ( attr != null ) {
                                       eclCookie = attr.getStringValueArray()[0];
                                       EclReadAndPlay.println ("DEBUG", " ECL cookie value ========>  " + eclCookie );
                                    }
                                }
                            } /* while (result.hasMoreElements()) */
                            
                        }

                        if ( EclReadAndPlay.displayMissingChanges == true ) {
                            if ( EclReadAndPlay.eclMode.equals("draft") ) {
                                results = masterConnection.search( "", LDAPv3.SCOPE_BASE, "(objectclass=*)", 
                                                                   new String[]{"lastChangeNumber"} , false );
                                entry = results.next();
                                attr = entry.getAttribute("lastChangeNumber");
                                if ( attr != null ) {
                                    EclReadAndPlay.lastChangeNumber = Integer.parseInt(attr.getStringValueArray()[0]);
                                }
                            } else if ( EclReadAndPlay.eclMode.equals("opends") ) {
                                results = masterConnection.search( "", LDAPv3.SCOPE_BASE, "(objectclass=*)", 
                                        new String[]{"lastExternalChangelogCookie"} , false );
                                entry = results.next();
                                attr = entry.getAttribute("lastExternalChangelogCookie");
                                if ( attr != null ) {
                                    EclReadAndPlay.lastExternalChangelogCookie = attr.getStringValueArray()[0];
                                }
                            }
                        }
                        
                    } /* while (idleTime <= EclReadAndPlay.MAX_IDLE_TIME) */

                    EclReadAndPlay.println("WARNING", "No new changes read in the ECL for " + Integer.toString(idleTime) + 
                                 " milliseconds. ======> EXIT");
                    System.exit(0);
                } catch( LDAPException e ) {

                    int errorCode = e.getLDAPResultCode(); 

                    // if server is down => switch
                    if ( ( errorCode == 91 ) || ( errorCode == 81 ) || ( errorCode == 80 ) ) {
                        // clear the queue of changes
                        queue.clear();
                        EclReadAndPlay.println( "WARNING", "Connection lost to " + master.host + ":" + master.port + ".");
                        masterN = (masterN+1) % masters.size();
                    } else {
                        EclReadAndPlay.println( "ERROR" , e.toString() );
                        e.printStackTrace();
                        System.exit(1);
                    }
                }
            } /* while (true) */

        } catch (Exception e) {
             e.printStackTrace();
             System.exit(1);
        }
    }

}
