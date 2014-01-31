package org.springframework.data.rest.webmvc.json;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.support.Repositories;
import org.springframework.data.rest.webmvc.jpa.Order;
import org.springframework.data.rest.webmvc.jpa.OrderRepository;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = RepositoryTestsConfig.class)
@Transactional
public class HalToJsonConverterTests {
	
	@Autowired private HalToJsonConverter halToJsonConv;

	@Test
	public void deserializesOrderInlineCreator() {
		final long ORDER_ID = 4711;

		//String content = readFile("orderInlineCreator.json");

		// Assert that the ID was set
		/*
		Order order = orderRepo.findOne(ORDER_ID);
		assertThat(order, is(notNullValue()));
		assertEquals("Mr Burn's order", order.getOrderName());
		
		// Assert that the 'creator' was created inline
		assertThat(order.getCreator(), is(notNullValue()));
		assertEquals("Montgomery", order.getCreator().getFirstName());
		assertEquals("Burns", order.getCreator().getLastName());
		*/
	}
	
}
