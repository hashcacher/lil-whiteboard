package com.vidi.whiteboard;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import processing.core.*;

//TODO: Replace all new Color(hexcolor) with 0xff0000 | hexcolor
//TODO: stop the thumbnails from scrolling too far
//TODO: fix thumbnail reload color bugs
//TODO: reduce redundant logs
public class WhiteBoard extends PApplet{

	  Color currentColor = Color.black;
	  float scale;
	  float swatchSize;
	  final int strokeWeight = 10;
	  final int swatchPadding = 3;
	  //Note on strokeweight:
	  ////big pic strokeweight   = strokeWeight
	  ////thumbnail strokeweight = 1
	  ////big pic eraser strokeweight   = strokeWeight * 10
	  ////thumbnail eraser strokeweight = 7.5f ;-)
	  
	  float prevX, prevY;
	  boolean mouseUp = true;
	  
	  //buttons
	  //button events are handled in mouseclicked
	  float saveX = swatchSize+40, saveY = 90, saveW = 345, saveH = 50;
	  float clearX = swatchSize+40, clearY = 20, clearW = 345, clearH = 50;
	  
	  int[] colors = {
	      0x9e0142,
	      0xf46d43,
	      0xfdae61,
	      0xfee08b,
	      0xe6f598,
	      0xabdda4,
	      0x66c2a5,
	      0x3288bd,
	      0x5e4fa2,
	      0x0,
	      0x7f7f7f,
	      0x8B4513,
	      0xffffff
	  }; //RRGGBB
	  
	  String logFilePath = "log.txt";
	  private PrintWriter pw;
	  int logfileLines = 0; 
	  
	  int currentActionCounter = 0;
	  List<Thumbnail> thumbnails;
	  float thumbnailsScale = .1f;
	  float thumbnailsX;
	  float thumbnailsMouseOffset;
	  float deltaOffsetX = 0;
	  boolean dragging = false;
	  PGraphics currentThumbnail;
	  boolean sketchLoaded = false;
	  
	  public void setup()
	  {
	    initLogger(logFilePath);
	    
	    //creates the applet window
	    size(displayWidth, displayHeight-100);
	    
	    //white
	    background(255);
	    
	    //4k is 3840 X 2160
	    scale = displayWidth/3840f;
	    
	    //how big each color square will be
	    swatchSize = (this.height - colors.length*swatchPadding) / colors.length;
	    
	    //create the history thumbnails from the log file
	    thumbnails = new ArrayList<Thumbnail>();
	    thumbnails.addAll(createThumbnails());
	    int nThumbnails = thumbnails.size();
	    thumbnailsX = this.width - (this.width*thumbnailsScale) * (nThumbnails + 1.5f); 
	    currentThumbnail = createGraphics((int)(this.width*thumbnailsScale), (int)(this.height*thumbnailsScale));
	    currentThumbnail.beginDraw();
	  }
	  public void draw()
	  {
		  if(thumbnails.isEmpty())
		  {
			  textAlign(CENTER);
			  text("You can restore sketches here, after you drawn something and pressed clear.", 
					  this.width/2, this.height - this.height*thumbnailsScale/2);
			  textAlign(CORNER);
		  }
		  
		  //drag
		dragThumbnails();
		//draw
		drawThumbnails();
		
	    drawArrow();
	    drawColorPicker();
	    
	    //buttons 
	    //button events are handled in mouseclicked
	    drawClearButton(clearX,clearY,clearW,clearH);
	    drawSaveFileButton(saveX, saveY, saveW, saveH);
	    
	    
	    //mouse events
	    if(mousePressed)
	    {
	      //if dragging the thumbnails around
	      if(dragging)
	    	  return;
	      
	      //don't draw if it's inside the swatches.
	      if(mouseX <= swatchSize || mouseX >= this.width - swatchSize) {}
	      else
	      {
	        stroke(currentColor.getRGB());
	        
	        //the eraser is bigger than the colors
	        if(currentColor.equals(Color.white))
	        {
	          strokeWeight(strokeWeight * 10);
	          currentThumbnail.strokeWeight(7.5f); 
	        }
	        else
	        {
	          strokeWeight(strokeWeight);
	          currentThumbnail.strokeWeight(1);
	        }
	          
	        //starting point, we have no prevX/prevY; just an ellipse
	        if(mouseUp)
	        {
	          log(true,mouseX,mouseY);
	          ellipse(mouseX,mouseY,strokeWeight/4,strokeWeight/4);
	        }
	        else //connect to the previous point
	        {
	          line(mouseX,mouseY,prevX, prevY);
	          if(mouseX != prevX && mouseY != prevY)
	          {
	            log(mouseX,mouseY);
	          }
	        }
	        prevX = mouseX;
	        prevY = mouseY;
	      }
	      mouseUp = false;
	    }
	  }
	  
	  private void drawArrow()
	  {
		  strokeWeight(1);
		  int arrowTip = 10;
		  float y = this.height - this.height*thumbnailsScale - arrowTip;
		  line(this.width/3,y, this.width*2/3, y);
		  line(this.width*2/3 - arrowTip,y-arrowTip, this.width*2/3, y);
		  line(this.width*2/3 - arrowTip,y+arrowTip, this.width*2/3, y);
	  }
	  
	  /**
	   * Draws the clear button
	   */
	  private void drawClearButton(float x, float y, float w, float h)
	  {
	    fill(0);
	    textSize(48);
	    text("save and clear", x + this.width*.004f, y + this.height*.03f);
	  }
	  
	  /**
	   * Draws the save file button
	   */
	  private void drawSaveFileButton(float x, float y, float w, float h)
	  {
		  fill(20);
		  textSize(48);
		  text("screenshot", x + this.width*.004f, y + this.height*.03f);
		  
		  //TODO: show the user where it's saved
	  }
	  
	  
	  public void mouseClicked()
	  {
		  //change color
	    if(mouseX <= swatchSize || mouseX >= this.width - swatchSize)
	    {
	      currentColor = new Color(get(mouseX,mouseY));
	      log(currentColor);
	      pw.flush();
	    }
	    
	    //loading thumbnails
	    else if(mouseInThumbnails())
	    {
	    	float thumbnailSize = this.width*thumbnailsScale;
	    	float pos = mouseX - thumbnailsX;
	    	float index = (int)(pos/thumbnailSize);
	    	loadThumbnail((int)index);
	    }
	    
	    //screenshots
	    else if(mouseX > saveX && mouseX < saveY + saveW && mouseY > saveY && mouseY < saveY + saveH)
	      {
	    	saveFrame("whiteboard-######.jpg");
	      }
	    
	    //clear screen
	    else if(mouseX > clearX && mouseX < clearY + clearW && mouseY > clearY && mouseY < clearY + clearH)
	      {
	        background(255);
	        logClear();
	      }
	  }
	public void mousePressed()
	  {
		  //if we're dragging the thumbnails, grab out offset.
		  if(mouseInThumbnails())
		  {
			  dragging = true;
			  thumbnailsMouseOffset  = mouseX - thumbnailsX;
		  }
	  }
	  
	  private boolean mouseInThumbnails()
	  {
		  return mouseY > this.height - this.height*thumbnailsScale;
	  }
	  
	  private void drawColorPicker()
	  {
	    int curY = 0;
	    for(int i = 0; i < colors.length; i++)
	    {
	      int current = colors[i];
	      
	      strokeWeight(swatchPadding);
	      
	      //check if it should be highlighted
	      if((0xff000000 | colors[i]) == currentColor.getRGB())
	        stroke(255,255,0);
	      else
	        stroke(0);
	      //bit operations because we have to add in the alpha bits. Thanks Processing.
	      fill(0xff000000 | current);
	      rect(0, curY, swatchSize, swatchSize); //left side
	      rect(this.width - swatchSize, curY, swatchSize, swatchSize); //right side
	      
	      curY += swatchSize + swatchPadding;
	    }
	  }
	  
	  private void drawThumbnails()
	  {
	    float curX = thumbnailsX;
	    float y = this.height - this.height*thumbnailsScale;
	    
	    int n = thumbnails.size();
	    int start = n >= 9 ? n - 9 : n;
	    start = 0;
	    for(int i = start; i < thumbnails.size(); i++)
	    {
	      Thumbnail tn = thumbnails.get(i);
	      //draw the PGraphics image buffer for this thumbnail
	      image(tn.pg, curX, y);
	      curX += this.width*thumbnailsScale;
	    }
	    
	    //need to enddraw to draw the pgraphics
	    currentThumbnail.endDraw();
	    image(currentThumbnail,curX,y);
	    currentThumbnail.beginDraw();
	    currentThumbnail.stroke(currentColor.getRGB());
	  }
	  
	  /**
	   * Log format
	   * clear means clear
	   * -xxxxxxx are colors
	   * +yyyyyy are initial strokes
	   * zzzz,zzzz are points connected to previous points
	   */
	  private void initLogger(String path) {
	    try {
	      File logFile = new File(logFilePath);
	      println("Log is at: " + logFile.getAbsoluteFile());
	      // no log yet?
	      pw = new PrintWriter(new FileOutputStream(path, true)); //true to append to file
	    } catch (FileNotFoundException e) {
	      e.printStackTrace();
	    }
	  }
	  private void log(int x, int y)
	  {
	    pw.println(x + "," + y);
	    currentThumbnail.line(prevX*thumbnailsScale,prevY*thumbnailsScale,x*thumbnailsScale,y*thumbnailsScale);
	    currentActionCounter++;
	  }
	  private void log(boolean start, int x, int y)
	  {
	    pw.println("+" + x + "," + y);
	    currentThumbnail.point(x*thumbnailsScale,y*thumbnailsScale);
	    currentActionCounter++;
	  }
	  private void log(Color c)
	  {
	    pw.println(c.getRGB());
	    currentThumbnail.stroke(c.getRGB());
	    currentActionCounter++;
	  }
	  private void logClear()
	  {
		sketchLoaded = false;
	    if(currentActionCounter <= 1)
	      return;
	    
	    //this is done too many times
	    pw.println("clear");
	    pw.flush();
	  
	    currentThumbnail.endDraw();
	    thumbnails.add(new Thumbnail(currentThumbnail, logfileLines));
	    currentThumbnail = createGraphics((int)(this.width*thumbnailsScale), (int)(this.height*thumbnailsScale));
	    currentThumbnail.stroke(currentColor.getRGB());
	    log(currentColor.getRGB());
	    logfileLines += currentActionCounter;
	    currentActionCounter = 0;
	    thumbnailsX = this.width - (this.width*thumbnailsScale) * (thumbnails.size() + 1.5f); 
	  }
	  
	  /**
	   * load a thumbnail the user clicked on
	   * here we draw it again to the screen
	   * and also load it in the currentThumbnail :-)
	   * We also re-log it so it gets saved.
	   * 
	   * @param number: which thumbnail to load
	   */
	  private void loadThumbnail(int number)
	  {
		  println(currentActionCounter);
		  println(sketchLoaded);
		  
		  //clear the current drawing
		  logClear();

		  //load the log
		  String []lines = loadStrings(logFilePath);
		  
		  //we need to find the nth drawing
		  int clearCounter = 0;
		  int i = 0;
		  String curLine = lines[0];
		  while(i < lines.length && clearCounter < number)
		  {
			  if(curLine.equals("clear"))
				  clearCounter++;
			  curLine = lines[++i];
		  }
		  
		  Color col = Color.black;
		    logfileLines = lines.length;
		    if(curLine.equals("clear"))
		    {
		    	System.err.println("corrupt log file");
		    	return;
		    }
		    
		    //clear the screens 
		    background(255);
		    currentThumbnail.background(255);
		    
		    stroke(col.getRGB());
		    log(col);
		    //color
		      while(curLine.charAt(0) == '-')
		      {
		        int num = Integer.parseInt(curLine);
		        col = new Color(num);
		        stroke(col.getRGB());
		        log(col);
		        curLine = lines[i++];
		      }
		    
		    
	      //get a line from the log file
	      prevX = Integer.parseInt(curLine.split(",")[0]);
	      prevY = Integer.parseInt(curLine.split(",")[1]);
	      while(i < lines.length && !lines[i].equals("clear"))
	      {
	        curLine = lines[i++];
	        
	        currentThumbnail.strokeWeight(1);
      	  	strokeWeight(strokeWeight);
	        
	        //if it's a color
	        if(curLine.charAt(0) == '-')
	        {
	          int num = Integer.parseInt(curLine);
	          col = new Color(num);
	          stroke(col.getRGB());
	          currentThumbnail.stroke(col.getRGB());
	          if(col == col.white)
	          {
	            currentThumbnail.strokeWeight(7.5f);
	            strokeWeight(strokeWeight*10);
	          }
	          log(col);
	        }
	        
	        //if it's a beginning stroke
	        else if(curLine.charAt(0) == '+')
	        {
	          String []split = curLine.split(",");
	          int nextX = Integer.parseInt(split[0]), nextY = Integer.parseInt(split[1]);
	          
	          //draw the starting point
	          //do it for the main screen
	          ellipse(nextX,nextY,strokeWeight/4,strokeWeight/4);
	          
	          log(true, nextX, nextY);
	          
	          prevX = nextX;
	          prevY = nextY;
	        }

	        //draw a line
	        else 
	        {
	          String []split = curLine.split(",");
	          int nextX = Integer.parseInt(split[0]), nextY = Integer.parseInt(split[1]);
	          
	          
	          line(prevX,prevY,nextX, nextY);
	          log(nextX, nextY);
	          prevX = nextX;
	          prevY = nextY;
	        }
	      }
	      sketchLoaded = true;
	  }

	  /**
	   * Reads the log file and makes thumbnails
	   * Run at launch
	   * @return A list of all the thumbnails in chronological order
	   */
	  private List<Thumbnail> createThumbnails() {
	    List<Thumbnail> thumbnails = new ArrayList<Thumbnail>();
	    
	    String []lines = loadStrings(logFilePath);
	    logfileLines = lines.length;
	    int i = 0;
	    
	    final int qualityThreshold = 10;
	    while(i < lines.length)
	    {
	      if(lines[i].equals("clear"))
	      {
	        i++;
	        continue;
	      }
	        
	      //make a new buffer to draw to
	      PGraphics pg = createGraphics((int)(this.width*thumbnailsScale), (int)(this.height*thumbnailsScale));
	      
	      //Thumbnail object keeps track of which line the thumbnail starts at so we can go back and redraw it 
	      Thumbnail tn = new Thumbnail(pg,i);
	      Color col = Color.black;
	      
	      pg.beginDraw();
	      pg.stroke(col.getRGB());
	      
	      String curLine = lines[i++];
	      if(curLine.charAt(0) == '-')
	      {
	        int num = Integer.parseInt(curLine);
	        col = new Color(num);
	        pg.stroke(col.getRGB());
	        continue;
	      }
	        
	      //get a line from the log file
	      int prevX = Integer.parseInt(curLine.split(",")[0]), prevY = Integer.parseInt(curLine.split(",")[1]);
	      int counter = 0;
	      while(i < lines.length && !lines[i].equals("clear"))
	      {
	        curLine = lines[i++];
	        
	        pg.strokeWeight(1);
	        
	        //if it's a color
	        if(curLine.charAt(0) == '-')
	        {
	          int num = Integer.parseInt(curLine);
	          col = new Color(num);
	          pg.stroke(col.getRGB());
	          if(col == col.white)
	            pg.strokeWeight(7.5f);
	        }
	        
	        //if it's a beginning stroke
	        else if(curLine.charAt(0) == '+')
	        {
	          String []split = curLine.split(",");
	          int nextX = Integer.parseInt(split[0]), nextY = Integer.parseInt(split[1]);
	          
	          //draw the starting point
	          pg.point(nextX,nextY);
	          prevX = nextX;
	          prevY = nextY;
	          counter++;
	        }

	        //draw a line
	        else 
	        {
	          String []split = curLine.split(",");
	          int nextX = Integer.parseInt(split[0]), nextY = Integer.parseInt(split[1]);
	          
	          pg.line(prevX*thumbnailsScale,prevY*thumbnailsScale,nextX*thumbnailsScale, nextY*thumbnailsScale);
	          prevX = nextX;
	          prevY = nextY;
	          counter++;
	        }
	      }
	      pg.endDraw();
	      if(counter > qualityThreshold)
	        thumbnails.add(tn);
	    }
	    
	    return thumbnails;
	  }

	  public void mouseReleased()
	  {
	    mouseUp = true;
	    dragging = false;
	  }
	  
	  public void dragThumbnails()
	  {
		if(!dragging)
			return;
		
		float sizeOfAllThumbnails = (thumbnails.size()+1)*thumbnailsScale*this.width;
		//TODO: bind the slider. use sideOfAllThumbnails
		fill(255);
		noStroke();
		rect(0,this.height-this.height*thumbnailsScale,this.width,this.height*thumbnailsScale);
		thumbnailsX = mouseX - thumbnailsMouseOffset;
	  }
	  
	  public void stop()
	  {
	    pw.flush();
	    pw.close();
	  }
	  
	  //instead of using fonts, draw each letter with lines
	  public void c()
	  {}
	  public void l()
	  {}
	  public void e()
	  {}
	  public void a()
	  {}
	  public void r()
	  {}

}
