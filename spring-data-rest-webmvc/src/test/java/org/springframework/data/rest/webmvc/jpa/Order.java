/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.rest.webmvc.jpa;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.annotations.GenericGenerator;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Oliver Gierke
 */
@Entity
@Table(name = "ORDERS")
public class Order {

	@Id 
	@GeneratedValue(generator = "TransactionalIDGenerator")
	@GenericGenerator(name = "TransactionalIDGenerator",
	        strategy = "org.springframework.data.rest.webmvc.jpa.TransactionalIDGenerator")		
	private Long id;
	@ManyToOne(cascade = CascadeType.MERGE, fetch = FetchType.LAZY)//
	private Person creator;
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)//
	private Set<LineItem> lineItems = new HashSet<LineItem>();

	public Order(Person creator) {
		this.creator = creator;
	}
	
	private String orderName;
	public String getOrderName() {
		return orderName;
	}

	public void setId(Long id) {
		this.id = id;
	}
	
	public void setOrderName(String orderName) {
		this.orderName = orderName;
	}

	protected Order() {

	}

	public Long getId() {
		return id;
	}

	public Person getCreator() {
		return creator;
	}

	/**
	 * @return the lineItems
	 */
	public Set<LineItem> getLineItems() {
		return lineItems;
	}

	public void add(LineItem item) {
		this.lineItems.add(item);
	}
}
