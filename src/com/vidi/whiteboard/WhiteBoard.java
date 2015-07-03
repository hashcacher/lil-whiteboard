package com.vidi.whiteboard;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import processing.core.*;

//TODO: Read the log as a stream instead of buffered; don't hold all the thumbnails in memory

//TODO: Replace all new Color(hexcolor) with 0xff0000 | hexcolor
//TODO: stop the thumbnails from scrolling too far
//TODO: fix thumbnail reload color bugs
//TODO: reduce redundant logs
public class WhiteBoard extends PApplet{

	private static final long serialVersionUID = 2L;
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
	  
	  String logFolder = "drawings/";
	  File directory;
	  private PrintWriter pw;
	  int currentLogFile = 0; 
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
	    initLogger(logFolder);
	    
	    //creates the applet window
	    size(displayWidth, displayHeight-100);
	    
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
	    currentLogFile = logFileTotalLines;
	    
	    //set the offset of the thumbnails
	    int nThumbnails = thumbnails.size(); //this is the same as logFileTotalLines =P
	    thumbnailsX = this.width - (this.width*thumbnailsScale) * (nThumbnails + 1.5f); 

	    
	    //start up the current thumbnail
	    currentThumbnail = createGraphics((int)thumbnailsW, (int)thumbnailsH);
	    currentThumbnail.beginDraw();

	    //draw the UI
	    background(255);
	    drawUI();
	  }
	  
	  private void drawUI()
	  {
		    drawColorPicker();
		    drawArrow();

		    //buttons 
		    //button events are handled in mouseclicked
		    drawClearButton(clearX,clearY,clearW,clearH);
		    drawSaveFileButton(saveX, saveY, saveW, saveH);
	  }
	  
	  public void draw()
	  {
		  System.out.println(currentActionCounter);
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
	      
	      //gotta redraw
	      drawColorPicker();
	    }
	    
	    //loading thumbnails
	    else if(mouseInThumbnails())
	    {
	    	float thumbnailSize = this.width*thumbnailsScale;
	    	float pos = mouseX - thumbnailsX;
	    	float index = (int)(pos/thumbnailSize);
	    	if((int) index != currentLogFile)
	    		loadThumbnail((int)index);
	    }
	    
	    //screenshots
	    else if(mouseInScreenshot())
	      {
	    	saveFrame("whiteboard-######.jpg");
	      }
	    
	    //clear screen
	    else if(mouseInClear())
	      {
	        fill(255);
	        stroke(0);
	        strokeWeight(3);
	        rect(swatchSize, 0, this.width - 2*swatchSize, this.height);
//	        drawArrow();
	        drawUI();
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
	    	if(curX > this.width - swatchSize*3f)
	    		break;
	    	
	    	if(curX > swatchSize)
	    	{

	    		Thumbnail tn = thumbnails.get(i);
		      //draw the PGraphics image buffer for this thumbnail
		      image(tn.pg, curX, y);
	    	}
	    	curX += this.width*thumbnailsScale;
	    }
	    
	    //need to enddraw to draw the pgraphics
	    currentThumbnail.endDraw();
	    image(currentThumbnail,curX,y);
	    currentThumbnail.beginDraw();
	    currentThumbnail.stroke(currentColor.getRGB());
	  }
	  
	  private boolean mouseInClear()
	  {
		    return mouseX > clearX && mouseX < clearX + clearW && mouseY > clearY && mouseY < clearY + clearH;
	  }
	  
	  private boolean mouseInScreenshot()
	  {
		  return mouseX > saveX && mouseX < saveX + saveW && mouseY > saveY && mouseY < saveY + saveH;
	  }
	  
	  /**
	   * Log format
	   * each drawing in it's own file
	   * each instruction separated by spacebar
	   * -xxxxxxx are colors
	   * +yyy,yyy are initial strokes
	   * zzzz,zzzz are points connected to previous points
	   */
	  private void initLogger(String path) {
	    try {
	    	directory = new File(path);
	    	if(directory.isDirectory())
	    	{
	    		pw = new PrintWriter(new FileOutputStream(getLatestFile(directory)));
	    	}
	    	else
	    	{
	    		new File(logFolder).mkdir();
	    		pw = new PrintWriter(new FileOutputStream("0"));
	    	}
	    	
//	      File logFile = new File(logFolder);
	      // no log yet?
	    } catch (FileNotFoundException e) {
	      e.printStackTrace();
	    }
	  }
	  
	  private String getLatestFile(File directory)
	  {
		  int max = 0;
		  File lastFile = null;
		  for (final File fileEntry : directory.listFiles()) 
		  {
			  try
			  {
				  int fileName = Integer.parseInt(fileEntry.getName());
				  max = Math.max(max, fileName);
				  lastFile = fileEntry;
			  }
			  catch(NumberFormatException|IndexOutOfBoundsException e){ //it's okay
			  }
		  }
		  
		  //overwrite the previous, zero size file
		  if(lastFile != null && lastFile.length() == 0)
		  {
			  System.out.println("Current file is: " + logFolder + String.valueOf(max));
			  return logFolder + String.valueOf(max);
		  }
		  else
		  {
			  System.out.println("Current file is: " + logFolder + String.valueOf(max+1));
			  return logFolder + String.valueOf(max+1);
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
		  //ignore if we're pressing a button.
		    if(mouseInClear() || mouseInScreenshot())
		    	return;
		    			
	    pw.print("+" + x + "," + y + " ");
	    currentThumbnail.point(x*thumbnailsScale,y*thumbnailsScale);
	    currentActionCounter++;
	  }
	  private void log(Color c)
	  {
	    pw.print(c.getRGB() + " ");
	    currentThumbnail.stroke(c.getRGB());
	  }
	  private void logClear()
	  {
	    if(currentActionCounter <= 1)
	    {
	    	currentActionCounter = 0;
	      return;
	    }

	    //need to enddraw before drawing PGraphics
	    currentThumbnail.endDraw();
	    
	    pw.flush();
	    
	    newThumbnail();
	    
	    //reset action counter
	    currentActionCounter = 1;
	    
	    //move the thumbnails to the end
	    thumbnailsX = this.width - thumbnailsW * (thumbnails.size() + 1.5f); 
	    
	    //redraw some stuff
	    drawArrow();
	    drawColorPicker();
	  }
	  
	  private void newThumbnail()
	  {
		    //save the current thumbnail
		    thumbnails.add(new Thumbnail(currentThumbnail, logFileTotalLines++));
		    currentLogFile = logFileTotalLines;
		    
		    //make a new current thumbnail
		    currentThumbnail = createGraphics((int)thumbnailsW, (int)thumbnailsH);
		    currentThumbnail.stroke(currentColor.getRGB());
		    
		    
		    //get next log file
		    String nextFile = getLatestFile(directory);
		    try {
				pw = new PrintWriter(new FileOutputStream(nextFile));
			} catch (FileNotFoundException e) {
				System.err.println("newThumbnail file not found");
				e.printStackTrace();
			}

		    //start the new sketch with our current color
		    log(currentColor);
	  }
	  
	  /**
	   * load a thumbnail the user clicked on
	   * here we draw it again to the screen
	   * and also load it in the currentThumbnail	 :-)
	   * 
	   * @param number: which thumbnail to load
	   */
	  private void loadThumbnail(int number)
	  {
		  //first seek to the nth line
		  FileInputStream fs;
		  String line = null;
			try {
				fs = new FileInputStream(logFolder);
				  BufferedReader br = new BufferedReader(new InputStreamReader(fs));
				  for(int i = 0; i < number; ++i)
				    br.readLine();
				  line = br.readLine();
				  br.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			if(line == null)
				System.err.println("loadThumbnail #" + number + " failed.");

			else
			{
				currentThumbnail.endDraw();
				currentThumbnail = createGraphics((int)thumbnailsW, (int)thumbnailsH);
				currentThumbnail.beginDraw();
				loadLine(line);
			}
	  }
	  
	  private void loadLine(String line)
	  {
//		  final float normStrokeWeight = 1;
//		  final float whiteStrokeWeight = 7.5f;
//		  
//		  
//		  String []pieces = line.split(" ");
//		  float prevX = 0, prevY = 0;
//		  for(String instruction : pieces)
//		  {
//			  if(instruction.length() == 0)
//				  continue;
//			  
//			  //color
//			  if(instruction.charAt(0) == '-' || instruction.charAt(0) == '0')
//			  {
//				  //add alpha to the color
//				  int color = Integer.parseInt(instruction);
//				  
//				  //eraser is bigger
//				  if(color == 0xffffff)
//					  currentThumbnail.strokeWeight(whiteStrokeWeight);
//				  else
//					  pg.strokeWeight(normStrokeWeight);
//
//				  //finally, set the stroke
//				  pg.stroke(color);
//			  }
//			  
//			  //starting stroke
//			  else if(instruction.charAt(0) == '+')
//			  {
//				  String[] xandy = instruction.split(",");
//				  prevX = Integer.parseInt(xandy[0]) * thumbnailsScale;
//				  prevY = Integer.parseInt(xandy[1]) * thumbnailsScale;
//				  pg.point(prevX,prevY);
//			  }
//			  
//			  else
//			  {
//				  String[] xandy = instruction.split(",");
//				  float x = Integer.parseInt(xandy[0]) * thumbnailsScale;
//				  float y = Integer.parseInt(xandy[1]) * thumbnailsScale;
//				  
//				  //connect to previous point
//				  pg.line(prevX, prevY, x, y);
//				  
//				  //current point is now the previous point
//				  prevX = x;
//				  prevY = y;
//			  }
//		  }
//		  
//		  pg.endDraw();
//		  return pg;
	  }

	  /**
	   * Reads the log file and makes thumbnails
	   * Run at launch
	   * @return A list of all the thumbnails in chronological order
	   */
	  private List<Thumbnail> createThumbnails() {
		  List<Thumbnail> thumbnails = new ArrayList<Thumbnail>();
		  
		  //read the log file
		  Path path = Paths.get(logFolder);
		    //The stream hence file will also be closed here

		  //parse in all the files that are numbers
		    try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) 
		    {
		           for (Path entry: stream) 
		           {
		 			  try
					  {
						  int fileName = Integer.parseInt(entry.getName(entry.getNameCount()-1).toString());
						  
						  FileInputStream fs;
						  String line = null;
							try {
								fs = new FileInputStream(entry.toString());
								System.out.println(entry.toString());
								  BufferedReader br = new BufferedReader(new InputStreamReader(fs));
								  line = br.readLine();
								  if(line == null)
									  continue;
								  else
									  thumbnails.add(createThumbnail(readLogLineCreateThumbnail(line)));
								  br.close();
									  
							} finally {}
							
					  }
					  catch(NumberFormatException e){ //it's okay
					  }
		           }
		       } catch (Throwable ex) {
		           // I/O error encounted during the iteration, the cause is an IOException
		           System.err.println( ex.getCause() );
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
				  int color = Integer.parseInt(instruction);
				  
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
				  pg.point(prevX,prevY);
			  }
			  
			  else
			  {
				  String[] xandy = instruction.split(",");
				  float x = Integer.parseInt(xandy[0]) * thumbnailsScale;
				  float y = Integer.parseInt(xandy[1]) * thumbnailsScale;
				  
				  //connect to previous point
				  pg.line(prevX, prevY, x, y);
				  
				  //current point is now the previous point
				  prevX = x;
				  prevY = y;
			  }
		  }
		  
		  pg.endDraw();
		  return pg;
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
		
		float sizeOfAllThumbnails = (thumbnails.size()+1)*thumbnailsW;
		//TODO: bind the slider. use sideOfAllThumbnails
		fill(255);
		noStroke();
		
		//gotta clear the thumbnails
		rect(0,this.height-thumbnailsH,this.width,thumbnailsH);
		thumbnailsX = mouseX - thumbnailsMouseOffset;
		
		//redraw the color picker
		drawColorPicker();
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
