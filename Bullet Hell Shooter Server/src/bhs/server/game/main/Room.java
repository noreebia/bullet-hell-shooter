package bhs.server.game.main;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import bhs.server.game.control.DataController;
import bhs.server.game.control.EnemySystem;
import game.protocol.Client;

public class Room {
	private DatagramSocket socket;		
	private DataController dataController = new DataController();
	private EnemySystem enemySystem = new EnemySystem(dataController);
	private List<Client> clients = new CopyOnWriteArrayList<Client>();
	private short connectionCount=0;
	private int id;
	private int port;
	private InputHandlingThread inputHandlingThread;
	private OutputHandlingThread outputHandlingThread;
	private ExecutorService executor = Executors.newCachedThreadPool();
	private ScheduledExecutorService ses = Executors.newScheduledThreadPool(3);
	private AtomicInteger uniquePlayerID = new AtomicInteger();
	private String state = "Joinable";
	
	public Room(int id) {
		System.out.println("Room number " + id + "created");
		this.id = id;
		enemySystem.resetEnemies(10);

		try {
			socket = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
		}
		
		inputHandlingThread = new InputHandlingThread(socket, dataController, enemySystem, clients);
		outputHandlingThread = new OutputHandlingThread(this, socket, dataController, clients, enemySystem);
		
		executor.execute(inputHandlingThread);
		ses.scheduleAtFixedRate(outputHandlingThread, 0, 8, TimeUnit.MILLISECONDS);
	}

	public short getConnectionCount() {
		return connectionCount;
	}
	
	public int getPort() {
		return socket.getLocalPort();
	}
	
	public int getID() {
		return id;
	}
	
	public int getUniquePlayerID() {
		return uniquePlayerID.getAndIncrement();
	}
	
	public int getPlayerCount() {
		return clients.size();
	}
	
	public void setState(String state) {
		this.state = state;
	}
	
	public String getState() {
		return state;
	}
	
	public short getCurrentStage() {
		return dataController.getLevel();
	}
	
	public void shutdown() {
		inputHandlingThread.terminate();
		executor.shutdown();
		ses.shutdown();
		try {
			if(!executor.awaitTermination(800, TimeUnit.MILLISECONDS) || !ses.awaitTermination(800, TimeUnit.MILLISECONDS)) {
				executor.shutdownNow();
				ses.shutdownNow();
			}
		} catch (InterruptedException e) {
			executor.shutdownNow();
			ses.shutdownNow();
		}
		socket.close();
		System.out.println("Room number " + id + " shutting down");
	}
}
