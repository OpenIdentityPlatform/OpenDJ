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

public class ImprovedLDAPConnection extends LDAPConnection {

    public ImprovedLDAPConnection() {
        super();
    }
    
    public void apply (Change change) {
        
        for (String mychange: change.changes) {
            String mytype = null;                
            // Parse LDIF content
            ByteArrayInputStream stream = new ByteArrayInputStream(mychange.getBytes());
            LDIF ldif = null;
            try {
                ldif = new LDIF(new DataInputStream(stream));
                LDIFContent content = ldif.nextRecord().getContent();

                //EclReadAndPlay.println ("DEBUG", "\n\nWriting the following update: \n" + content.toString() );
                switch (content.getType()) {
                    case LDIFContent.ADD_CONTENT:
                        mytype = "ADD";
                        content = ( LDIFAddContent ) content;

                        LDAPAttributeSet attrSet = new LDAPAttributeSet( ((LDIFAddContent)content).getAttributes());
                        // remove non-user-modifiable attributes:
                        // entryuuid, pwdchangedtime, creatorsname, createtimestamp
                        LDAPAttribute entryuuidAttr = attrSet.getAttribute("entryuuid");
                        if ( entryuuidAttr != null ) {
                            attrSet.remove("entryuuid");
                        }
                        LDAPAttribute pwdchangedAttr = attrSet.getAttribute("pwdChangedTime");
                        if ( entryuuidAttr != null ) {
                            attrSet.remove("pwdchangedtime");
                        }
                        LDAPAttribute creatorAttr = attrSet.getAttribute("creatorsname");
                        if ( creatorAttr != null ) {
                            attrSet.remove("creatorsname");
                        }
                        LDAPAttribute createtimeAttr = attrSet.getAttribute("createtimestamp");
                        if ( createtimeAttr != null ) {
                            attrSet.remove("createtimestamp");
                        }
                        LDAPEntry addEntry = new LDAPEntry ( change.dn, attrSet );
                        //EclReadAndPlay.println ("INFO", "********************* Entry: ************** \n" + addEntry + "\n******************\n" );
                        try {
                            this.add( addEntry );
                        }
                        catch( LDAPException e ) {
                            EclReadAndPlay.println("ERROR", "Cannot add entry \"" + change.dn + "\" (csn=" 
                                         + change.csn + ")" );
                            EclReadAndPlay.println("ERROR", e.toString() );
                            e.printStackTrace();
                            System.exit(1);
                        }

                        // replace the unique id
//                        LDAPAttribute myAttr = new LDAPAttribute ("nsuniqueid", change.nsUniqueId);
//                        LDAPAttribute myAttr = new LDAPAttribute ("entryuuid", change.nsUniqueId);
//                        LDAPModification mod = new LDAPModification ( LDAPModification.REPLACE, myAttr );
//                        try {
//                            this.modify( change.dn, mod );
//                        }
//                        catch( LDAPException e ) {
//                            EclReadAndPlay.println ("ERROR", "Cannot modify nsuniqueid of entry \"" 
//                                                    + change.dn + "\" (csn=" + change.csn + ")" );
//                            EclReadAndPlay.println ("ERROR", e.toString() );
//                            System.exit(1);
//                        }
                        //System.out.EclReadAndPlay.println( addEntry);
                        break;
                    case LDIFContent.MODIFICATION_CONTENT:
                        mytype="MOD";
                        LDAPModification[] mods = ((LDIFModifyContent)content).getModifications();
                        // remove modifiersname and modifytimestamp mods
                        boolean[] deleteItem = new boolean[mods.length];
                        int size = 0;
                        for (int i = 0 ; i < mods.length ; i++) {
                            LDAPAttribute modAttr = mods[i].getAttribute();
                            if ( modAttr.getBaseName().equalsIgnoreCase("modifiersname") ||
                                 modAttr.getBaseName().equalsIgnoreCase("modifytimestamp") ) {
                                // remove mods[i] from mods
                                deleteItem[i] = true;
                            } else {
                                deleteItem[i] = false;
                                size++;
                            }
                        }
                        LDAPModification[] realMods = new LDAPModification[size];
                        int index = 0;
                        for (int i = 0 ; i < mods.length ; i++) {
                            if ( !deleteItem[i] ) {
                                realMods[index++] = mods[i];
                            }
                        }
                        try {
                            this.modify( change.dn, realMods );
                        }
                        catch( LDAPException e ) {
                            EclReadAndPlay.println("ERROR", "Cannot modify entry \"" + change.dn 
                                         + "\" (csn=" + change.csn + ")" );
                            EclReadAndPlay.println("DEBUG", "mods\"" + mods + "\"" );
                            EclReadAndPlay.println("ERROR", e.toString() );
                            e.printStackTrace();
                            System.exit(1);
                        }
                        break;
                    case LDIFContent.MODDN_CONTENT:
                        if ( change.newRDN == null ) { // => fixOP MODRDN
                            change.newRDN=((LDIFModDNContent)content).getRDN();
                            change.deleteOldRDN=((LDIFModDNContent)content).getDeleteOldRDN();
                            change.newSuperior=((LDIFModDNContent)content).getNewParent();
                        }
                        
                        
                        try {
                            if (change.newSuperior == null ) {
                                mytype="MODRDN";
                                this.rename( change.dn, change.newRDN, change.deleteOldRDN );
                            }
                            else {
                                mytype="MODDN";
                                this.rename( change.dn, change.newRDN, change.newSuperior, change.deleteOldRDN );
                            }
                        }
                        catch( LDAPException e ) {
                            EclReadAndPlay.println( "ERROR", "Cannot rename entry \"" + change.dn 
                                          + "\" (csn=" + change.csn + ")" );
                            EclReadAndPlay.println( "ERROR", "newRDN =\"" + change.newRDN 
                                          + "\" (deleteOldRDN=" + change.deleteOldRDN + ")" );
                            EclReadAndPlay.println( "ERROR", "change =\"" + mychange + ")" );
                            EclReadAndPlay.println( "ERROR", e.toString());
                            e.printStackTrace();
                            System.exit(1);
                        }
                        break;
                    case LDIFContent.DELETE_CONTENT:
                        mytype="DEL";
                        try {
                            this.delete( change.dn );
                        }
                        catch( LDAPException e ) {
                            EclReadAndPlay.println ("ERROR", "Cannot delete entry \"" + change.dn 
                                          + "\" (csn=" + change.csn + ")" );
                            EclReadAndPlay.println( "ERROR", e.toString() );
                            e.printStackTrace();
                            System.exit(1);
                        }
                        break;
                    default:
                        EclReadAndPlay.println("ERROR", "Cannot parse change (type=" + content.getType() 
                                     + "):\n" + mychange + "_");
                        mytype="Unknown";
                        break;
                }

            } catch ( IOException e ) {
                EclReadAndPlay.println( "ERROR" , e.toString() );
                e.printStackTrace();
                EclReadAndPlay.println( "ERROR" , change.toString() );
            }
            EclReadAndPlay.accessOut.println(EclReadAndPlay.getDate() + "- INFO: " + mytype + " \"" + change.dn 
                                   + "\" (" + change.csn +" / " + change.changeNumber + ")" );
        }
    }
    
}


