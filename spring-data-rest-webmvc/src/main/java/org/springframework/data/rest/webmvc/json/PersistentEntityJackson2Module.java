package org.springframework.data.rest.webmvc.json;


import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mapping.Association;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.SimpleAssociationHandler;
import org.springframework.data.mapping.model.BeanWrapper;
import org.springframework.data.repository.support.Repositories;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.core.mapping.ResourceMapping;
import org.springframework.data.rest.core.mapping.ResourceMappings;
import org.springframework.data.rest.core.mapping.ResourceMetadata;
import org.springframework.data.rest.webmvc.PersistentEntityResource;
import org.springframework.data.rest.webmvc.support.RepositoryLinkBuilder;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
import org.springframework.util.Assert;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
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

	private final ResourceMappings mappings;

	@Autowired private Repositories repositories;
	@Autowired private RepositoryRestConfiguration config;
	@Autowired private HalToJsonConverter halToJsonConverter;
	private Set<Class<?>> registeredDeserializerDomainClasses;

	public PersistentEntityJackson2Module(ResourceMappings resourceMappings) {

		super(new Version(1, 1, 0, "BUILD-SNAPSHOT", "org.springframework.data.rest", "jackson-module"));

		registeredDeserializerDomainClasses = new HashSet<Class<?>>();
		
		Assert.notNull(resourceMappings, "ResourceMappings must not be null!");

		this.mappings = resourceMappings;

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
				addDeserializer(domainType, new ResourceDeserializer(pe, halToJsonConverter));
				registeredDeserializerDomainClasses.add(domainType);
			}
		}
	}

	private class ResourceDeserializer<T extends Object> extends StdDeserializer<T> {

		private static final long serialVersionUID = 8195592798684027681L;
		private final HalToJsonConverter halToJsonConverter;

		private ResourceDeserializer(final PersistentEntity<?, ?> persistentEntity, 
				HalToJsonConverter halToJsonConverter) {
			super(persistentEntity.getType());
			this.halToJsonConverter = halToJsonConverter;

		}
		

		@SuppressWarnings({ "unchecked", "incomplete-switch", "unused" })
		@Override
		public T deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {

			TokenBuffer domainObjectBuffer = new TokenBuffer(jp);
			
			//DomainObjectBuilder domainObjectBuilder = new DomainObjectBuilder(jp);
			
			JsonToken tok = null;
			
			try {
				halToJsonConverter.parseJsonObject(jp, domainObjectBuffer, 1);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
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
