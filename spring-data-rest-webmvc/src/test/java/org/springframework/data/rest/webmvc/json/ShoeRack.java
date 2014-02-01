package org.springframework.data.rest.webmvc.json;

import java.util.List;

public class ShoeRack {

	private String type;
	private List<Shoe> shoes;

	public ShoeRack() {
		
	}
	
	public ShoeRack(String type, List<Shoe> shoes) {
		this.type = type;
		this.shoes = shoes;
	}
	
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public List<Shoe> getShoes() {
		return shoes;
	}
	public void setShoes(List<Shoe> shoes) {
		this.shoes = shoes;
	}
}
