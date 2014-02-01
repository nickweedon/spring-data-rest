package org.springframework.data.rest.webmvc.json;


import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.rest.webmvc.jpa.JpaWebTests;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = RepositoryTestsConfig.class)
public class ResourceDeserializerTests {
	private SimpleModule module; 
	private static final TypeDescriptor URI_TYPE = TypeDescriptor.valueOf(URI.class);
	private ConditionalGenericConverter mockUriConverter; 

	
	@Before
	public void setUp() {
		mockUriConverter = mock(ConditionalGenericConverter.class);
		
		module = new SimpleModule("LongDeserializerModule",
					new Version(1, 0, 0, null, null, null));
		
	}

	@Test
	public void deserializesShoeNoLace() throws Exception {
		final Map<String, TypeDescriptor> exportedAssociations = new HashMap<String, TypeDescriptor>();

		addDerserializer(Shoe.class, exportedAssociations);
		
		Shoe shoe = deserializeJsonFile("shoeNoLace.json", Shoe.class);
		
		assertThat(shoe, is(notNullValue()));
		assertEquals("blue", shoe.getColour());
		assertEquals(11, shoe.getSize());
		assertEquals(51, shoe.getId());
	}

	@Test(expected=HttpMessageNotReadableException.class)
	public void deserializesShoeLinkLaceNoExportFails() throws Exception {
		
		addUrlObject("http://localhost:8080/shoeLace/1", new ShoeLace("Black", "Nylon", 15));
		
		final Map<String, TypeDescriptor> exportedAssociations = new HashMap<String, TypeDescriptor>();
		
		addDerserializer(Shoe.class, exportedAssociations);

		deserializeJsonFile("shoeLinkLace.json", Shoe.class);
	}

	@Test(expected=HttpMessageNotReadableException.class)
	public void deserializesShoeLinkLaceNoUriObjectFails() throws Exception {
		
		final Map<String, TypeDescriptor> exportedAssociations = new HashMap<String, TypeDescriptor>();
		exportedAssociations.put("lace", TypeDescriptor.valueOf(ShoeLace.class));
		
		addDerserializer(Shoe.class, exportedAssociations);

		deserializeJsonFile("shoeLinkLace.json", Shoe.class);
	}
	
	@Test
	public void deserializesShoeLinkLace() throws Exception {
		
		addUrlObject("http://localhost:8080/shoeLace/1", new ShoeLace("Black", "Nylon", 15));
		
		final Map<String, TypeDescriptor> exportedAssociations = new HashMap<String, TypeDescriptor>();
		exportedAssociations.put("lace", TypeDescriptor.valueOf(ShoeLace.class));
		
		addDerserializer(Shoe.class, exportedAssociations);

		Shoe shoe = deserializeJsonFile("shoeLinkLace.json", Shoe.class);

		// Ensure that the parent object is still serialized correctly
		assertThat(shoe, is(notNullValue()));
		assertEquals("blue", shoe.getColour());
		assertEquals(11, shoe.getSize());
		assertEquals(51, shoe.getId());
		
		assertThat(shoe.getLace(), is(notNullValue()));
		assertEquals("Black", shoe.getLace().getColour());
		assertEquals("Nylon", shoe.getLace().getMaterial());
		assertEquals(15, shoe.getLace().getLength());
	}

	private void addUrlObject(String uri, Object object) {
		when(mockUriConverter.convert(URI.create(uri), 
				URI_TYPE, TypeDescriptor.valueOf(object.getClass())))
					.thenReturn(object);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void addDerserializer(Class<?> objectType, Map<String, TypeDescriptor> exportedAssociations) {
		module.addDeserializer(objectType, 
				new ResourceDeserializer(objectType, exportedAssociations, 
						mockUriConverter));
	}
	
	private <T> T deserializeJsonFile(String file, Class<? extends T> objectType) throws JsonParseException, JsonMappingException, IOException {
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.registerModule(module);		
		
		ClassPathResource classPathFile = new ClassPathResource(file, ResourceDeserializerTests.class);
		
		return (T) objectMapper.readValue(classPathFile.getFile(), objectType);
	}
}
