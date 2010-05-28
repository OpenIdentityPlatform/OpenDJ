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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 */

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.Enumeration;
import netscape.ldap.LDAPAttribute;
import netscape.ldap.LDAPAttributeSet;
import netscape.ldap.LDAPConnection;
import netscape.ldap.LDAPEntry;
import netscape.ldap.LDAPException;
import netscape.ldap.LDAPSearchConstraints;
import netscape.ldap.LDAPSearchResults;
import netscape.ldap.LDAPv3;
import netscape.ldap.controls.LDAPPersistSearchControl;


public class PSearchOperations extends Thread {

    public static final String ADD = "ADD";
    public static final String MODIFY = "MODIFY";
    public static final String DELETE = "DELETE";
    public static final String MODDN = "MODDN";
    public static final String ALL = "ALL";
    private LDAPConnection connection;
    private String hostname;
    private int portnumber;
    private String bindDN;
    private String bindPW;
    private String suffix;
    private int threadId;
    private String fileName;
    private boolean output;
    private boolean ldifFormat;
    private boolean logFile;
    private String operation;
    /**
     * constructor
     * @param id
     * @param hostname
     * @param portnumber
     * @param bindDN
     * @param bindPW
     * @param suffix
     */
    public PSearchOperations(int id, String hostname, int portnumber, String bindDN, String bindPW, String suffix) {
        this.hostname = hostname;
        this.portnumber = portnumber;
        this.bindDN = bindDN;
        this.bindPW = bindPW;
        this.suffix = suffix;
        this.threadId = id;
        this.output = false;
        this.logFile = false;
        this.ldifFormat = false;
        //by default all operation
        this.operation = ALL;

    }
    /**
     * to use systeme.out
     * @param output boolean
     */
    public void setOutput(boolean output) {        
        this.output = output;
    }
    /**
     * to use the log file
     * @param logFile boolean
     */
    public void useLogFile(boolean logFile) { 
        this.logFile = logFile;
    }
    /**
     * to define the log file and URI
     * @param file String
     */
    public void setLogFile(String file) {        
        //if there one thread the thread id are not add in the file name
        this.fileName = file;
        //in multy thread for each thread the thread id are add in the file name
        if (threadId!=0) {
            String ext = file.substring(file.lastIndexOf("."), file.length());
            this.fileName = file.substring(0, file.lastIndexOf(".")) + threadId + ext;
        }
        //delete old log file if logFile is present and enable
        File fileToDelete = new File(fileName);
        if (fileToDelete.isFile() && logFile) {
            fileToDelete.delete();
        }
    }
    /**
     * to define the PSearch operation
     * @param operation String
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    public void setLdifFormat(boolean ldifFormat) {
        this.ldifFormat = ldifFormat;
    }

    /**
     *Connect to server.
     */
    private void connect() {
        try {
            connection = new LDAPConnection();            
            connection.connect(3, hostname, portnumber, "", "");
            connection.authenticate(3, bindDN, bindPW);
            if(!ldifFormat)
              write("[Thread id: " + threadId + "] \n" + getDate() + connection);
        } catch (LDAPException ex) {
            System.out.println("[Thread id: " + threadId + "] Connection :" + ex.getMessage());
            System.exit(1);
        }
    }
    /**
     * to instanciate new LDAPPersistSearchControl
     * @return LDAPPersistSearchControl
     */
    private LDAPPersistSearchControl PSearchControl() {
        int op = 0;
        if (operation.equals(ALL)) {
            op = LDAPPersistSearchControl.ADD |
                    LDAPPersistSearchControl.MODIFY |
                    LDAPPersistSearchControl.DELETE |
                    LDAPPersistSearchControl.MODDN;
        } else if (operation.equals(ADD)) {
            op = LDAPPersistSearchControl.ADD;
        } else if (operation.equals(MODIFY)) {
            op = LDAPPersistSearchControl.MODIFY;
        } else if (operation.equals(DELETE)) {
            op = LDAPPersistSearchControl.DELETE;
        } else if (operation.equals(MODDN)) {
            op = LDAPPersistSearchControl.MODDN;
        }

        boolean changesOnly = true;
        boolean returnControls = true;
        boolean isCritical = true;

        LDAPPersistSearchControl persistCtrl =
                new LDAPPersistSearchControl(
                op,
                changesOnly,
                returnControls,
                isCritical);
        return persistCtrl;
    }
    /**
     * LDAP Search
     * @return LDAPSearchResults
     */
    public LDAPSearchResults LDAPSearch() {
        LDAPSearchResults res = null;
        try {
            LDAPPersistSearchControl persistCtrl = PSearchControl();
            LDAPSearchConstraints cons = connection.getSearchConstraints();
            cons.setBatchSize(1);
            cons.setServerControls(persistCtrl);
            // Start the persistent search.
            res = connection.search(suffix, LDAPv3.SCOPE_SUB, "(objectclass=*)", null, false, cons);
        } catch (LDAPException ex) {
            System.out.println("[Thread id: " + threadId + "] LDAPSearch :" + ex.getMessage());
            System.exit(1);
        }
        return res;
    }
    /**
     * return the date and time
     * @return String
     */
    public static String getDate() {
        // Initialize the today's date string
        String DATE_FORMAT = "yyyy/MM/dd:HH:mm:ss";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(DATE_FORMAT);
        Calendar c1 = Calendar.getInstance(); // today
        return ("[" + sdf.format(c1.getTime()) + "]");
    }
    /**
     *
     * @param b byte off control operation
     * @return
     */
    public String controlName(byte b) {
        String control;
        switch (b) {
            case LDAPPersistSearchControl.ADD:
                control = "ADD";
                break;
            case LDAPPersistSearchControl.DELETE:
                control = "DELETE";
                break;
            case LDAPPersistSearchControl.MODDN:
                control = "MODDN";
                break;
            case LDAPPersistSearchControl.MODIFY:
                control = "MODIFY";
                break;
            default:
                control = String.valueOf(b);
                break;
        }
        return control;
    }
    /**
     * to write on the log file or to use syteme out
     * @param msg String
     */
    public void write(String msg) {
        if (output) {
            System.out.println(msg);
        }
        if (logFile) {
            FileWriter aWriter = null;
            try {
                aWriter = new FileWriter(fileName, true);
                aWriter.write(msg + "\n");
                aWriter.flush();
                aWriter.close();
            } catch (IOException ex) {
                System.out.println("[Thread id: " + threadId + "]Write :" + ex.getMessage());
            } finally {
                try {
                    aWriter.close();
                } catch (IOException ex) {
                    System.out.println("[Thread id: " + threadId + "]Write :" + ex.getMessage());
                }
            }
        }
    }
    /**
     * run thread methode
     */
    public void run() {
        connect();
        LDAPSearchResults result = LDAPSearch();
        while (result.hasMoreElements() && connection.isConnected()) {
            byte[] arr = result.getResponseControls()[0].getValue();
            LDAPEntry entry = (LDAPEntry) result.nextElement();
            LDAPAttributeSet attrSet = entry.getAttributeSet();
            Enumeration attrs = attrSet.getAttributes();
            if (entry.getDN().contains("break")) {
                String message = "\n[Thread id: " + threadId + "] " + getDate() + " [BREAK]";
                if(!ldifFormat)
                  write(message);
                System.exit(0);
            } else if (entry.getDN().contains("stop")) {
                try {
                    connection.disconnect();
                    String message = "\n[Thread id: " + threadId + "] " + getDate() + "[STOP]";
                    if(!ldifFormat)
                      write(message);
                    System.exit(0);
                } catch (LDAPException ex) {
                    System.out.println("[Thread id: " + threadId + "]run :" + ex.getLDAPErrorMessage());
                }
            }
            String message = "[Thread id: " + threadId + "] " + getDate() + " [" + controlName(arr[4]) + "]";
           if(!ldifFormat)
             write("\n" + message);
           else
             write("\n");
            String dn = "dn: " + entry.getDN();
            write(dn);
            while (attrs.hasMoreElements()) {
                LDAPAttribute attr = (LDAPAttribute) attrs.nextElement();
                String name = attr.getName();
                Enumeration values = attr.getStringValues();
                while (values.hasMoreElements()) {
                    String attribute = name + ": " + values.nextElement();
                    write(attribute);
                }
            }
        }
        if (!connection.isConnected()) {
            String message = "\n[Thread id: " + threadId + "] " + getDate() + "[CONNECTION CLOSE]";
            write(message);
        }
    }
}
