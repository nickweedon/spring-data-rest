package org.springframework.data.rest.webmvc.support;

import static org.springframework.util.StringUtils.hasText;

import java.net.URI;

import org.springframework.data.repository.support.Repositories;
import org.springframework.data.rest.core.mapping.ResourceMappings;
import org.springframework.data.rest.core.mapping.ResourceMetadata;
import org.springframework.util.Assert;

public class RepositoryUriResolver {
	
	private final Repositories repositories;
	private final ResourceMappings mappings;
	
	public RepositoryUriResolver(Repositories repositories,
			ResourceMappings mappings) {
		Assert.notNull(repositories, "Repositories must not be null!");
		Assert.notNull(mappings, "ResourceMappings must not be null!");
		
		this.repositories = repositories;
		this.mappings = mappings;
	}

	public ResourceMetadata findRepositoryInfoForUri(URI uri) {
		return findRepositoryInfoForUriPath(uri.getPath());
	}
	
	public ResourceMetadata findRepositoryInfoForUriPath(String requestUriPath) {
			
		if (requestUriPath.startsWith("/")) {
			requestUriPath = requestUriPath.substring(1);
		}
	
		String[] parts = requestUriPath.split("/");
	
		if (parts.length == 0) {
			// Root request
			return null;
		}
	
		return findRepositoryInfoForSegment(parts[0]);
	}
	
	public ResourceMetadata findRepositoryInfoForSegment(String pathSegment) {
	
		if (!hasText(pathSegment)) {
			return null;
		}
	
		for (Class<?> domainType : repositories) {
			ResourceMetadata mapping = mappings.getMappingFor(domainType);
			if (mapping.getPath().matches(pathSegment) && mapping.isExported()) {
				return mapping;
			}
		}
	
		return null;
	}

}
