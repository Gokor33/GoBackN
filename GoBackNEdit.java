import datalink.Packet;
import datalink.Protocol;

/*
  A go-back n type sliding window protocol
  */

public class GoBackNEdit extends Protocol
{
    int nextBufferToSend;        // buffer to be sent when channel is idle
    int firstFreeBufferIndex;    // buffer to getin which to store next packet
    int nextSequenceNumberExpected;  // sequence number expected
    int firstUnAcknowledged;     // last unacknowledged frame
    final int maximumSequenceNumber;
    int numberOfPacketsStored;
    final int windowSize;
    
    Packet[] buffer;
    double timer;
    
    public GoBackNEdit(int windowSize, double timer)
    {
	super( windowSize, timer);
	numberOfPacketsStored = 0;
	nextBufferToSend = 0;
	firstFreeBufferIndex= 0;
	nextSequenceNumberExpected = 0;
	firstUnAcknowledged = 0;
	maximumSequenceNumber = windowSize;
	this.windowSize = windowSize;
	this.timer = timer;
	buffer = new Packet[windowSize+1];
    }

    public void FrameArrival( Object frame)
    {
	DLLFrame f = (DLLFrame) frame;
	/* a frame has arrived from the physical layer */
	/* check that it is the one that is expected */
	if (f.sequenceNumber == nextSequenceNumberExpected)
	    {
		sendPacket(f.info); /* valid frame, so send it */
	 	                    /* to the network layer */
		nextSequenceNumberExpected = inc( nextSequenceNumberExpected);
		//if the channel is idle send an explicit acknowledgment
		if(isChannelIdle()){
			transmit_ackFrame(nextBufferToSend);
	 	}
	}
	/*if the acknowledgment is -1 then change the expected frame to be 
	the one that didn't get an acknowledgment and provided that the channel
	is idle, re-transmit the frame again. Else, continue with sending the
	rest of the frames/packets*/
	
	if(f.acknowledgment < 0)
	{
		nextBufferToSend = firstUnAcknowledged;
		if ( isChannelIdle() )
		    {
			transmit_frame( nextBufferToSend);
			nextBufferToSend = inc( nextBufferToSend);
		    }
	}
	
	/* if frame n is ACKed then that implies n-1,n-2 etc have also been */
	/* ACKed, so stop associated timers.                                 */
	else{
		while ( between( firstUnAcknowledged,
			 f.acknowledgment,
			 nextBufferToSend) )
	    {
		numberOfPacketsStored--;
		stopTimer(firstUnAcknowledged);
		firstUnAcknowledged = inc( firstUnAcknowledged);
	    }
	}
 	if ( numberOfPacketsStored < windowSize )
	    enableNetworkLayer();
    }
    public void PacketArrival( Packet p)
    {
	DLLFrame f;
	buffer[firstFreeBufferIndex] = p;
	numberOfPacketsStored++;		/* buffer packet */
	if ( numberOfPacketsStored >= windowSize )
	    disableNetworkLayer();

	if ( isChannelIdle() )
	    {
		transmit_frame( nextBufferToSend);
		nextBufferToSend = inc( nextBufferToSend);
	    }
	firstFreeBufferIndex = inc( firstFreeBufferIndex);
    }

    public void TimeOut( int code)
    {
	nextBufferToSend = firstUnAcknowledged;
	if ( isChannelIdle() )
	    {
		transmit_frame( nextBufferToSend);
		nextBufferToSend = inc( nextBufferToSend);
	    }
    }

    public void CheckSumError()
    {
    	 if(isChannelIdle()){
          transmit_nackFrame(nextBufferToSend);
         }
    }

    public void ChannelIdle()
    {
	if ( nextBufferToSend != firstFreeBufferIndex )
	    {
		transmit_frame( nextBufferToSend);
		nextBufferToSend = inc( nextBufferToSend);
	    }
    }

    private boolean between( int a, int b, int c)
    {  /* calculate if a<=b<c circularly */
	if(((a<=b) && (b<c))
	   || ((c<a) && (a<=b))
	   || ((b<c) && (c<a)))
	    return true;
	else
	    return false;
    }

    private int inc ( int a)
    {  /* increment modulo maximum_sequence_number + 1 */
	a++;
	a %= maximumSequenceNumber+1;
	return a;
    }

    private void transmit_frame( int sequenceNumber)
    {
	int acknowledgement;
	/* piggyback acknowledge of last frame receieved */
	acknowledgement = (nextSequenceNumberExpected+maximumSequenceNumber)
	    % (maximumSequenceNumber+1);
	/* send it to physical layer */
	sendFrame( new DLLFrame( sequenceNumber,
				 acknowledgement,
				 buffer[sequenceNumber]));
	startTimer( sequenceNumber, timer);
    }
    
    private void transmit_ackFrame( int sequenceNumber)
    {
	int acknowledgement;
	acknowledgement = (nextSequenceNumberExpected+maximumSequenceNumber)
	    % (maximumSequenceNumber+1);
	/* send explicit acknowledgment */
	sendAckFrame( new DLLFrame( -1,
				 acknowledgement));
    }
    
    private void transmit_nackFrame( int sequenceNumber)
    {
	/* send negative acknowledgment */
	sendNackFrame( new DLLFrame( -1,
				 -1));
    }
}

class DLLFrame
{ /* frame structure */
    int sequenceNumber;
    int acknowledgment;
    datalink.Packet info;

    DLLFrame ( int s, int a, datalink.Packet p)
    {
	info = p;
	sequenceNumber = s;
	acknowledgment = a;
    }
    
    DLLFrame ( int s, int a)
    {
	sequenceNumber = s;
	acknowledgment = a;
    }
}
/*class DLLAckFrame
{
	int sequenceNumber;
	int acknowledgment;
    DLLAckFrame (int s, int a)
    {
    sequenceNumber = s;
    acknowledgment = a;
    }
}
*/

