package org.springframework.data.rest.webmvc.json;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.rest.core.UriDomainClassConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.util.TokenBuffer;

class ResourceDeserializer<T extends Object> extends StdDeserializer<T> {

	private static final long serialVersionUID = 8195592798684027681L;
	private final Map<String, TypeDescriptor> exportedAssociationTypeMap;
	private final ConditionalGenericConverter uriDomainClassConverter;
	private static final TypeDescriptor URI_TYPE = TypeDescriptor.valueOf(URI.class);
	private static final Logger LOG = LoggerFactory.getLogger(PersistentEntityJackson2Module.class);

	ResourceDeserializer(Class<?> entityType, 
			Map<String, TypeDescriptor> exportedAssociationTypeMap,
			ConditionalGenericConverter uriDomainClassConverter) {
		super(entityType);
		this.exportedAssociationTypeMap = exportedAssociationTypeMap;
		this.uriDomainClassConverter = uriDomainClassConverter; 
	}

	@SuppressWarnings({ "unchecked", "incomplete-switch", "unused" })
	@Override
	public T deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
		JsonToken tok = null;
		
		TokenBuffer domainObjectBuffer = convertToJsonBuffer(jp);
		
		ObjectMapper objectMapper = new ObjectMapper();
		JsonParser filteredJp = domainObjectBuffer.asParser();

		Object entity = objectMapper.readValue(filteredJp, handledType());

		/*
		Order order = (Order) objectMapper.readValue(filteredJp, handledType());
		Object entity = order;

		
		System.out.println("============ Domain Object ============");
		System.out.println("Order name: " + order.getOrderName());
		for(LineItem lineItem : order.getLineItems()) {
			System.out.println("Name: " + lineItem.getName());
		}
		Person creator = order.getCreator();
		System.out.println("Creator: " + creator.getFirstName() + " " + creator.getLastName());
		*/
		
		//System.out.println(ReflectionToStringBuilder.toString(domainObject));
		

		return (T) entity;
	}
	
	private String getErrorMsgPrefix(JsonParser jp) {
		int JsonLineNo = jp.getCurrentLocation().getLineNr();
		return "While parsing JSON document at line " + JsonLineNo + ": ";
	}
	
	private void logDebug(int depth, String msg) {
		if(LOG.isWarnEnabled()) {
			StringBuilder fullMsg = new StringBuilder();
			for(int i = 1; i < depth; i++) {
				fullMsg.append("\t");
			}
			fullMsg.append(msg);
			LOG.warn(fullMsg.toString());
		}
	}
	
	private TokenBuffer convertToJsonBuffer(JsonParser halJsonParser) throws JsonParseException, IOException {
		TokenBuffer domainObjectBuffer = new TokenBuffer(halJsonParser);
		parseJsonObject(halJsonParser, domainObjectBuffer, 1);
		return domainObjectBuffer;
	}
	
	private void parseJsonObject(JsonParser jp, TokenBuffer domainObjectBuffer, int depth) throws JsonParseException, IOException {
		Set<String> processedFields = new HashSet<String>();
		JsonToken token = jp.getCurrentToken();

		String name = jp.getCurrentName();

		if(token == null) {
			throw new HttpMessageNotReadableException(getErrorMsgPrefix(jp) 
					+ "Malformed JSON encountered. Cannot parse object.");
		}
		
		logDebug(depth, name + ": " + token.toString());
		
		domainObjectBuffer.copyCurrentEvent(jp);

		depth++;

		do {
			token = jp.nextToken();
			name = jp.getCurrentName();

			if(token == JsonToken.START_OBJECT) {
				parseJsonObject(jp, domainObjectBuffer, depth);
				continue;
			}

			if(token == JsonToken.END_ARRAY || token == JsonToken.END_OBJECT) {
				depth--;
			}
			
			if(token == JsonToken.FIELD_NAME) {
				if(processedFields.contains(name)) {
					throw new HttpMessageNotReadableException(getErrorMsgPrefix(jp) 
						+ "Encountered field '" + name + " more than once in the same object."
						+ "Note that a relation field may not appear inline if it is contained in the '_links' section of that object.");
				}
				processedFields.add(name);
				
				if(name.equals("_links")) {
					logDebug(depth, "<Processing a '_links' section>");
					token = jp.nextToken();
					if(token != JsonToken.START_OBJECT) {
						throw new HttpMessageNotReadableException(getErrorMsgPrefix(jp) 
								+ "Expected '_links' to be of type object.");
					}
					processLinksObject(jp, domainObjectBuffer, processedFields, depth);
					continue;
				}
			}

			logDebug(depth, name + ": " + token.toString());

			if(token == JsonToken.START_ARRAY) {
				depth++;
			}

			domainObjectBuffer.copyCurrentEvent(jp);
		}
		while(token != JsonToken.END_OBJECT); 
	}

	private void processLinksObject(JsonParser jp, TokenBuffer domainObjectBuffer, Set<String> processedFields, int depth) throws JsonParseException, IOException {
		// Inside _links object

		JsonToken token = jp.nextToken();
		depth++;

		// For each link field
		do {
			if(token != JsonToken.FIELD_NAME) {
				throw new HttpMessageNotReadableException(getErrorMsgPrefix(jp) 
						+ "Expected field but encountered json token type '" + token.toString() + "'.");					
			}
			String linkName = jp.getCurrentName();

			if(processedFields.contains(linkName)) {
				throw new HttpMessageNotReadableException(getErrorMsgPrefix(jp) 
					+ "Encountered link field '" + linkName + " more than once in the same object."
					+ "Note that a relation field may not appear inline if it is contained in the '_links' section of that object.");
			}
			
			if(token == JsonToken.FIELD_NAME && linkName.equals("_links")) {
				throw new HttpMessageNotReadableException(getErrorMsgPrefix(jp) 
						+ "Encountered '_links' property while already inside a '_links' object.");
			}
			
			// Process the link field's value, could be either a single object or an array
			token = jp.nextToken();
			boolean isArray = false;
			switch(token) {
				case START_OBJECT:
					break;
				case START_ARRAY:
					isArray = true;
					//domainObjectBuffer.copyCurrentEvent(jp);
					// Consume the next token so we are in a state that is consistent
					// with that of processing a single link value.
					token = jp.nextToken();
					break;
				default:
					throw new HttpMessageNotReadableException(getErrorMsgPrefix(jp) 
							+ "Expected link property to be of 'object' or 'array' type.");
			}
			
			// Continue to process the value(s) of the link until we reach:
			// END_OBJECT (for an object) or END_ARRAY (for an array)
			boolean moreLinkValues = true;
			while(moreLinkValues) {
				token = jp.nextToken();
				switch(token) {
					case FIELD_NAME:
						if(!jp.getCurrentName().equals("href")) {

							// We only understand 'href' at the moment so ignore
							// TODO: log a warning if logging is enabled
							token = jp.nextToken();
							continue;
						}
						//token = jp.nextToken();
						String uriString = jp.nextTextValue();
						
						TypeDescriptor associationType = exportedAssociationTypeMap.get(linkName);

						// If we have an exported association mapping for this type
						if(associationType != null) {
							
							URI uri = URI.create(uriString);
							Object linkObject = uriDomainClassConverter.convert(uri, URI_TYPE, associationType);
							
							if(linkObject == null) {
								throw new HttpMessageNotReadableException(getErrorMsgPrefix(jp) 
										+ "No data object for URI '" + uriString + "'.");
							}
							
							// Add the created association object to the Json buffer representing 
							// the transformed domain object
							domainObjectBuffer.writeObjectField(linkName, linkObject);
							logDebug(depth, "new '" + linkName + "' <-- '" + uriString + "'");
						} else {
							throw new HttpMessageNotReadableException(getErrorMsgPrefix(jp) 
									+ "Could not create association mapping for uri '" + uriString 
									+ "' as there is no exported assocation for this type.");
						}
						break;
					case END_OBJECT:
						if(!isArray) {
							moreLinkValues = false;
						}
						break;
					case END_ARRAY:
						if(!isArray) {
							throw new HttpMessageNotReadableException(getErrorMsgPrefix(jp) 
									+ "Expected end of array but encountered end of object.");
						} 
						//domainObjectBuffer.copyCurrentEvent(jp);
						moreLinkValues = false;
						break;
					case START_OBJECT:
						if(!isArray) {
							throw new HttpMessageNotReadableException(getErrorMsgPrefix(jp) 
									+ "Encountered more than one object in a non-array type '_links' object.");
						} 
						break;
					default:
						throw new HttpMessageNotReadableException(getErrorMsgPrefix(jp) 
								+ "Expected field or end of object/array.");
				}
			}
			// Read the next link field
			token = jp.nextToken();
		} while(token != JsonToken.END_OBJECT);
	}
}