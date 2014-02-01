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
import org.springframework.data.mapping.Association;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.SimpleAssociationHandler;
import org.springframework.data.mapping.model.BeanWrapper;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.core.mapping.ResourceMappings;
import org.springframework.data.rest.core.mapping.ResourceMetadata;
import org.springframework.data.rest.webmvc.PersistentEntityResource;
import org.springframework.data.rest.webmvc.json.PersistentEntityJackson2Module.MapResource;
import org.springframework.data.rest.webmvc.support.RepositoryLinkBuilder;
import org.springframework.hateoas.Link;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

class ResourceSerializer extends StdSerializer<PersistentEntityResource<?>> {

	static final Logger LOG = LoggerFactory.getLogger(PersistentEntityJackson2Module.class);
	final ResourceMappings mappings;
	final RepositoryRestConfiguration config;
	final ObjectMapper objectMapper;
	
	@SuppressWarnings({ "unchecked", "rawtypes" }) 
	ResourceSerializer(ResourceMappings mappings, RepositoryRestConfiguration config, ObjectMapper objectMapper) {
		super((Class) PersistentEntityResource.class);
		this.mappings = mappings;
		this.config = config;
		this.objectMapper = objectMapper;
	}

	/*
	 * (non-Javadoc)
	 * @see com.fasterxml.jackson.databind.ser.std.StdSerializer#serialize(java.lang.Object, com.fasterxml.jackson.core.JsonGenerator, com.fasterxml.jackson.databind.SerializerProvider)
	 */
	@Override
	public void serialize(final PersistentEntityResource<?> resource, final JsonGenerator jgen,
			final SerializerProvider provider) throws IOException, JsonGenerationException {

		if (PersistentEntityJackson2Module.LOG.isDebugEnabled()) {
			PersistentEntityJackson2Module.LOG.debug("Serializing PersistentEntity " + resource.getPersistentEntity());
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

					if (PersistentEntityJackson2Module.maybeAddAssociationLink(builder, mappings, property, links) || !metadata.isExported(property)) {
						// A link was added so don't inline this association
						filteredProperties.add(property.getName());
					}
					
				}
			});

			MapResource mapResource = new PersistentEntityJackson2Module.MapResource(model, links, obj);
			
			// Create a Jackson JSON filter to remove 'filteredProperties' from the JSON output
			FilterProvider filters = 
					new SimpleFilterProvider().addFilter(DomainClassIntrospector.ENTITY_JSON_FILTER,
							SimpleBeanPropertyFilter.serializeAllExcept(filteredProperties));
			
			// Output the map resource using the filter and a custom AnnotationIntrospector
			// used by the objectmapper (see configuration class).
			// The custom AnnotationInstrospector associates the 'domain class' with the
			// filter we defined (an alternative to using the @JsonFilter class annotation)
			objectMapper.writer(filters).writeValue(jgen, mapResource);
		} catch (IllegalStateException e) {
			throw (IOException) e.getCause();
		}
	}
}