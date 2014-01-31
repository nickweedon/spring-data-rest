package org.springframework.data.rest.webmvc.jpa;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class Sequence {
	@Id
	String name;

	Sequence() {

	}
	
	
	Sequence(String name) {
		this.name = name;
	}
	
	long value;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public long getNextValue() {
		return ++value;
	}
}
