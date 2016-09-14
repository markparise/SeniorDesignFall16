
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.AnimationTimer;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;


/**
 * A chart that fills in the area between a line of data points and the axes.
 * Good for comparing accumulated totals over time.
 * 
* @see javafx.scene.chart.Chart
 * @see javafx.scene.chart.Axis
 * @see javafx.scene.chart.NumberAxis
 * @related charts/line/LineChart
 * @related charts/scatter/ScatterChart
 */

public class TemperatureChart extends Application {
	static final float ERR_UNPLUGGED = -111;
	static final float ERR_PI_NOT_CONNECTED = -222;
	
    private static final int MAX_DATA_POINTS = 300;
    private static final float MAX_TEMP = 30.0f;
    private static final float MIN_TEMP = 20.0f;
    private static long timeOfLastTempWarning = -1;
    		
    private Series series; // made this static doesnt seem to break
    private int xSeriesData = 0;
    private ConcurrentLinkedQueue<Number> dataQ = new ConcurrentLinkedQueue<Number>();
    private ExecutorService executor;
    private CommunicateWithServerAndAddToQueue addToQueue;
    private Timeline timeline2;
    private NumberAxis xAxis;
	private String lastDataPoint = "";
    private static boolean initialDataSetup = false;
    
    private static String mode = " C";
    private Pane root;
    
    private final static String serverAddress = "127.0.0.1";//"50.82.161.220"
    private final static int serverPort = 61088;
    private final static String sendAllData = "send300";
    private final static String sendButtonPress = "buttonpress";
    private static Socket s;
    private static BufferedReader input;

    
    private float tempData[] = new float[300];
    private float timeData[] = new float[300]; //time index corresponds with temp index
    private int placement = 0; //Most recent data, ranges 0-299 current position in array
    private int amountOfData = 0;//ranges 0-300 temps recorded
    private boolean isVirtualButtonPressed = false; //toggled by gui button, sent by socket thread
    private boolean applicationRunning = true;
    private boolean inErrorState = false;
    
    private void init(Stage primaryStage) {
        xAxis = new NumberAxis(0,MAX_DATA_POINTS,MAX_DATA_POINTS/10);
        xAxis.setForceZeroInRange(false);
        xAxis.setAutoRanging(false);
        
        NumberAxis yAxis = new NumberAxis(10, 50, 5);
        yAxis.setAutoRanging(false);

        //-- Chart
        final AreaChart<Number, Number> sc = new AreaChart<Number, Number>(xAxis, yAxis) {
            // Override to remove symbols on each data point
            @Override protected void dataItemAdded(Series<Number, Number> series, int itemIndex, Data<Number, Number> item) {}
        };
        sc.setAnimated(false);
        sc.setId("liveAreaChart");
        sc.setTitle("Real Time Temperature");

        //-- Button
        final Button b = new Button();
        b.setId("virtualbutton");
        b.setText("Display LED temperature");
        b.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent event) {
				System.out.println("Virtual button pressed");
				if (isVirtualButtonPressed == false)
				{
					isVirtualButtonPressed = true;
				}
				else
				{
					isVirtualButtonPressed = false;
				}
			}
        	
        });
        
        //--Other button
        final Button tempSwitcher = new Button();
        tempSwitcher.setId("tempSwitcher");
        tempSwitcher.setText("Toggle C/F");
        tempSwitcher.setOnAction(new EventHandler<ActionEvent>() {
 
            @Override
            public void handle(ActionEvent arg0) {
            	if (!lastDataPoint.isEmpty()) {
            		if (mode.matches(" C")) {
	                	mode = " F";
	                	float temp = Integer.parseInt(lastDataPoint);
	                	temp = (float) ((temp * 1.8) + 32);
	                	Math.round(temp);
	                	lastDataPoint = "" + (int) temp;
	                } else {
	                	mode = " C";
	                	float temp = Integer.parseInt(lastDataPoint);
	                	temp = (float) ((temp - 32) * (.55555));
	                	Math.round(temp);
	                	lastDataPoint = "" + (int) temp;
	                }
            	} else {
            		if (mode.matches(" C")) {
	                	mode = " F";
            		} else {
            			mode = " C";
            		}
            	}
                series.setName(lastDataPoint + mode);
            }
           
        });
        //-- Chart Series
        series = new AreaChart.Series<Number, Number>();
        series.setName("Area Chart Series");
        series.setName("N/A" + mode);
        sc.getData().add(series);
		
		//javafx.scene.control.ScrollBar
        ScrollBar zoom = new ScrollBar();
        zoom.setOrientation(Orientation.VERTICAL);
        zoom.setMaxHeight(350);
        zoom.setMinHeight(350);
        zoom.setValue(100);
        zoom.setMax(200);
        
        sc.setScaleX(zoom.getValue()/100);
        sc.setScaleY(zoom.getValue()/100);
        zoom.valueProperty().addListener(new ChangeListener<Number>() {

			@Override
			public void changed(ObservableValue<? extends Number> arg0, Number arg1, Number arg2) {
				sc.setScaleX(arg2.doubleValue()/100);
				sc.setScaleY(arg2.doubleValue()/100);
			}
        	
        });

        root = new Pane();
        tempSwitcher.setLayoutX(350);
        tempSwitcher.setLayoutY(800);
        primaryStage.setWidth(1100);
        b.setLayoutY(800);
        sc.setLayoutX(300);
        sc.setLayoutY(200);
        root.getChildren().add(zoom);
        root.getChildren().add(tempSwitcher);
        root.getChildren().add(sc);
        root.getChildren().add(b);
        primaryStage.setScene(new Scene(root));
    }

    @Override public void start(Stage primaryStage) throws Exception {
        init(primaryStage);
        primaryStage.show();

        //-- Prepare Executor Services
        executor = Executors.newCachedThreadPool();
        addToQueue = new CommunicateWithServerAndAddToQueue();
        executor.execute(addToQueue);
        
        //-- Prepare Timeline
        prepareTimeline();
        
      
    }

    public static void main(String[] args) throws IOException {
        launch(args);
        
     
    }
    
    /*
     * Given current temp sends SMS if Max or Min temp are exceeded
     * and a sms has not been sent in the last 30 seconds
     */
    private void sendSMSIfNecessary(float temp)
    {
    	//Commented out so we dont spam elijah
    	if(temp > MAX_TEMP || temp<MIN_TEMP)
    	{
    		/*long currentTime = System.nanoTime();
    		if(timeOfLastTempWarning == -1 || TimeUnit.NANOSECONDS.toSeconds(currentTime - timeOfLastTempWarning) > 30 )
    		{
    			if(temp > MAX_TEMP )
    				Sms.Text(String.format("Temperature exceeded: %.2f", MAX_TEMP) ); //xseriesdata.++
    			if(temp < MIN_TEMP)
    				Sms.Text(String.format("Temperature dropped below: %.2f", MIN_TEMP) );
    		}*/ 
    		
    	}
    }

    private float getTempBasedOnMode(float temp)
    {
    	if(mode.matches(" C"))
    	{
    		return temp;
    	}
    	else
    	{
    		return temp*9.0f/5.0f+32;
    	}
    	
    }
    
    /*
     * Sets temp based on mode F or C
     */
    private void displayTemp(float tempDegC)
    {
    	series.setName("temp temp here");
    	/*if(mode == 0)
    	{
    		//currentTempDisplay.SetText();
    		series.setName( Float.toString(tempDegC));
    	}
    	else
    	{
    		float tempDegF = tempDegC * 9/5 + 32;
    		series.setName( Float.toString(tempDegF));
    	}*/
    }
    /*
     *Controls all communication with server, keeps placement and amountOfData up to date
     *receives tempData[] & timeData[] from server 
     *sends bool isVirtualButtonPressed to server
     *	placement:most recent data index
     *	amountOfData:how many temps and times are currently recorded maxs out at 300
     */
    private class CommunicateWithServerAndAddToQueue implements Runnable {
        public void run() {
        	System.out.println("Attempting to connect to server.");
        	   try( Socket serverSocket = new Socket(serverAddress, serverPort)	)
        	   {
        		   //Send server ID
        		   PrintWriter outputToServer = new PrintWriter(serverSocket.getOutputStream(), true);		   
        		   outputToServer.println("PC");
        		   	ObjectInputStream inFromServer = new ObjectInputStream(serverSocket.getInputStream());   
	        		
        		   	//initial mass temp data, mass time data, and placement/amount data received
        		   	Object mess = null;
	        		while(mess == null)
		        	{
	        				mess = inFromServer.readObject();
		        		   if(mess != null)
		        		   {
		        			   float test1[] = (float[]) mess;
		        			   System.out.printf("mass tempData[0]: %f\n", test1[0]);
		        		   }
		        	}
	        		synchronized(tempData)
	        		{
	        			tempData = (float[]) mess;
	        		}
	        		mess = null;
	        		while(mess == null)
		        	{
	        				mess = inFromServer.readObject();
		        		   if(mess != null)
		        		   {
		        			   float test1[] = (float[]) mess;
		        			   System.out.printf("mass timeData[0]: %f\n", test1[0]);
		        		   }
		        	}
	        		synchronized(timeData)
	        		{
	        			timeData = (float[]) mess;
	        		}
	        		mess = null;
	        		while(mess == null)
		        	{
	        				mess = inFromServer.readObject();
		        		   if(mess != null)
		        		   {
		        			   int testPrint[] = (int[]) mess;
		        			   System.out.printf("placement: %d, amountOfData: %d\n", testPrint[0],testPrint[1]);
		        		   }
		        	}
	        		placement = ((int[]) mess)[0];
	        		amountOfData = ((int[]) mess)[1];
	        		
	        		if(amountOfData < 300)
	        		{
	        			for(int i=0;i<placement;i++)
		        		{
	        				if(!isTempError(tempData[i]))
	        				{
	        					dataQ.add(tempData[i]);
			        			
	        				}
		        			
		        		}
	        		}
	        		else
	        		{
	        			for(int i = placement+1;i<300;i++)
	        			{
	        				if(!isTempError(tempData[i]))
	        				{
	        					dataQ.add(tempData[i]);
			        			
	        				}
	        			}
	        			for(int i=0;i<=placement;i++)
		        		{
	        				if(!isTempError(tempData[i]))
	        				{
	        					dataQ.add(tempData[i]);
			        			
	        				}
		        			//System.out.printf("%f, ",tempData[i]);
		        		}
	        		}
	        		System.out.print("\n");
	       
	        		
	        		//Constant connection reading temp/time and sending virtual button updating position and amount of data
	        		boolean connected = true;
	        		while(connected)
	        		{
	        			float temp = inFromServer.readFloat();
	        			float time = inFromServer.readFloat();
	        			//System.out.printf("%f recived at %f\n ",temp, time );
	        			//if(placement==0 || time != timeData[placement-1] )//shitty way of stopping repeated messages
	        			//{
	        			if(amountOfData == 0 )
	        			{
	        				tempData[placement] = temp;
		        			timeData[placement] = time;
		        			amountOfData++;
		        			System.out.printf("%f recived at %f\n ",temp, time );
		        			if(!isTempError(temp))
		        			{
		        				dataQ.add(temp);
		        				sendSMSIfNecessary(temp);
		        				Platform.runLater(new Runnable() {
			        	            @Override
			        	            public void run() {
			        	              series.setName(getTempBasedOnMode(temp) + mode);
			        	            }
			        			});	
		        			}
		        		 		
	        			}
	        			else
	        			{
	        				if(time != timeData[placement] )
	        				{
	        					placement += 1;
	        					if(placement >= 300) placement =0;
	        					if(amountOfData < 300) amountOfData++;
	        					
			        			tempData[placement] = temp;
			        			timeData[placement] = time;   
			        			System.out.printf("%f recived at %f\n ",temp, time );
			        			
			        			if(!isTempError(temp))
			        			{
			        				dataQ.add(temp);
			        				sendSMSIfNecessary(temp);
				        			Platform.runLater(new Runnable() {
				        	            @Override
				        	            public void run() {
				        	              series.setName(getTempBasedOnMode(temp) + mode);
				        	            }
				        			});
				        		
			        			}
			        			
	        				}	     			
	        			}
		        		outputToServer.println(String.valueOf(isVirtualButtonPressed)); 
	       
	        		}
	        		
	        
        	   } catch (UnknownHostException e1) {
					System.err.println("Host ip undetermined");
					e1.printStackTrace();
				} catch (IOException e1) {
					System.err.println("IOException");
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (ClassNotFoundException e) {
					System.err.println("Class read from io stream not found");
					e.printStackTrace();
				} 
    		   System.out.println("Past everything");

               
       		
        	// needs to recieve data constantly and add it to the chart
        	try {
				String answer = input.readLine();
				/*
				if (answer == tempDisconnected)
				{
					
				}
				*/
				dataQ.add(Integer.parseInt(answer));
				Thread.sleep(50);
				executor.execute(this);
			} catch (IOException | InterruptedException | NullPointerException e) {
				
			}
        }
    }

    //-- Timeline gets called in the JavaFX Main thread
    private void prepareTimeline() {
        // Every frame to take any data from queue and add to chart
        new AnimationTimer() {
            @Override public void handle(long now) {
                addDataToSeries();
            }
        }.start();
    }
    /*
     * checks if temp is err code if it is 
     * the graph and text is updated 
     */
    private boolean isTempError(float temp)
    {
    	if(temp == ERR_UNPLUGGED)
    	{
    		xSeriesData++;
			Platform.runLater(new Runnable() {
	            @Override
	            public void run() {
	              series.setName("Sensor Unplugged!");
	            }
			});
			while(!dataQ.isEmpty())
			{
				dataQ.remove();
			}
			inErrorState = true;
			return true;
    	}
    	else if(temp == ERR_PI_NOT_CONNECTED)
    	{
    		xSeriesData++;
			Platform.runLater(new Runnable() {
	            @Override
	            public void run() {
	              series.setName("No Data Available!");
	            }
			});
			while(!dataQ.isEmpty())
			{
				dataQ.remove();
			}
			inErrorState = true;
			return true;
    	}
    	inErrorState = false;
    	return false;
    }

    private void addDataToSeries() {
        for (int i = 0; i < 20; i++) { //-- add 20 numbers to the plot+
            if (dataQ.isEmpty()) break;
            int tempLastDataPoint = (int) dataQ.remove();
            series.getData().add(new AreaChart.Data(xSeriesData++, tempLastDataPoint));
            
            if (mode.matches(" F")) {
            	float temp = Integer.parseInt(lastDataPoint);
            	temp = (float) ((temp * 1.8) + 32);
            	Math.round(temp);
            	lastDataPoint = "" + (int) temp;
            } else {
            	lastDataPoint = "" + tempLastDataPoint;
            }
        }
        // remove points to keep us at no more than MAX_DATA_POINTS
        if (series.getData().size() > MAX_DATA_POINTS) {
            series.getData().remove(0, series.getData().size() - MAX_DATA_POINTS);
        }
        // update 
        xAxis.setLowerBound(xSeriesData-MAX_DATA_POINTS);
        xAxis.setUpperBound(xSeriesData-1);
    }
}
