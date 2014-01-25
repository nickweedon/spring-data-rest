package org.springframework.data.rest.webmvc.json;

import java.util.*;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;

public class DomainClassIntrospector extends JacksonAnnotationIntrospector {
	public static final String ENTITY_JSON_FILTER = "PersistentEntityJSONFilter";

	public Set<Class<?>> domainClassSet;
	
	public DomainClassIntrospector(List<Class<?>> domainClasses) {

		domainClassSet = new HashSet<Class<?>>();
		domainClassSet.addAll(domainClasses);
	}

	
	@Override
	public Object findFilterId(Annotated ann) {
		// Allow a filter annotation to override the default filter
		Object id = super.findFilterId(ann);

		if(id != null) {
			return id;
		}

		if(domainClassSet.contains(ann.getRawType())) {
    		return ENTITY_JSON_FILTER;
    	}
    	
    	return null;
	}

}

