import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;
import java.net.*;

class MockRouter {
	private int portNumber;
	private String[] adjacents;
	ServerThread serverThread;;
	ServerThread1 serverThread1;
	boolean stop = false;
	List<String[]> linkStateMessageHistory = new CopyOnWriteArrayList<String[]>();

	
	//portAndReceivePort keep track of which port the message came from 
	Map<Integer, Integer> portAndReceivePort = Collections.synchronizedMap(new HashMap<Integer, Integer>());	//thread safe
	Long time;
	
	//portAndDistance map the port to its distance 
	Map<Integer, Integer> portAndDistance = new HashMap<Integer, Integer>();
	
	List<Integer[]> routingTable = new CopyOnWriteArrayList<Integer[]>();	
	
	MockRouter(int portNumber, String[] adjacents) {
		this.portNumber = portNumber;
		this.adjacents = adjacents;
		
		//instantiating two threads
		serverThread = new ServerThread();
		serverThread1 = new ServerThread1();
		
		//setting the time of when the program started
		time = System.currentTimeMillis();
		
		//split the port and distance and put it in a map
		for(int i = 0; i < adjacents.length; i++) {
			String[] message = adjacents[i].split("-");
			portAndDistance.put(Integer.parseInt(message[0]), Integer.parseInt(message[1]));
		}
	}
	
	class ServerThread extends Thread { //--------------------------------------------------------------------------------------Start of ServerThread class (INNER CLASS)
		@Override
		public void run() {
			try {
				ServerSocket serverSocket = new ServerSocket(portNumber);
				
				while(!stop) {
					Socket clientSocket = serverSocket.accept();
					
					//another thread to handle client connection (allow for multiple connection at once)
			        Thread thread = new Thread(() -> {
			            try {
			            	
			                // handle connection here
							InputStreamReader in = new InputStreamReader(clientSocket.getInputStream());
							BufferedReader br = new BufferedReader(in);
							String line = br.readLine();
							
							//receive a link state messages to which it responds with ACK and a newline, 
							if(line.charAt(0) == 'l') {
								
								//loop and read all the message in the buffer
								while(line != null) {
									
									//break out of loop if message end with EOF
									if(line.endsWith("EOF")) {
										break;
									} 
									
									linkStateMessageHandler(line, br.readLine());
									line = br.readLine();
								}
								
								//sending message to the connected socket
								PrintWriter pw = new PrintWriter(clientSocket.getOutputStream());
								pw.println("ACK");
								pw.println("EOF");	//marking the end of the file
								pw.flush();
								
								pw.close();
								br.close();
								in.close();							
							}
							
							//receive a h followed by a newline (a history message) 
							//to which it responds with all the link state messages its received 
							//(and at what time since the start of the simulation), 
							//followed by a line with the word TABLE followed by lines giving its routing table
							else if(line.charAt(0) == 'h') {
								PrintWriter pw = new PrintWriter(clientSocket.getOutputStream());
								
								//concatenating the link state message and send it to the port
								for(int i = 0; i < linkStateMessageHistory.size(); i++) {
									String m = "";
									String[] message = linkStateMessageHistory.get(i);
									for(int j = 0; j < message.length; j++) {
										m += message[j] + " ";
									}
									pw.println(m);
								}
								pw.println('\n' + "Ellasped time since the start of simulation in seconds: " + (System.currentTimeMillis() - time)/1000 + " seconds"+ '\n');
								pw.println("Table");
								
								
								//TODO: PRINT ROUTING TABLE HERE
								for(Integer[] table : routingTable) {
									pw.println("Destination: " + table[0] + ", Cost: " + table[1] + ", NextHop: " + table[2]);
								}
								
								
								
								
								pw.println("EOF");	//marking the end of the file
								
								pw.close();
								br.close();
								in.close();
							}
							
							//receive a s followed by a newline (stop) to which it responds with STOPPING and a newline and then it stops its thread.
							else if(line.charAt(0) == 's') {
								
								//sending message to the connected socket
								PrintWriter pw = new PrintWriter(clientSocket.getOutputStream());
								pw.println("STOPPING");
								pw.println("EOF");	//marking the end of the file
								pw.flush();
								stop = true;
								
								pw.close();
								br.close();
								in.close();
							}
							
							//delete port from portAndDistance
							else if(line.charAt(0) == 'd') {
								int index;
								//loop and read all the message in the buffer
								while(line != null) {
									
									//break out of loop if message end with EOF
									if(line.endsWith("EOF")) {
										break;
									} 
									
									line = br.readLine();
									portAndDistance.remove(Integer.parseInt(line));
									
									//loop through adjacent to find the index of the stopped port
									for(int i = 0; i < adjacents.length; i++) {
										String[] message = adjacents[i].split("-");
										if(Integer.parseInt(message[0]) == Integer.parseInt(line)) {
											index = i;
											
											//remove the port from array
											if (index >= 0 && index < adjacents.length) { // Check if the index is within the bounds of the array
											    String[] newArr = new String[adjacents.length - 1];
											    System.arraycopy(adjacents, 0, newArr, 0, index);
											    System.arraycopy(adjacents, index + 1, newArr, index, adjacents.length - index - 1);
											    adjacents = newArr;
											}
											break;
										}
									}
									
									line = br.readLine();
								}
								
								br.close();
								in.close();
							}
	
			            } catch (IOException e) {
							e.printStackTrace();
						} finally {
			                try {
			                	clientSocket.close();  // close connection when done
			                } catch (IOException e) {
			                    e.printStackTrace();
			                }
			            }
			        });
			        thread.start();  // start new thread to handle connection
				}
				serverSocket.close();	//close connection when done
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		//method to handle link state message
		public void linkStateMessageHandler(String line, String portNum) {
			
			//split the message and remove the l in front of the link state message
			String[] message = line.split(" ");
			String[] linkStateMessage = Arrays.copyOfRange(message, 1, message.length);
	
			//make sure only one thread can modify the list at a time
			synchronized(linkStateMessageHistory) {
				boolean isInList = false;
				
				for(int i = 0; i < linkStateMessageHistory.size(); i++) {
					String[] m = linkStateMessageHistory.get(i);
					
					//checking if there is already a link state message for a port
					if(Integer.parseInt(linkStateMessage[0]) == Integer.parseInt(m[0])) {
						isInList = true;
						
						//check if its a new message by comparing its sequence number
						//and if it's new, the old message gets replace by the new message
						if(Integer.parseInt(linkStateMessage[1]) > Integer.parseInt(m[1])) {
							linkStateMessageHistory.set(i, linkStateMessage);
							portAndReceivePort.replace(Integer.parseInt(linkStateMessage[0]), Integer.parseInt(portNum));
						}
						break; 
					}
				}
				
				//add to list only if message its not in the list
				if(!isInList) {
					linkStateMessageHistory.add(linkStateMessage);
					portAndReceivePort.put(Integer.parseInt(linkStateMessage[0]), Integer.parseInt(portNum));
				}
			}
		}
	}
	
	class ServerThread1 extends Thread { //--------------------------------------------------------------------------------------Start of ServerThread1 class (INNER CLASS)
		
		@Override
		public void run() {
			
			//thread is use for making connection with its adjacent neighbor and send link state message every 3 - 4 seconds
			Thread thread = new Thread(() -> {
				int sequenceNumber = 0;
				
			    // code to be executed by the thread
				while(!stop) {
					try {
						
						//setting up the port own link state message in the format:
						//l sender_port seq_number time_to_live adjacent_router_1_port-distance_1 ... adjacent_router_n_port-distance_n
						String message = "l " + portNumber + " " + sequenceNumber + " 60 ";
						sequenceNumber++;
						
						//concatenating adjacent neighbors to its link state message
						for(int j = 0; j < adjacents.length; j++) {
							message += adjacents[j] + " ";
						}
						
						//loop through port and make connections to send link state message in the format:
						//l sender_port seq_number time_to_live adjacent_router_1_port-distance_1 ... adjacent_router_n_port-distance_n
						for(Entry<Integer, Integer> port : portAndDistance.entrySet()) {
							try {
								Socket socket = new Socket("localhost", port.getKey());				
								PrintWriter pw = new PrintWriter(socket.getOutputStream());
				
								//sending its own link state message
								pw.println(message);
								pw.println(portNumber);
								pw.flush();
								
								//sending link state message it has received
								for(int i = 0; i < linkStateMessageHistory.size(); i++) {
									String[] temp = linkStateMessageHistory.get(i);
									String stateLinkMessage = "l ";
									
									if(portAndReceivePort.get(Integer.parseInt(temp[0])) != null) {
									
										//don't allow message to be sent back to the port that send this message
										if(!portAndReceivePort.get(Integer.parseInt(temp[0])).equals(port.getKey())) {
											
											//concatenate that link state message into the correct format
											for(int k = 0; k < temp.length; k++) {
												stateLinkMessage += temp[k] + " ";
											}
											
											//sending other link state message 
											pw.println(stateLinkMessage);
											pw.println(portNumber);
											pw.flush();
										}
									} else {
										
										//concatenate that link state message into the correct format
										for(int k = 0; k < temp.length; k++) {
											stateLinkMessage += temp[k] + " ";
										}
										
										//sending other link state message 
										pw.println(stateLinkMessage);
										pw.println(portNumber);
										pw.flush();
									}
								}
								pw.println("EOF");	//marking the end of the file
								pw.flush();
								pw.close();
							} catch (UnknownHostException e) {
							} catch (IOException e) {
							}
						}
						
						//will generate a random long between 0 and 1000
				        Random random = new Random();
				        long randomLong = random.nextLong() % 1000;
				        if (randomLong < 0) {
				            randomLong += 1000;
				        }
				        long totalTime = 3000 + randomLong;
				        Thread.sleep(totalTime);	//make the thread sleep for 3 - 4 seconds
					} catch (InterruptedException e) {
					}
				}
			});
			thread.start(); // start the thread
			
			//while loop will loop every second and does the following things:
			//decrement the time to live by 1
			//computer a new routing table using Dijkstra's algorithm
			while(!stop) {
				try {
					
					
					//decrement the time to live by 1 seconds
					for(int i = 0; i < linkStateMessageHistory.size(); i++) {
						String[] temp = linkStateMessageHistory.get(i);
						int TTL = Integer.parseInt(temp[2]) - 1;
						linkStateMessageHistory.get(i)[2] = Integer.toString(TTL);
						
						//remove link state message if it's time to live reaches 0
						if(TTL < 0) {
							linkStateMessageHistory.remove(i);
						}
					}
					
					//clear the routing table and recalculate the shortest path
					routingTable.clear();
					computeRoutingTable(portNumber, portAndDistance, linkStateMessageHistory);
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
		//compute a routing table and store it in a list called routingTable 
		public void computeRoutingTable(int portNumber, Map<Integer, Integer> portAndDistance, List<String[]> linkStateMessage) {
			List<Integer[]> tentativeList = new ArrayList<Integer[]>();	
			int forbiddenPort = 0;
			
			//put itself on the routing table(confirm list) with a distance of zero
			//should be in the format (Destination, Cost, NextHop)
			Integer[] temp = {portNumber, 0, portNumber};
			routingTable.add(temp);
			
			//add it's adjacent neighbors to the tentative list
			for(Entry<Integer, Integer> entry : portAndDistance.entrySet()) {
				Integer[] temp1 = {entry.getKey(), entry.getValue(), entry.getKey()};
				tentativeList.add(temp1);
			}
			
			//keep looping if tentativeList is not empty
			while(tentativeList.size() != 0) {
				Integer[] newlyAddedMember = {null, null, null};	
				
				//find the lowest cost member of tentative and add it to confirmed list
				if(tentativeList.size() > 0) {
					int shortestDistance = tentativeList.get(0)[1];
					int index = 0;
					
					for(int i = 0; i < tentativeList.size(); i++) {
						if(tentativeList.get(i)[1] < shortestDistance) {
							index = i;
							shortestDistance = tentativeList.get(i)[1];
						}
					}
					routingTable.add(tentativeList.get(index));
					newlyAddedMember = tentativeList.get(index);
					tentativeList.remove(index);
				}
				
				//look at the link state message of the newly added member of confirmed list and compute distance
				for(int j = 0; j < linkStateMessage.size(); j++) {
					String[] message = linkStateMessage.get(j);
					if(Integer.parseInt(message[0]) == newlyAddedMember[0]) {
						
						//loop through all of the newly added member's adjacent and compute distance from starting port to this port and this port to its adjacent port
						for(int k = 3; k < message.length; k++) {
							String[] portDist = message[k].split("-");
							
							for(int z = 0; z < routingTable.size(); z++) {
								if(routingTable.get(z)[0] == Integer.parseInt(portDist[0])) {
									forbiddenPort = Integer.parseInt(portDist[0]);
								}
							}
							
							if(Integer.parseInt(portDist[0]) != forbiddenPort) {
								boolean isInTentList = false;
								boolean isInConfirmList = false;
								
								int distance = newlyAddedMember[1] + Integer.parseInt(portDist[1]);
								Integer[] temp2 = {Integer.parseInt(portDist[0]), distance, newlyAddedMember[2]};
														
								//loop through tentative list and compare distance
								for(int l = 0; l < tentativeList.size(); l++) {
									
									//if it already in tentativeList and has a shorter distance, replace it
									if(tentativeList.get(l)[0] == temp2[0]) {
										isInTentList = true;
										if(tentativeList.get(l)[1] > temp2[1]) {
											tentativeList.set(l, temp2);
										}
										break;
									}
								}
								
								//loop through confirm list
								for(int m = 0; m < routingTable.size(); m++) {
									
									//if its in confirm list 
									if(routingTable.get(m)[0] == temp2[0]) {
										isInConfirmList = true;
										break;
									}
								}							
								
								//add it to tentativeList if its not in the confirm and tentative list
								if(!isInTentList && !isInConfirmList) {
									tentativeList.add(temp2);
								}	
							}
						}
					}
				}			
			}
		}
	}
}

public class LinkStateSimulator {
	public static void main(String[] args) {
		ArrayList<Integer> ports = new ArrayList<Integer>();
		
		try {
			BufferedReader br = new BufferedReader(new FileReader(args[0]));
			String line;
			
			//keep reading one line at a time until it reaches the end of the file
			while((line = br.readLine()) != null) {
				String[] arrString = line.split(" ");
				int portNumber = Integer.parseInt(arrString[0]);
				ports.add(portNumber);
				String[] adjacents = Arrays.copyOfRange(arrString, 1, arrString.length);
				
				MockRouter mockRouter = new MockRouter(portNumber, adjacents);
				
				//starting the threads
				mockRouter.serverThread.start();
				mockRouter.serverThread1.start();
			}
			br.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("Initialization complete. Ready to accept commands.");
		
		Scanner scanner = new Scanner(System.in);
		
		while(scanner.hasNext()) {
			String command = scanner.next();
			
			try {
				
				//h some_port_number #returns the result of sending an h message to the router some_port_number
				if(command.equals("h") && scanner.hasNext()) {
					Socket clientSocket = new Socket("localhost", scanner.nextInt());
					
					//sending a message to mentioned port
					PrintWriter pw = new PrintWriter(clientSocket.getOutputStream());
					pw.println("h");
					pw.println("(a history message)");
					pw.println("EOF");	//marking the end of the file
					pw.flush();
					
					//message receive from mentioned port
					InputStreamReader in = new InputStreamReader(clientSocket.getInputStream());
					BufferedReader br = new BufferedReader(in);
					String line;
					
					//loop and read all the message in the buffer
					while((line = br.readLine()) != null) {
						
						//break out of loop if message end with EOF
						if(line.endsWith("EOF")) {
							break;
						} 
						System.out.println(line);
					}
					System.out.println('\n');
					br.close();
					in.close();
					pw.close();
					clientSocket.close();
				}	
				
				//s some_port_number #shuts down MockRouter some_port_number
				if(command.equals("s") && scanner.hasNext()) {
					Integer port = scanner.nextInt();
					Socket clientSocket = new Socket("localhost", port);
					
					//sending a message to mentioned port
					PrintWriter pw = new PrintWriter(clientSocket.getOutputStream());
					pw.println("s");
					pw.println("(stop)");
					pw.println("EOF");	//marking the end of the file
					pw.flush();
					
					//message receive from mentioned port
					InputStreamReader in = new InputStreamReader(clientSocket.getInputStream());
					BufferedReader br = new BufferedReader(in);
					String line;
					
					//loop and read all the message in the buffer
					while((line = br.readLine()) != null) {
						
						//break out of loop if message end with EOF
						if(line.endsWith("EOF")) {
							break;
						} 
						System.out.println(line);
					}	
					System.out.println('\n');
					br.close();
					in.close();
					pw.close();
					
					clientSocket.close();
					
					//send a message to all other port that a port is deleted
					for(Integer p : ports) {
						Socket socket = new Socket("localhost", p);
						
						//sending a message to mentioned port
						PrintWriter pw1 = new PrintWriter(socket.getOutputStream());
						pw1.println("d");
						pw1.println(port);
						pw1.println("EOF");	//marking the end of the file
						pw1.flush();
						
						pw1.close();
						socket.close();
					}
					ports.remove(port);
				} 
				
				//e #exits the program
				if(command.equals("e")) {
					
					//sending a s to each port telling it to stop
					for(Integer port : ports) {
						Socket clientSocket = new Socket("localhost", port);
						
						//sending a message to mentioned port
						PrintWriter pw = new PrintWriter(clientSocket.getOutputStream());
						pw.println("s");
						pw.println("(stop)");
						pw.println("EOF");	//marking the end of the file
						pw.flush();
						
						pw.close();
						clientSocket.close();
					}
					
					//connecting to port again so thread waiting for connection aren't stuck
					for(Integer port : ports) {
						Socket clientSocket = new Socket("localhost", port);
						
						//sending a message to mentioned port
						PrintWriter pw = new PrintWriter(clientSocket.getOutputStream());
						pw.println("EOF");	//marking the end of the file
						pw.flush();
						
						pw.close();
						clientSocket.close();
					}
					
					System.out.println("exiting might take a couple of seconds");
					break;		//break out the while loop
				}
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
