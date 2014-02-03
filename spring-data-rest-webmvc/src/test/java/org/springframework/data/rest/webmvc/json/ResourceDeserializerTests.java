package org.springframework.data.rest.webmvc.json;


import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.repository.support.Repositories;
import org.springframework.data.rest.core.mapping.ResourceMappings;
import org.springframework.data.rest.webmvc.jpa.Author;
import org.springframework.data.rest.webmvc.jpa.Book;
import org.springframework.data.rest.webmvc.jpa.Order;
import org.springframework.data.rest.webmvc.support.RepositoryUriResolver;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import org.junit.runners.MethodSorters;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = RepositoryTestsConfig.class)
@DirtiesContext
public class ResourceDeserializerTests {
	private SimpleModule module; 
	private static final TypeDescriptor URI_TYPE = TypeDescriptor.valueOf(URI.class);
	private ConditionalGenericConverter mockUriConverter; 

	@Autowired private Repositories repositories;
	@Autowired private ResourceMappings mappings;

	
	@Before
	public void setUp() {
		mockUriConverter = mock(ConditionalGenericConverter.class);
		
		module = new SimpleModule("LongDeserializerModule",
					new Version(1, 0, 0, null, null, null));
		
	}

	/**
	 * @throws Exception
	 */
	@Test(expected=HttpMessageNotReadableException.class)
	public void deserializeBookLinkAuthorNoExportFails() throws Exception {
		
		addUrlObject("http://localhost:8080/authors/57", new Author((long)57, "Joshua Bloch"));
		addDerserializer(Book.class);
		deserializeJsonFile("bookBadLinkAuthor.json", Book.class);
	}
	
	@Test(expected=HttpMessageNotReadableException.class)
	public void deserializeBookLinkAuthorNoUriObjectFails() throws Exception {

		addDerserializer(Book.class);
		deserializeJsonFile("bookLinkAuthor.json", Book.class);
	}

	@Test
	public void deserializesBookInlineAuthor() throws Exception {
		addDerserializer(Order.class);
		
		Book book = deserializeJsonFile("bookInlineAuthor.json", Book.class);
		
		assertThat(book, is(notNullValue()));
		assertEquals("Thinking in Java", book.getTitle());
		assertEquals("978-0-13-187248-6", book.getIsbn());

		assertThat(book.getAuthors(), is(notNullValue()));
		assertTrue(book.getAuthors().contains(new Author(null, "Bruce Eckel")));
	}

	@Test(expected=HttpMessageNotReadableException.class)
	public void deserializeBookLinkAuthorAndInlineAuthorFails() throws Exception {
		
		addUrlObject("http://localhost:8080/authors/57", new Author((long)57, "Joshua Bloch"));
		addDerserializer(Book.class);
		deserializeJsonFile("bookLinkAuthorAndInlineAuthor.json", Book.class);
	}
	
	@Test
	public void deserializesBookInlineAuthorAndSelfLink() throws Exception {
		addDerserializer(Book.class);
		
		Book book = deserializeJsonFile("bookInlineAuthorAndSelfLink.json", Book.class);
		
		assertThat(book, is(notNullValue()));
		assertEquals("Effective Java", book.getTitle());
		assertEquals("978-0321356680", book.getIsbn());

		assertThat(book.getAuthors(), is(notNullValue()));
		assertTrue(book.getAuthors().contains(new Author(null, "Joshua Bloch")));
	}
	
	private void addUrlObject(String uri, Object object) {
		when(mockUriConverter.convert(URI.create(uri), 
				URI_TYPE, TypeDescriptor.valueOf(object.getClass())))
					.thenReturn(object);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void addDerserializer(Class<?> objectType) {
		PersistentEntity<?, ?> pe = repositories.getPersistentEntity(objectType);
		if(pe == null) {
			throw new NullPointerException("No persistenty entity for type: " + objectType.getName());
		}
		module.addDeserializer(objectType, 
				new ResourceDeserializer(pe, mockUriConverter, mappings.getMappingFor(objectType),
						new RepositoryUriResolver(repositories, mappings)));
	}
	
	private <T> T deserializeJsonFile(String file, Class<? extends T> objectType) throws JsonParseException, JsonMappingException, IOException {
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.registerModule(module);		
		
		ClassPathResource classPathFile = new ClassPathResource(file, ResourceDeserializerTests.class);
		
		return (T) objectMapper.readValue(classPathFile.getFile(), objectType);
	}
}
