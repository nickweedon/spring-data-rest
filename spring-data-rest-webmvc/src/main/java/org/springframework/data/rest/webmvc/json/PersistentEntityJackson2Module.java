package org.springframework.data.rest.webmvc.json;

import static org.springframework.beans.BeanUtils.*;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.CollectionFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter.ConvertiblePair;
import org.springframework.data.mapping.Association;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.SimpleAssociationHandler;
import org.springframework.data.mapping.model.BeanWrapper;
import org.springframework.data.repository.support.Repositories;
import org.springframework.data.rest.core.UriDomainClassConverter;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.core.mapping.ResourceMapping;
import org.springframework.data.rest.core.mapping.ResourceMappings;
import org.springframework.data.rest.core.mapping.ResourceMetadata;
import org.springframework.data.rest.webmvc.PersistentEntityResource;
import org.springframework.data.rest.webmvc.jpa.LineItem;
import org.springframework.data.rest.webmvc.jpa.Order;
import org.springframework.data.rest.webmvc.jpa.Person;
import org.springframework.data.rest.webmvc.support.RepositoryLinkBuilder;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.Assert;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.util.TokenBuffer;

/**
 * @author Jon Brisbin
 */
public abstract class PersistentEntityJackson2Module extends SimpleModule implements InitializingBean {

	private static final long serialVersionUID = -7289265674870906323L;
	private static final Logger LOG = LoggerFactory.getLogger(PersistentEntityJackson2Module.class);
	private static final TypeDescriptor URI_TYPE = TypeDescriptor.valueOf(URI.class);

	private final ResourceMappings mappings;
	private final ConversionService conversionService;

	@Autowired private Repositories repositories;
	@Autowired private RepositoryRestConfiguration config;
	@Autowired private UriDomainClassConverter uriDomainClassConverter;
	private Set<Class<?>> registeredDeserializerDomainClasses;

	public PersistentEntityJackson2Module(ResourceMappings resourceMappings, ConversionService conversionService) {

		super(new Version(1, 1, 0, "BUILD-SNAPSHOT", "org.springframework.data.rest", "jackson-module"));

		registeredDeserializerDomainClasses = new HashSet<Class<?>>();
		
		Assert.notNull(resourceMappings, "ResourceMappings must not be null!");
		Assert.notNull(conversionService, "ConversionService must not be null!");

		this.mappings = resourceMappings;
		this.conversionService = conversionService;

		addSerializer(new ResourceSerializer());
	}

	public static boolean maybeAddAssociationLink(RepositoryLinkBuilder builder, ResourceMappings mappings,
			PersistentProperty<?> persistentProperty, List<Link> links) {

		Assert.isTrue(persistentProperty.isAssociation(), "PersistentProperty must be an association!");
		ResourceMetadata ownerMetadata = mappings.getMappingFor(persistentProperty.getOwner().getType());

		if (!ownerMetadata.isManagedResource(persistentProperty)) {
			return false;
		}

		ResourceMapping propertyMapping = ownerMetadata.getMappingFor(persistentProperty);

		if (propertyMapping.isExported()) {
			links.add(builder.slash(propertyMapping.getPath()).withRel(propertyMapping.getRel()));
			// This is an association. We added a Link.
			return true;
		}

		// This is not an association. No Link was added.
		return false;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void afterPropertiesSet() throws Exception {
		
		for (Class<?> domainType : repositories) {
			PersistentEntity<?, ?> pe = repositories.getPersistentEntity(domainType);
			if (null == pe) {
				if (LOG.isWarnEnabled()) {
					LOG.warn("The domain class {} does not have PersistentEntity metadata.", domainType.getName());
				}
			} else {
				addDeserializer(domainType, new ResourceDeserializer(pe, mappings.getMappingFor(domainType)));
				registeredDeserializerDomainClasses.add(domainType);
			}
		}
	}

	private class ResourceDeserializer<T extends Object> extends StdDeserializer<T> {

		private static final long serialVersionUID = 8195592798684027681L;
		private final PersistentEntity<?, ?> persistentEntity;
		private final Map<String, TypeDescriptor> exportedAssociationTypeMap;

		private ResourceDeserializer(final PersistentEntity<?, ?> persistentEntity, final ResourceMetadata metadata) {
			super(persistentEntity.getType());
			this.persistentEntity = persistentEntity;
			this.exportedAssociationTypeMap = new HashMap<String, TypeDescriptor>();

			// Build the exported association map
			persistentEntity.doWithAssociations(new SimpleAssociationHandler() {

				/*
				 * (non-Javadoc)
				 * @see org.springframework.data.mapping.SimpleAssociationHandler#doWithAssociation(org.springframework.data.mapping.Association)
				 */
				@Override
				public void doWithAssociation(Association<? extends PersistentProperty<?>> association) {

					PersistentProperty<?> property = association.getInverse();
					
					if (metadata.isExported(property)) {
						exportedAssociationTypeMap.put(property.getName(), TypeDescriptor.valueOf(property.getType()));
					}
				}
			});
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
		
		public void parseJsonObject(JsonParser jp, TokenBuffer domainObjectBuffer, int depth) throws JsonParseException, IOException {
			Set<String> processedFields = new HashSet<String>();
			JsonToken token = jp.getCurrentToken();

			String name = jp.getCurrentName();

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

		@SuppressWarnings({ "unchecked", "incomplete-switch", "unused" })
		@Override
		public T deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {

			TokenBuffer domainObjectBuffer = new TokenBuffer(jp);
			
			//DomainObjectBuilder domainObjectBuilder = new DomainObjectBuilder(jp);
			
			JsonToken tok = null;
			
			try {
				logDebug(0, "Transforming HAL Json Object into regular inlined REST object: ");
				parseJsonObject(jp, domainObjectBuffer, 1);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			ObjectMapper objectMapper = new ObjectMapper();
			JsonParser filteredJp = domainObjectBuffer.asParser();

			//Object entity = objectMapper.readValue(filteredJp, handledType());

			Order order = (Order) objectMapper.readValue(filteredJp, handledType());
			Object entity = order;

			
			System.out.println("============ Domain Object ============");
			System.out.println("Order name: " + order.getOrderName());
			for(LineItem lineItem : order.getLineItems()) {
				System.out.println("Name: " + lineItem.getName());
			}
			Person creator = order.getCreator();
			System.out.println("Creator: " + creator.getFirstName() + " " + creator.getLastName());
			
			//System.out.println(ReflectionToStringBuilder.toString(domainObject));
			

			return (T) entity;
		}
	}

	private class ResourceSerializer extends StdSerializer<PersistentEntityResource<?>> {

		@SuppressWarnings({ "unchecked", "rawtypes" })
		private ResourceSerializer() {
			super((Class) PersistentEntityResource.class);
		}

		/*
		 * (non-Javadoc)
		 * @see com.fasterxml.jackson.databind.ser.std.StdSerializer#serialize(java.lang.Object, com.fasterxml.jackson.core.JsonGenerator, com.fasterxml.jackson.databind.SerializerProvider)
		 */
		@Override
		public void serialize(final PersistentEntityResource<?> resource, final JsonGenerator jgen,
				final SerializerProvider provider) throws IOException, JsonGenerationException {

			if (LOG.isDebugEnabled()) {
				LOG.debug("Serializing PersistentEntity " + resource.getPersistentEntity());
			}

			Object obj = resource.getContent();

			final PersistentEntity<?, ?> entity = resource.getPersistentEntity();
			final BeanWrapper<PersistentEntity<Object, ?>, Object> wrapper = BeanWrapper.create(obj, null);
			final Object entityId = wrapper.getProperty(entity.getIdProperty());
			final ResourceMetadata metadata = mappings.getMappingFor(entity.getType());
			final RepositoryLinkBuilder builder = new RepositoryLinkBuilder(metadata, config.getBaseUri()).slash(entityId);

			final List<Link> links = new ArrayList<Link>();
			// Start with ResourceProcessor-added links
			links.addAll(resource.getLinks());

			final Map<String, Object> model = new LinkedHashMap<String, Object>();

			try {
				final Set<String> filteredProperties = new HashSet<String>();

				if(entity.getIdProperty() != null && !config.isIdExposedFor(entity.getType())) {
					filteredProperties.add(entity.getIdProperty().getName());
				}

				// Add associations as links
				entity.doWithAssociations(new SimpleAssociationHandler() {

					/*
					 * (non-Javadoc)
					 * @see org.springframework.data.mapping.SimpleAssociationHandler#doWithAssociation(org.springframework.data.mapping.Association)
					 */
					@Override
					public void doWithAssociation(Association<? extends PersistentProperty<?>> association) {

						PersistentProperty<?> property = association.getInverse();

						if (maybeAddAssociationLink(builder, mappings, property, links)) {
							// A link was added so don't inline this association
							filteredProperties.add(property.getName());
							return;
						}
						
						if (!metadata.isExported(property)) {
							filteredProperties.add(property.getName());
						}
					}
				});

				MapResource mapResource = new MapResource(model, links, obj);
				
				// Create a Jackson JSON filter to remove 'filteredProperties' from the JSON output
				FilterProvider filters = 
						new SimpleFilterProvider().addFilter(DomainClassIntrospector.ENTITY_JSON_FILTER,
								SimpleBeanPropertyFilter.serializeAllExcept(filteredProperties));
				
				// Output the map resource using the filter and a custom AnnotationIntrospector
				// used by the objectmapper (see configuration class).
				// The custom AnnotationInstrospector associates the 'domain class' with the
				// filter we defined (an alternative to using the @JsonFilter class annotation)
				createObjectWriter(filters).writeValue(jgen, mapResource);
			} catch (IllegalStateException e) {
				throw (IOException) e.getCause();
			}
		}
	}

	protected abstract ObjectWriter createObjectWriter(FilterProvider filterProvider);
	
	private static class MapResource extends Resource<Map<String, Object>> {
		
		/**
		 * @param content
		 * @param links
		 */
		public MapResource(Map<String, Object> content, Iterable<Link> links, Object obj) {
			super(content, links);
			this.setObj(obj);
		}

		/* 
		 * (non-Javadoc)
		 * @see org.springframework.hateoas.Resource#getContent()
		 */
		@Override
		@JsonIgnore
		public Map<String, Object> getContent() {
			return super.getContent();
		}

		@JsonAnyGetter
		public Map<String, Object> any() {
			return getContent();
		}

		@com.fasterxml.jackson.annotation.JsonUnwrapped		
		private Object obj;
		
		public Object getObj() {
			return obj;
		}

		public void setObj(Object obj) {
			this.obj = obj;
		}
	}
}
