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

class Writer extends Thread {

    BlockingQueue<Change> q;
    String hostport;
    
    public Writer(BlockingQueue<Change> q, String hostport) {
        this.q = q;
        this.hostport = hostport;
    }
    
    public void run() {
        try {
            Server application = new Server( hostport );
            ImprovedLDAPConnection applicationConnection = new ImprovedLDAPConnection();

            // Connect to the stand-alone server
            EclReadAndPlay.println("INFO", "****** Connecting to application " 
                         + application.host + ":" + application.port + " ......");
            applicationConnection.connect( application.host, application.port );
            applicationConnection.authenticate( 3, EclReadAndPlay.bindDn, EclReadAndPlay.bindPwd );
            EclReadAndPlay.println("INFO", "****** ...... Connected to application " 
                         + application.host + ":" + application.port );

            while (true) {
                // Read change from the queue
                Change change = q.take();
                //EclReadAndPlay.println ("DEBUG", "Change read from the queue -----> : " + change.toString() );

                CSN RUVcsn=EclReadAndPlay.RUV.get(change.replicaIdentifier);
                if ( RUVcsn != null ) {                    
                    // if operation is not replicated
                    if ( change.csn == null ) 
                        continue;

                    if (change.csn.compareTo(RUVcsn) < 0) {
                        // EclReadAndPlay.println ("DEBUG", Integer.toHexString(i.intValue()) + " < " + Integer.toHexString(l.intValue()) );
                        EclReadAndPlay.println("DEBUG", "Operation " + change.changeNumberValue + " csn " 
                                     +  change.csn + " has already been replayed");
                        continue;
                    }
                }
                
                try {                    
                    // Write change on stand-alone server
                    applicationConnection.apply(change);

                    // Write change CSN to file under "db" directory
                    File f;
                    if (EclReadAndPlay.files.containsKey(change.replicaIdentifier)) {
                        f = EclReadAndPlay.files.get(change.replicaIdentifier);
                        // f.renameTo(new File(EclReadAndPlay.dbPath, new String(change.replicaIdentifier+".tmp") ));
                    } else {
                        f = new File(EclReadAndPlay.dbPath, change.replicaIdentifier + ".csn");
                        EclReadAndPlay.files.put(change.replicaIdentifier,f);
                    }
                    
                    FileWriter out = new FileWriter(f);
                    out.write(change.csn.value);
                    out.flush();
                    out.close();

                    EclReadAndPlay.RUV.put(change.replicaIdentifier,change.csn);

                    if ( EclReadAndPlay.eclMode.equals("draft") )
                        EclReadAndPlay.inc_ops(change.changeNumber);
                    else if ( EclReadAndPlay.eclMode.equals("opends") )
                        EclReadAndPlay.inc_ops(change.changelogCookie);
                    
                    // Log a message for the written change on "logs/access" file
                    EclReadAndPlay.accessOut.println(EclReadAndPlay.getDate() 
                                   + "- INFO: " + change.type + " \"" 
                                   + change.dn + "\" (" + change.csn +" / " 
                                   + change.changeNumber + ")" );
                } catch (Exception e) {
                    EclReadAndPlay.println( "ERROR", e.toString() );
                    e.printStackTrace();
                    System.exit(1);
                }
                   
                    //nb_changes++;

            }
        } catch (Exception e) {
             e.printStackTrace();
             System.exit(1);
        }
    }
       
}
