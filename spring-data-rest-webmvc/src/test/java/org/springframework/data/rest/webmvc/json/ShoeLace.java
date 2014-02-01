package org.springframework.data.rest.webmvc.json;

public class ShoeLace {
	private String colour;
	private String material;
	private int length;

	public ShoeLace() {
		
	}
	
	public ShoeLace(String colour, String material, int length) {
		this.colour = colour;
		this.material = material;
		this.length = length;
	}
	
	public String getColour() {
		return colour;
	}
	public void setColour(String colour) {
		this.colour = colour;
	}
	public String getMaterial() {
		return material;
	}
	public void setMaterial(String material) {
		this.material = material;
	}
	public int getLength() {
		return length;
	}
	public void setLength(int length) {
		this.length = length;
	}
	
	
}
