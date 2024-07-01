package org.openidentityplatform.opendj.ldif;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldif.EntryReader;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;


public final class JSONEntryReader implements EntryReader {

	static ObjectMapper mapper = new ObjectMapper();
	
	JsonParser parser ;
	JsonToken token=null;
	
    public JSONEntryReader(final InputStream in) throws JsonParseException, IOException {
    	parser=mapper.getFactory().createParser(in);
    	if ( parser.nextToken() != JsonToken.START_ARRAY ) {
    	    throw new JsonParseException(parser, "invalid format" );
    	}
    	token=parser.nextToken();
    }

    @Override
    public void close() throws IOException {
    	parser.close();
    }

    @Override
    public boolean hasNext() throws DecodeException, IOException {
       return token == JsonToken.START_OBJECT ;
    }


    @Override
    public Entry readEntry() throws DecodeException, IOException {
    	if (hasNext()) {
    		final Map<String,List<Map<String,String>>> entry=mapper.readValue(parser,new TypeReference<Map<String,List<Map<String,String>>>>() {});
    		final String key=entry.keySet().iterator().next(); 
    		final Entry res=new LinkedHashMapEntry(key);
    		List<Map<String,String>> attrsArray=entry.get(res.getName().toString());
    		if (attrsArray==null) {
    			attrsArray=entry.get(key);
    		}
    		for (Map<String,String> attrs : attrsArray) {
    			for (java.util.Map.Entry<String,String> attr : attrs.entrySet()) {
    				res.addAttribute(attr.getKey(), attr.getValue());
				}
			}
    		token=parser.nextToken();
    		return res;
    	}
    	return null;
    }
}
