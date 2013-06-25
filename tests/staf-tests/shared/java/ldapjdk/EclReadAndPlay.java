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
import java.io.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

class EclReadAndPlay {
   
    // dbPath --> stores files containing csn of identifiers
    static final String dbPath = "."; /*"db";*/
    // configPath --> stores masters config file
    static final String configPath = "."; /*"config";*/
    static final String mastersFilename = "masters";
    static final String logsPath = "."; /*"logs";*/
    static final String accessFilename = "access";

    // Maximum time (in milliseconds) without update being read from the master: 60 s
    static final int MAX_IDLE_TIME = 60000;

    // ECL "draft" mode -- Initial changeNumber
    static final int INITIAL_CHANGENUMBER = 1;
    // ECL "opends" mode -- Initial control value:
    // --control "1.3.6.1.4.1.26027.1.5.4:false:;" ==> first cookie: ";"
    static final String INITIAL_COOKIE = ";";


    static PrintWriter standardOut = null;
    static PrintWriter accessOut = null;
    static HashMap<String,CSN> RUV;
    static Object lock;
    static HashMap<String,File> files;
    static int nb_ops = 0;
    static int total_nb_ops = 0;
    static int nb_ignored = 0;
    static int changeNumber = 0;
    static int lastChangeNumber = 0;
    static String changelogCookie = null;
    static String lastExternalChangelogCookie = null; 
    static int missingChanges = 0;
    static int lastMissingChanges = 1;
    
    static String eclMode = null;
    static int queueSize = 0;
    static String bindDn = null;
    static String bindPwd = null;
    static boolean displayMissingChanges = false;
    static String outputFilename = null;
    
    public static void main( String[] args )

   {

        
        FileWriter out = null;
                
        files = new HashMap<String,File>();
        
        // Load latest read CSN values from files in db/ directory
        File csnDir = new File(dbPath);
        RUV = new HashMap<String,CSN>();
        try {
        	FilenameFilter csnFileFilter = new FilenameFilter() { 
        	    public boolean accept(File dir, String name) { 
        		    return name.endsWith(".csn"); 
        		}
        	}; 
            File[] csnFiles = csnDir.listFiles(csnFileFilter);
            if ( csnFiles != null ) {
                for (File f: csnFiles) {
                	String csnFilename = f.getName();
                    String id = 
                        csnFilename.substring(0, csnFilename.indexOf(".csn"));
                    BufferedReader in = new BufferedReader (new FileReader(f));
                    CSN mycsn = new CSN(in.readLine());
                    if ( mycsn.value == null ) 
                        mycsn = new CSN("00000000000000000000");
                    // System.out.println(files[i] + "\t" + mycsn);
                    RUV.put(id, mycsn);

                    files.put(id, f);
                }
            }
        } catch (IOException e) {
            println("ERROR", e.toString());
            e.printStackTrace();
            System.exit(1);
        }
                

        /********** Parse arguments **********/
        int masterN=0;
        String hostport = null;
        ArrayList<Server> masters = new ArrayList<Server>();
        
        for (int k = 0; k < args.length; k++) {
            String opt = args[k];
            String val = args[k+1];

            // ECL mode: "opends" or "draft"
            if (opt.equals("-m")) {
                eclMode = val;
            } 

            // Queue size
            if (opt.equals("-q")) {
                queueSize = Integer.parseInt(val);
            }

            // Display missing changes?
            if (opt.equals("-x")) {
            	if ( val.equals("true") )
                    displayMissingChanges = true;
            	else
            		displayMissingChanges = false;
            }
            
            // Bind DN
            if (opt.equals("-D")) {
                bindDn = val;
                System.out.println(".......... bindDN: " + bindDn);
            }

            // Bind password
            if (opt.equals("-w")) {
                bindPwd = val;
                System.out.println(".......... bindPwd: " + bindPwd);
            }


            // Stand-alone server:port
            if (opt.equals("-s")) {
                hostport = val;
                System.out.println(".......... stand-alone server: " + hostport);        
            }


            // Replicated masters
            if (opt.equals("-p")) {
                masters.add(new Server(val));
            }
            
            
            // Standard output file
            if (opt.equals("-o")) {
                outputFilename = val;
            }
            
            k++;
        } /* for() */


        if ( eclMode == null || queueSize == 0 || bindDn == null || 
             bindPwd == null || hostport == null || masters.size() == 0 ||
             outputFilename == null ) {
            System.out.println("usage: -m {draft|opends} -q {queue size} "
                               + "-x {(displayMissingChanges):true|false} "
                               + "-o {outputFilename} -D {bindD} -w {bindPwd} "
                               + "-s {standalone-host:port} "
                               + "-p {master1-host:port} "
                               + "-p {master2-host:port}...");
            System.exit(1);
        }

        /* try {
            File mastersFile= new File(configPath, mastersFilename);
            LineNumberReader in=new LineNumberReader (new FileReader(mastersFile) );
            String line;
            while ( in.ready() ) {
                line=in.readLine();
                masters.add(new Server(line));
            }
        } catch (IOException e) {
            println ("ERROR", e.toString());
            System.exit(1);
        } */
        
        masters.trimToSize();
        // System.out.println(masters);

        /*************************************/


        // Output file (logs are appended)
        try {
            standardOut = new PrintWriter(new BufferedWriter(new FileWriter( new File(outputFilename)) ) );
        } catch (IOException e) {
            println ("ERROR", e.toString() );
            e.printStackTrace();
            System.exit(1);
        }
        
        
        // Access log (data is appended)
        try {
            accessOut = new PrintWriter(new BufferedWriter(new FileWriter( new File(logsPath, accessFilename)) ) );
        } catch (IOException e) {
            println ("ERROR", e.toString() );
            e.printStackTrace();
            System.exit(1);
        }
        
        
        
        
        /********** Initialise reader/writer threads **********/
        // Create a bounded blocking queue of integers
        BlockingQueue<Change> queue = new ArrayBlockingQueue<Change>(queueSize);

        // Initialise reader thread --> read updates from replicated master        
        Reader reader = new Reader(queue, masters);
        reader.start();

        // Initialise writer thread --> write updates onto stand-alone server
        Writer writer = new Writer(queue, hostport);
        writer.start();

        lock = new Object();
        synchronized (lock) {
            try {
              lock.wait();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        long start = System.currentTimeMillis();
        int i=0;
        while (true) {
            int loopPeriod = 10; /* 10 s */
            sleep(loopPeriod * 1000);
            total_nb_ops += nb_ops;
            long duration = ((System.currentTimeMillis() - start)/1000);
            println("INFO", "Replayed " + nb_ops/loopPeriod 
            		+ " ops/sec. (Avg = " + (total_nb_ops/duration)
                    + " ops/s, Total = " + total_nb_ops 
                    + " , ignored = " + nb_ignored + " )");
            nb_ops = 0;
            if ( i++ == 3 && displayMissingChanges == true ) {
                if ( eclMode.equals("draft") ) {
                    missingChanges = lastChangeNumber - changeNumber;
                    float percentage = (lastMissingChanges - missingChanges);
                    println("INFO", "Current changeNumber = " + changeNumber 
                            + ", lastChangeNumber = " + lastChangeNumber 
                            + ", missing changes = " + missingChanges + "/" 
                            + lastMissingChanges + " (" + percentage + ")");
                    lastMissingChanges = missingChanges;
                    if (lastMissingChanges == 0)
                        lastMissingChanges = 1;
                } else if ( eclMode.equals("opends") ) {
                    println("INFO", "Current changelogCookie = " 
                    		+ changelogCookie 
                            + ", lastExternalChangelogCookie = " 
                            + lastExternalChangelogCookie);
                }
                i = 0;
            }
        }
        
    }
    
     public static void inc_ops(int c) {
        nb_ops++;
        changeNumber = c;	
    }
   
     public static void inc_ops(String c) {
         nb_ops++;
         changelogCookie = c;	
     }
     
    public static void inc_ignored(int c) {
        nb_ignored++;
        changeNumber = c;
    }

    public static void inc_ignored(String c) {
        nb_ignored++;
        changelogCookie = c;
    }

    public static String getDate() {
    
        // Initialize the today's date string
        String DATE_FORMAT = "yyyy/MM/dd:HH:mm:ss";
        java.text.SimpleDateFormat sdf = 
            new java.text.SimpleDateFormat(DATE_FORMAT);
        Calendar c1 = Calendar.getInstance(); // today
        return("[" + sdf.format(c1.getTime()) + "]");
   }
   
   public static void println(String level, String msg) {
        standardOut.println (getDate() + " - " + level + ": " + msg );
   }
   
   public static void sleep(int time) {
        try {  
            Thread.sleep(time);
        }
        catch ( InterruptedException e )
        {
             println( "ERROR" ,  e.toString() );
        }
    }
}
