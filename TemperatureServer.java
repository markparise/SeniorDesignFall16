import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TemperatureServer {
	static final float ERR_UNPLUGGED = -111;
	static final float ERR_PI_NOT_CONNECTED = -222;
	
	static float[] data = new float[300]; //temp data
	static float[] timeData = new float[300]; //time associated with data, time since server started
	static long serverCreationTime;
	static boolean isVirtualButtonPressed = false;
	static boolean isSensorPluggedIn = true;//starts true will be corrected by pi message if wrong
	static int placement;
	static int amountOfData = 0;
	static boolean newDataToSend = false;
	static boolean isPiConnected = false;
	static long timeOfLastRPiRead =0;//Used to tell when Rpi is no longer connected
	
	private static ExecutorService executor;
	
	final static String hostName = "127.0.0.1"; // Localhost for testing
	final static int portNumber = 61088;//Set to port 0 if you don't know a specific open port
	
	
	public static void main(String[] args) throws IOException {

		placement = 0;	
		executor = Executors.newCachedThreadPool();	
		serverCreationTime = System.nanoTime();
		
	     try ( ServerSocket Server = new ServerSocket(portNumber)) 
	     {
	    	
	    	System.out.printf("Temp server is listening on port %d\n",Server.getLocalPort());
    		boolean waitForConnection = true;
    		while(waitForConnection)
    		{
    			//Server.setSoTimeout(180*1000);//2 min timeout during server.accept
    			System.out.print("server awaiting connection\n");
    			Socket newSocket = Server.accept();
    			System.out.println("Connection recieved.");
    			BufferedReader in = new BufferedReader(new InputStreamReader(newSocket.getInputStream()));
    	
    			String idMessage = in.readLine();
    			System.out.printf("Message Recieved: |%s|\n", idMessage);
    			System.out.flush();

    			if(idMessage.equals("Pi"))
    			{
    				System.out.println("Pi connected");
    				isPiConnected = true;
    				communicateWithPi recieveInputFromPi = new communicateWithPi(newSocket);
    				executor.execute(recieveInputFromPi);
    			}
    			else if(idMessage.equals("PC"))
    			{
    				System.out.println("PC connected");
    				communicateWithPC pcThread = new communicateWithPC(newSocket, in);
    				executor.execute(pcThread);
    			}
    			else
    			{
    				System.err.println("Invalid ID message recieved.\n");
    			}
    		} 	 
	    	 
	     }catch (java.net.SocketTimeoutException e)
	     {
	    	 System.err.println("Server timedout.");
		     System.exit(-1);  	 
	     }  catch (IOException e) {
	        System.err.println("Could not listen on port " + portNumber);
	        System.exit(-1);
	     } 
		
		executor.shutdown();
	}
	

	/*
	 * goal for this function:
	 * check for input from client, if we have input we will compare it to SEND_ALL_DATA or BUTTON_PRESSED
	 * do what we're required to do with the input and then go back to checking for inputs 
	 */
	private static class communicateWithPC implements Runnable {
		private Socket pcSocket;
		BufferedReader inFromPC;
		communicateWithPC(Socket pcSocket, BufferedReader in)
		{
			this.pcSocket = pcSocket;
			this.inFromPC = in;
		}
		
		@Override
		public void run() {
			boolean connected= true;
			
			try(
				ObjectOutputStream outputToPC = new ObjectOutputStream(pcSocket.getOutputStream());
				) 
			
			{
				//initial data sent on connection
				outputToPC.writeObject(data);
				outputToPC.writeObject(timeData);
				int dataInfo[] = {placement, amountOfData};
				outputToPC.writeObject(dataInfo);
				
				while(connected)
				{	
					//Make sure pi is still active
					if(isPiConnected && timeOfLastRPiRead != 0)
					{	
						long elapsedNanos = System.nanoTime() - timeOfLastRPiRead;
						float elapsedTimeSec = TimeUnit.NANOSECONDS.toMillis(elapsedNanos)/1000.0f;;
						System.out.printf("pi last read: %f\n",elapsedTimeSec);
						if(elapsedTimeSec >= 5)
						{
							System.err.println("Pi is inactive");
							timeOfLastRPiRead = 0;
							isPiConnected = false;
						}
					}
				
				
					
					
					if(isPiConnected)
					{
						newDataToSend = true;
						if(newDataToSend)
						{
							int lastPlacement = placement-1;
							if(lastPlacement == -1 ) 
							{
								lastPlacement=299;			
							}
							outputToPC.writeFloat(data[lastPlacement]);
							outputToPC.flush();
							outputToPC.writeFloat(timeData[lastPlacement]);
							outputToPC.flush();
							String virtualButtonMess = inFromPC.readLine();
							isVirtualButtonPressed = Boolean.parseBoolean(virtualButtonMess);
							System.out.printf("From PC virtual button: %s\n",virtualButtonMess);
						
						}
					}
					else
					{
						outputToPC.writeFloat(ERR_PI_NOT_CONNECTED);
						outputToPC.flush();
						outputToPC.writeFloat(secElapsedSinceServerStart());
						outputToPC.flush();
						String virtualButtonMess = inFromPC.readLine();
						isVirtualButtonPressed = Boolean.parseBoolean(virtualButtonMess);
						Thread.sleep(500);
					}
						
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
				}
				
				
			} catch (IOException e1) {
				System.err.println("IOException\n");
				e1.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	/*
	 * goal for this function:
	 * receives temp data and unplugged sensor error from pi
	 * always responds to the pi with isVirtualButtonPressed
	 */
	private static class communicateWithPi implements Runnable {
		private Socket PiSocket;
		
		communicateWithPi(Socket PiSocket)
		{
			this.PiSocket = PiSocket;
		}
		
		
		@Override
		public void run() {
			try {
    			PrintWriter outputToPi = new PrintWriter(PiSocket.getOutputStream(), true);
    			BufferedReader inFromPi = new BufferedReader(new InputStreamReader(PiSocket.getInputStream()));

    			boolean piConnected = true;
    			while(piConnected)
    			{
    			
					String piMessage = inFromPi.readLine();
					
					if(piMessage!=null)
					{
						
						if(  piMessage.equals("Bye"))
						{
							piConnected = false;
						}
						else if( piMessage.equals("ErrUn"))
						{
							newDataToSend = true;
							isSensorPluggedIn = false;
							if(amountOfData <=300) 
								amountOfData += 1;
							
							synchronized(data)
							{
								data[placement] = ERR_UNPLUGGED;
							}
							float elapsedTimeFloat = secElapsedSinceServerStart();
							synchronized(timeData)
							{
								timeData[placement] = elapsedTimeFloat;
							}
							System.out.printf("%s recieved at %f\n",piMessage, elapsedTimeFloat);
							placement++;
							if (placement == 299) 
							{
								placement = 0;
							}
							
							
							
							System.out.println("Sending VirtualButton to pi");
							outputToPi.print(String.valueOf(isVirtualButtonPressed));
							outputToPi.flush();
							timeOfLastRPiRead = System.nanoTime();
						}
						else
						{
							newDataToSend = true;
							isSensorPluggedIn = true;
							if(amountOfData <=300) 
								amountOfData += 1;
							
							synchronized(data)
							{
								data[placement] = Float.parseFloat(piMessage);
							}
							float elapsedTimeFloat = secElapsedSinceServerStart();
							synchronized(timeData)
							{
								timeData[placement] = elapsedTimeFloat;
							}
							System.out.printf("%s recieved at %f\n",piMessage, elapsedTimeFloat);
							placement++;
							if (placement == 299) 
							{
								placement = 0;
							}
							System.out.println("Sending VirtualButton to pi");

							outputToPi.print(String.valueOf(isVirtualButtonPressed));
							outputToPi.flush();
							timeOfLastRPiRead = System.nanoTime();
						}
					}
    			}
    			
    			System.out.println("Connection with Pi terminated.");
    			outputToPi.close();
    			inFromPi.close();
    			PiSocket.close();
			} catch (IOException e) {
				System.err.println("Error in getting stream from socket\n");
				isPiConnected = false;
				e.printStackTrace();
			}finally
			{
				isPiConnected= false;
			}
			
		}
	}
	
	/*
	 * returns seconds since server's creation
	 */
	private static float secElapsedSinceServerStart()
	{
		long elapsedTimeNano = System.nanoTime()- serverCreationTime;
		long elapsedTimeMilli = TimeUnit.NANOSECONDS.toMillis(elapsedTimeNano);
		return elapsedTimeMilli/1000.0f;
	}
	
	/**
	 * Temporary function for random values
	 */
	void randomData() {
		int i;
		for (i=0; i<300; i++) {
			// data must be >=10 && <=50
			data[i] = (int) (10 + Math.random()*40);
		}
	}
	
	
}
