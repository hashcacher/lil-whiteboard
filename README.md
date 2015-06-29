# lil-whiteboard
a simple whiteboard with a vector-based memory log

Requires Java 7 or higher.

Saves everything ever drawn in log.txt.
You can scroll through thumbnails of everything ever drawn 
You can restore the thumbnails
You can take screenshots

##To Do
* Important: Read the log as a stream instead of buffered.
** don't hold all the thumbnails in memory

* Replace all new Color(hexcolor) with 0xff0000 | hexcolor
* stop the thumbnails from scrolling too far
* reduce redundant logs