package com.vidi.whiteboard;

import processing.core.PGraphics;

public class Thumbnail {
	public PGraphics pg;
	public int line; //the starting line of this thumbnail
	
	public Thumbnail(PGraphics pg, int line)
	{
		this.pg = pg;
		this.line = line;
	}
}
