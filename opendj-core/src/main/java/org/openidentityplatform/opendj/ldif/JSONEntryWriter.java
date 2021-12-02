package org.openidentityplatform.opendj.ldif;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldif.EntryWriter;
import org.forgerock.util.Reject;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JSONEntryWriter implements EntryWriter {
	static ObjectMapper mapper = new ObjectMapper();
	
	final PrintWriter out;
	public JSONEntryWriter(final OutputStream out) {
        this.out=new PrintWriter(out);
        this.out.println("[");
    }
	
   @Override
    public void close() throws IOException {
	   this.out.println("]");
	   out.close();
    }

    @Override
    public void flush() throws IOException {
    	out.flush();
    }

  
    @Override
    public JSONEntryWriter writeComment(final CharSequence comment) throws IOException {
        return this;
    }

    boolean firstEntry=true;
    @Override
    public JSONEntryWriter writeEntry(final Entry entry) throws IOException {
        Reject.ifNull(entry);
        
        this.out.println(((firstEntry)?" ":",")+"{\""+new String(JsonStringEncoder.getInstance().quoteAsString(entry.getName().toString())) +"\":[");
        firstEntry=false;
        
        final TreeMap<String,AbstractMap.SimpleEntry<String,ByteSequence>> attr=new TreeMap<>(); //sort by key:value
        for (final Attribute attribute : entry.getAllAttributes()) {
            final String attributeDescription = attribute.getAttributeDescriptionAsString();
            if (attribute.isEmpty()) {
            	attr.put(attributeDescription, new AbstractMap.SimpleEntry<String,ByteSequence>(attributeDescription,ByteString.empty()) );
            } else {
                for (final ByteString value : attribute) {
                    attr.put(attributeDescription+value, new AbstractMap.SimpleEntry<String,ByteSequence>(attributeDescription,value));
                }
            }
        }
        boolean first=true;
        for (AbstractMap.SimpleEntry<String,ByteSequence> kv : attr.values()) { 
        	final Map<String, String> params = new HashMap<>(1);
        	params.put(kv.getKey(),kv.getValue().toString());
        	this.out.println(" "+((first)?" ":",")+mapper.writeValueAsString(params));
        	first=false;
		}
        this.out.println("]}");
        return this;
    }
}
