package org.springframework.data.rest.webmvc.json;

public class Shoe {
	
	private int id;
	private String colour;
	private int size;
	private ShoeLace lace;

	public Shoe() {
	}
	
	public Shoe(int id, String colour, int size, ShoeLace lace) {
		this.id = id;
		this.colour = colour;
		this.size = size;
		this.lace = lace;
	}
	
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public String getColour() {
		return colour;
	}
	public void setColour(String colour) {
		this.colour = colour;
	}
	public int getSize() {
		return size;
	}
	public void setSize(int size) {
		this.size = size;
	}
	public ShoeLace getLace() {
		return lace;
	}
	public void setLace(ShoeLace lace) {
		this.lace = lace;
	}
	
}
