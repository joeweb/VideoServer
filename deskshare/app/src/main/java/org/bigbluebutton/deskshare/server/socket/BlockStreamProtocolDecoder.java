/**
* BigBlueButton open source conferencing system - http://www.bigbluebutton.org/
* 
* Copyright (c) 2012 BigBlueButton Inc. and by respective authors (see below).
*
* This program is free software; you can redistribute it and/or modify it under the
* terms of the GNU Lesser General Public License as published by the Free Software
* Foundation; either version 3.0 of the License, or (at your option) any later
* version.
* 
* BigBlueButton is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
* PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License along
* with BigBlueButton; if not, see <http://www.gnu.org/licenses/>.
*
*/
package org.bigbluebutton.deskshare.server.socket;

import java.awt.Point;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.bigbluebutton.deskshare.common.Dimension;
import org.bigbluebutton.deskshare.server.events.CaptureEndBlockEvent;
import org.bigbluebutton.deskshare.server.events.CaptureStartBlockEvent;
import org.bigbluebutton.deskshare.server.events.CaptureUpdateBlockEvent;
import org.bigbluebutton.deskshare.server.events.MouseLocationEvent;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

public class BlockStreamProtocolDecoder extends CumulativeProtocolDecoder {
	final private Logger log = Red5LoggerFactory.getLogger(BlockStreamProtocolDecoder.class, "deskshare");
	
	private static final String ROOM = "ROOM";
	private static final byte[] END_FRAME = new byte[] {'D', 'S', '-', 'E', 'N', 'D'};
    private static final byte[] HEADER = new byte[] {'B', 'B', 'B', '-', 'D', 'S'};
    private static final byte CAPTURE_START_EVENT = 0;
    private static final byte CAPTURE_UPDATE_EVENT = 1;
    private static final byte CAPTURE_END_EVENT = 2;
    private static final byte MOUSE_LOCATION_EVENT = 3;
    
    protected boolean doDecode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
    	// Remember the initial position.
        int start = in.position();
        byte[] endFrame = new byte[END_FRAME.length];
        
        // Now find the END FRAME delimeter in the buffer.
        int curpos = 0;
        while (in.remaining() >= END_FRAME.length) {
        	curpos = in.position();
            in.get(endFrame);

            if (Arrays.equals(endFrame, END_FRAME)) {
            	//log.debug("***** END FRAME {} = {}", endFrame, END_FRAME);
                // Remember the current position and limit.
                int position = in.position();
                int limit = in.limit();
                try {
                    in.position(start);
                    in.limit(position);
                    // The bytes between in.position() and in.limit()
                    // now contain a full frame.
                    parseFrame(session, in.slice(), out);
                } finally {
                    // Set the position to point right after the
                    // detected END FRAME and set the limit to the old
                    // one.
                    in.position(position);
                    in.limit(limit);
                }
                return true;
            }

            in.position(curpos+1);
        }

        // Could not find END FRAME in the buffer. Reset the initial
        // position to the one we recorded above.
        in.position(start);
        return false;
    }

    private void parseFrame(IoSession session, IoBuffer in, ProtocolDecoderOutput out) {
    	//log.debug("Frame = {}", in.toString());
     	try {       		
        	byte[] header = new byte[HEADER.length];    
      	
        	in.get(header, 0, HEADER.length);    	
        	
        	if (! Arrays.equals(header, HEADER)) {
	    		log.info("Invalid header. Discarding. {}", header);     
	    		return;
        	}
        	
        	int messageLength = in.getInt();    	

        	if (in.remaining() < messageLength) {
        		log.info("Invalid length. Discarding. [{} < {}]", in.remaining(), messageLength);
        		return;
        	}
        	
        	decodeMessage(session, in, out);
        	
        	return;    		
     	} catch (Exception e) {
	    	log.warn("Failed to parse frame. Discarding.");			
		}    	
    }
    
    private void decodeMessage(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
    	byte event = in.get();
    	switch (event) {
	    	case CAPTURE_START_EVENT:
	    		log.info("Decoding CAPTURE_START_EVENT");
	    		decodeCaptureStartEvent(session, in, out);
	    		break;
	    	case CAPTURE_UPDATE_EVENT:
	    		//log.info("Decoding CAPTURE_UPDATE_EVENT");
	    		decodeCaptureUpdateEvent(session, in, out);
	    		break;
	    	case CAPTURE_END_EVENT:
	    		log.info("Got CAPTURE_END_EVENT event: " + event);
	    		decodeCaptureEndEvent(session, in, out);
	    		break;
	    	case MOUSE_LOCATION_EVENT:
	    		decodeMouseLocationEvent(session, in, out);
	    		break;
	    	default:
    			log.error("Unknown event: " + event);
    			throw new Exception("Unknown event: " + event);  	    	
    	}
    }
        
    private void decodeMouseLocationEvent(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
    	String room = decodeRoom(session, in);
    	if ("".equals(room)) {
    		log.warn("Empty meeting name in decoding mouse location.");
    		throw new Exception("Empty meeting name in decoding mouse location.");
    	}
    	
        int seqNum = in.getInt();
        int mouseX = in.getInt();
        int mouseY = in.getInt();
        	
        /** Swallow end frame **/
        in.get(new byte[END_FRAME.length]);

        MouseLocationEvent event = new MouseLocationEvent(room, new Point(mouseX, mouseY), seqNum);
        out.write(event);    		
    }
    
    private void decodeCaptureEndEvent(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
    	String room = decodeRoom(session, in);
    	if ("".equals(room)) {
    		log.warn("Empty meeting name in decoding capture end event.");
    		throw new Exception("Empty meeting name in decoding capture end event.");
    	}
    	
    	log.info("CaptureEndEvent for " + room);
    	int seqNum = in.getInt();
        	
        /** Swallow end frame **/
        in.get(new byte[END_FRAME.length]);
        	
    	CaptureEndBlockEvent event = new CaptureEndBlockEvent(room, seqNum);
    	out.write(event);
    }
    
    private void decodeCaptureStartEvent(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception { 
    	String room = decodeRoom(session, in);
    	if ("".equals(room)) {
    		log.warn("Empty meeting name in decoding capture start event.");
    		throw new Exception("Empty meeting name in decoding capture start event.");
    	}
    	
        session.setAttribute(ROOM, room);
        int seqNum = in.getInt();
        	
    	Dimension blockDim = decodeDimension(in);
    	Dimension screenDim = decodeDimension(in);    	
    	
    	boolean useSVC2 = (in.get() == 1);
    	
        /** Swallow end frame **/
        in.get(new byte[END_FRAME.length]);
        			
        log.info("CaptureStartEvent for " + room);
        CaptureStartBlockEvent event = new CaptureStartBlockEvent(room, screenDim, blockDim, seqNum, useSVC2);	
        out.write(event);    		
    }
    
    private Dimension decodeDimension(IoBuffer in) {
    	int width = in.getInt();
    	int height = in.getInt();
		return new Dimension(width, height);
    }
       
    private String decodeRoom(IoSession session, IoBuffer in) {
    	int roomLength = in.get();
//    	System.out.println("Room length = " + roomLength);
    	String room = "";
    	try {    		
    		room = in.getString(roomLength, Charset.forName( "UTF-8" ).newDecoder());
    		if (session.containsAttribute(ROOM)) {
        		String attRoom = (String) session.getAttribute(ROOM);
        		if (!attRoom.equals(room)) {
        			log.warn(room + " is not the same as room in attribute [" + attRoom + "]");
        		}     			
    		}   		
		} catch (CharacterCodingException e) {
			log.error(e.getMessage());
		}   
		
		return room;
    }
    
    private void decodeCaptureUpdateEvent(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
    	String room = decodeRoom(session, in);
    	if ("".equals(room)) {
    		log.warn("Empty meeting name in decoding capture start event.");
    		throw new Exception("Empty meeting name in decoding capture start event.");
    	}
    	
        int seqNum = in.getInt();
        int numBlocks = in.getShort();

        String blocksStr = "Blocks changed ";
        	
        for (int i = 0; i < numBlocks; i++) {
            int position = in.getShort();
            blocksStr += " " + position;
            	
            boolean isKeyFrame = (in.get() == 1) ? true : false;
            int length = in.getInt();
            byte[] data = new byte[length];
            in.get(data, 0, length);    	
            CaptureUpdateBlockEvent event = new CaptureUpdateBlockEvent(room, position, data, isKeyFrame, seqNum);
            out.write(event);    		
        }
        	
        /** Swallow end frame **/
        in.get(new byte[END_FRAME.length]);   		   	
    }
}
