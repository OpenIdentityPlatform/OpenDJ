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
import java.util.ArrayList;

class Change {
    int changeNumber = 0;
    String changelogCookie = null;
    CSN csn;
    String type = "";
    String dn = "";
    ArrayList<String> changes = new ArrayList<String>(2);
    String change = "";
    String replicaIdentifier = null;
    String changeNumberValue = null;
    String nsUniqueId = "";
    boolean deleteOldRDN = false;
    String newRDN = null;
    String newSuperior = null;
    
    
    public Change(LDAPEntry entry) throws Exception {
        
        
        LDAPAttribute attr = entry.getAttribute("replicaIdentifier");
        if ( attr == null ) {
            throw new Exception("No value found  for replicaIdentifier");
        }
        replicaIdentifier = attr.getStringValueArray()[0];
        

        attr = entry.getAttribute("changeNumber");
        if ( attr == null ) {
            throw new Exception("No value found  for changeNumber");
        }
        changeNumberValue = attr.getStringValueArray()[0];
        changeNumber = Integer.parseInt(changeNumberValue);

        attr = entry.getAttribute("changelogCookie");
        if ( attr != null ) {
        	changelogCookie = attr.getStringValueArray()[0];
        }
        
        
        attr = entry.getAttribute("replicationCSN");
        if ( attr == null ) {
            throw new Exception("No value found  for replicationCSN");
        }
        csn = new CSN(attr.getStringValueArray()[0]);
        
        attr = entry.getAttribute("targetDN");
        if ( attr == null ) {
            throw new Exception("No value found  for targetDN");
        }
        dn = attr.getStringValueArray()[0];

        attr = entry.getAttribute("changeType");
        if ( attr == null ) {
            throw new Exception("No value found  for changeType");
        }
        type = attr.getStringValueArray()[0];
        
        
//        attr = entry.getAttribute("targetUniqueId");
//        if ( attr == null ) {
//            throw new Exception("No value found  for targetUniqueId");
//        }
//        nsUniqueId=attr.getStringValueArray()[0];
        attr = entry.getAttribute("targetEntryUUID");
        if ( attr == null ) {
            throw new Exception("No value found  for targetEntryUUID");
        }
        nsUniqueId = attr.getStringValueArray()[0];

        
        // modrdn
        if ( type.equals("modrdn") ) {
            attr = entry.getAttribute("deleteOldRDN");
            if ( attr == null ) {
                throw new Exception("No value found  for deleteOldRDN");
            }
            deleteOldRDN = Boolean.getBoolean(attr.getStringValueArray()[0]);

            attr = entry.getAttribute("newRDN");
            if ( attr == null ) {
                throw new Exception("No value found  for newRDN");
            }
            newRDN = attr.getStringValueArray()[0];
            
            attr = entry.getAttribute("newSuperior");
            if ( attr != null ) {
                newSuperior=attr.getStringValueArray()[0];
            }
        }
        
        // Conflict
        attr = entry.getAttribute("changeHasReplFixupOp");
        if ( attr != null ) {
            change = attr.getStringValueArray()[0];
            if ( change.trim().endsWith("-") ) {
                change = change.substring(0, change.length()-3) + "\r\n";
            }
            String changeHasReplFixupOp=change.replaceFirst("targetDn", "dn") + "\r\n";

            // println ("INFO", "FixupOp (csn="+ csn+"):\n" + changeHasReplFixupOp);
            changes.add(changeHasReplFixupOp);
        }

        attr = entry.getAttribute("changes");
        if ( attr != null ) {
            change = attr.getStringValueArray()[0];
            if ( change.trim().endsWith("-") ) {
                change = change.substring(0, change.length()-3) + "\r\n";
            }
        }
        
        
        if ( type.equals("modify") && ( change.equals("") ) ) {
            throw new Exception("Attribute changes is empty - replicationCSN="+ csn);
            //EclReadAndPlay.accessOut.println (getDate() + "- WARNING: Ignore change csn=" + csn );
        }
        
        String myChange = "dn: " + dn + "\n" +
                        "changetype: " + type + "\n" +
                        change +"\n";

        changes.add(myChange);
        
        changes.trimToSize();
        
    }
    
    
    public String toString() {
        return ("change number " + changeNumber + " (csn="+csn +")");
    }
}
