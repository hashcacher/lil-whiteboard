package com.vidi.whiteboard;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.swing.text.StyledEditorKit.FontSizeAction;

import processing.core.*;

//TODO: Read the log as a stream instead of buffered; don't hold all the thumbnails in memory

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
	  float saveX = swatchSize+140, saveY = 120, saveW = 345, saveH = 50;
	  float clearX = swatchSize+140, clearY = 50, clearW = 300, clearH = 50;
	  
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
	  int currentLogLine = 0; 
	  int logFileTotalLines = 0; 
	  
	  int currentActionCounter = 0;
	  List<Thumbnail> thumbnails;
	  float thumbnailsScale = .1f;
	  float thumbnailsX;
	  float thumbnailsW;
	  float thumbnailsH;
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
	    
	    //set the size of thumbnails
	    thumbnailsW = this.width*thumbnailsScale;
	    thumbnailsH = this.height*thumbnailsScale;

	    //create the history thumbnails from the log file
	    thumbnails = new ArrayList<Thumbnail>();
	    thumbnails.addAll(createThumbnails());

	    int nThumbnails = thumbnails.size(); //this is the same as logFileTotalLines =P
	    //set the offset of the thumbnails
	    thumbnailsX = this.width - (this.width*thumbnailsScale) * (nThumbnails + 1.5f); 

	    
	    //start up the current thumbnail
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
			  textAlign(LEFT);
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

		  textSize(16);
		  text("drag me", this.width/2, y - 10);
	  }
	  
	  /**
	   * Draws the clear button
	   */
	  private void drawClearButton(float x, float y, float w, float h)
	  {
	    fill(0);
	    textSize(48);
	    text("save and clear", x, y);
	  }
	  
	  /**
	   * Draws the save file button
	   */
	  private void drawSaveFileButton(float x, float y, float w, float h)
	  {
		  fill(20);
		  textSize(48);
		  text("screenshot", x , y);
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
	    else if(mouseX > saveX && mouseX < saveX + saveW && mouseY > saveY && mouseY < saveY + saveH)
	      {
	    	saveFrame("whiteboard-######.jpg");
	      }
	    
	    //clear screen
	    else if(mouseX > clearX && mouseX < clearX + clearW && mouseY > clearY && mouseY < clearY + clearH)
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
	    float y = this.height - thumbnailsH;
	    
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
	   * each drawing on its own line
	   * each instruction separated by spacebar
	   * -xxxxxxx are colors
	   * +yyy,yyy are initial strokes
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
	    pw.print(x + "," + y + " ");
	    currentThumbnail.line(prevX*thumbnailsScale,prevY*thumbnailsScale,x*thumbnailsScale,y*thumbnailsScale);
	    currentActionCounter++;
	  }
	  private void log(boolean start, int x, int y)
	  {
	    pw.print("+" + x + "," + y + " ");
	    currentThumbnail.point(x*thumbnailsScale,y*thumbnailsScale);
	    currentActionCounter++;
	  }
	  private void log(Color c)
	  {
	    pw.print(c.getRGB() + " ");
	    currentThumbnail.stroke(c.getRGB());
	    currentActionCounter++;
	  }
	  private void logClear()
	  {
	    if(currentActionCounter <= 1)
	      return;
	    
	    pw.println("");
	    pw.flush();
	  
	    //need to enddraw before drawing PGraphics
	    currentThumbnail.endDraw();
	    
	    //save the current thumbnail
	    thumbnails.add(new Thumbnail(currentThumbnail, logFileTotalLines++));
	    
	    //make a new current thumbnail
	    currentThumbnail = createGraphics((int)thumbnailsW, (int)thumbnailsH);
	    currentThumbnail.stroke(currentColor.getRGB());
	    
	    //start the new sketch with our current color
	    log(currentColor.getRGB());
	    
	    //reset action counter
	    currentActionCounter = 0;
	    
	    //move the thumbnails to the end
	    thumbnailsX = this.width - (this.width*thumbnailsScale) * (thumbnails.size() + 1.5f); 
	    
	    //redraw some stuff
	    drawArrow();
	    drawColorPicker();
	  }
	  
	  /**
	   * load a thumbnail the user clicked on
	   * here we draw it again to the screen
	   * and also load it in the currentThumbnail	 :-)
	   * We also re-log it so it gets saved.
	   * 
	   * @param number: which thumbnail to load
	   */
	  private void loadThumbnail(int number)
	  {
	  }

	  /**
	   * Reads the log file and makes thumbnails
	   * Run at launch
	   * @return A list of all the thumbnails in chronological order
	   */
	  private List<Thumbnail> createThumbnails() {
		  List<Thumbnail> thumbnails = new ArrayList<Thumbnail>();
		  
		  //read the log file
		  Path path = Paths.get(logFilePath);
		    //The stream hence file will also be closed here
		    try(Stream<String> lines = Files.lines(path)){
		        lines.forEachOrdered(
		        		e -> thumbnails.add(createThumbnail(readLogLineCreateThumbnail(e))));
		    } catch (IOException e1) {
				e1.printStackTrace();
			}
		  
		  return thumbnails;
	  }
	  
	  private Thumbnail createThumbnail(PGraphics pg)
	  {
		  return new Thumbnail(pg, logFileTotalLines++);
	  }
	  
	  private PGraphics readLogLineCreateThumbnail(String line)
	  {
		  final float normStrokeWeight = 1;
		  final float whiteStrokeWeight = 7.5f;
		  
		  PGraphics pg = createGraphics((int)thumbnailsW, (int)thumbnailsH);
		  pg.beginDraw();
		  
		  String []pieces = line.split(" ");
		  float prevX = 0, prevY = 0;
		  for(String instruction : pieces)
		  {
			  if(instruction.length() == 0)
				  continue;
			  
			  //color
			  if(instruction.charAt(0) == '-' || instruction.charAt(0) == '0')
			  {
				  //add alpha to the color
				  int color = fixColor(Integer.parseInt(instruction));
				  
				  //eraser is bigger
				  if(color == 0xffffff)
					  pg.strokeWeight(whiteStrokeWeight);
				  else
					  pg.strokeWeight(normStrokeWeight);

				  //finally, set the stroke
				  pg.stroke(color);
			  }
			  
			  //starting stroke
			  else if(instruction.charAt(0) == '+')
			  {
				  String[] xandy = instruction.split(",");
				  prevX = Integer.parseInt(xandy[0]) * thumbnailsScale;
				  prevY = Integer.parseInt(xandy[1]) * thumbnailsScale;
				  point(prevX,prevY);
			  }
			  
			  else
			  {
				  String[] xandy = instruction.split(",");
				  float x = Integer.parseInt(xandy[0]) * thumbnailsScale;
				  float y = Integer.parseInt(xandy[1]) * thumbnailsScale;
				  
				  //connect to previous point
				  line(prevX, prevY, x, y);
				  
				  //current point is now the previous point
				  prevX = x;
				  prevY = y;
			  }
		  }
		  
		  pg.endDraw();
		  return pg;
	  }

	  private int fixColor(int col)
	  {
		  return 0xff0000 | col;
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
