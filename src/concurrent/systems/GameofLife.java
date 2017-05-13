package concurrent.systems;

import java.applet.Applet;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;

import javax.swing.JFrame;

import mpi.MPI;

@SuppressWarnings("serial")
public class GameofLife extends Applet {

	private LifeEnv env;

	// Get the applet started
	public void init() {
		build(this);
		new Worker(this).start();
	}

	public void work() {
		while (true) {
			// Just sit in a loop running forever
			env.run1Generation();
		}
	}

	// Make a user interface
	private void build(Container f) {
		setLayout(new BorderLayout());
		env = new LifeEnv();
		env.setBackground(Color.decode("#AA7739"));
		f.add("Center", env);
	}
}

@SuppressWarnings("serial")
class LifeEnv extends Canvas {
	// This holds the data structures for the game and computes the currents
	// The update environment
	private int update[][];
	// The current env
	private int current[][];
	// Need to swap the envs over
	private int swap[][];

	// private static final variables are constants
	private static final int POINT_SIZE = 7;
	private static final Color POINT_COLOR = Color.decode("#FFD9AA");
	// Width and height of environment
	private static final int N = 100;
	private static final int CANVAS_SIZE = 800;
	
	private static int gener=0;
	public LifeEnv() {
		update = new int[N][N];
		current = new int[N][N];

		// // Pattern
		for (int i = 0; i < N; i++) {
			current[0][i] = 1;
			current[N - 1][i] = 1;
			current[i][N - 1] = 1;
			current[i][0] = 1;
		}

		setSize(CANVAS_SIZE, CANVAS_SIZE);
	}

	public void run1Generation() {
		// This method is where the processes meet.
		// This method also acts as MPI.Barrier. I
		// safely send all the processes here and they would wait.

		// To compute the time taken by each Generation in the process
		long startTime = System.nanoTime();

		int rank = MPI.COMM_WORLD.Rank();
		int size = MPI.COMM_WORLD.Size();

		int sendsize = 2700; // 2700 for each item with processes greater than 4
		int[] sendArray = new int[size * sendsize];//making array according to the processes
													// process.
		int[] localin = new int[sendsize];
		int later[] = new int[size * sendsize];

		if (rank == 0) { // rank 0 splits the array into segments.
			sendArray = scatterArrayMaker(sendsize);
		}

		if (rank == 0) { // scattering the array
			MPI.COMM_WORLD.Scatter(sendArray, 0, sendsize, MPI.INT, localin, 0, sendsize, MPI.INT, 0);
		} else {
			MPI.COMM_WORLD.Scatter(sendArray, 0, sendsize, MPI.INT, localin, 0, sendsize, MPI.INT, 0);
		}

		int finalArray[] = new int[2500];
		int recvSize = 2500;
		finalArray = Algorithm(localin); // calculate the ranks 

		if (rank == 0) {// gather all of the calculated arrays
			MPI.COMM_WORLD.Gather(finalArray, 0, recvSize, MPI.INT, later, 0, recvSize, MPI.INT, 0);
		} else {
			MPI.COMM_WORLD.Gather(finalArray, 0, recvSize, MPI.INT, later, 0, recvSize, MPI.INT, 0);
		}

		if (rank == 0) { // making the array to 2d again
			int newCount = 0;
			for (int i = 0; i < 100; i++) {
				for (int j = 0; j < 100; j++) {
					try {
						update[i][j] = later[newCount];
						newCount++;
					} catch (Exception e) {
						System.out.println(e);
						System.out.println("New Count is " + newCount);
						System.out.println("Length is " + later.length);
					}
					long iters = 10000;
					do {
					} while (--iters > 0);
				}
			}
			swap = current;
			current = update;
			update = swap;
			repaint();
			// used to show how long one iteration takes
			long endTime = System.nanoTime();
			long duration = (endTime - startTime) / 1000000; // converting to
																// milli seconds
			System.out.println("Generation # "+gener+" execution time = " + duration + "ms");
			gener++;
		}
	}

	private int[] Algorithm(int[] inArray) {
		int scatterArray[] = new int[2500];
		int scatterArray2D[][] = new int[25][100];
		int sendArray[][] = new int[27][100];
		int newCount = 0;

		// Making the 2d array into 1D
		for (int i = 0; i < 27; i++) {
			for (int j = 0; j < 100; j++) {
				sendArray[i][j] = inArray[newCount];
				newCount++;
			}
		}

		for (int i = 1; i < 26; i++) {
			for (int j = 0; j < 100; j++) {
				int im = (i + N - 1) % N;
				int ip = (i + 1) % N;
				int jm = (j + N - 1) % N;
				int jp = (j + 1) % N;
				switch (sendArray[im][jm] + sendArray[im][j] + sendArray[im][jp] + sendArray[i][jm] + sendArray[i][jp]
						+ sendArray[ip][jm] + sendArray[ip][j] + sendArray[ip][jp]) {
				case 0:
				case 1:
					scatterArray2D[i - 1][j] = 0;
					break;
				case 2:
					scatterArray2D[i - 1][j] = sendArray[i][j];
					break;
				case 3:
					scatterArray2D[i - 1][j] = 1;
					break;
				case 4:
				case 5:
				case 6:
				case 7:
				case 8:
					scatterArray2D[i - 1][j] = 0;
					break;
				}

			}
			// Slow things down so that you can see them
			long iters = 10000;
			do {
			} while (--iters > 0);
		}
		newCount = 0;
		for (int i = 0; i < 25; i++) {
			for (int j = 0; j < 100; j++) {
				scatterArray[newCount] = scatterArray2D[i][j];
				newCount++;
			}
		}
		return scatterArray;
	}

	private int[] scatterArrayMaker(int sendsize) {
		int size = MPI.COMM_WORLD.Size();
		int[] sendArray = new int[(size * sendsize) + 1];// 2700 x4, 27 rows per
															// process.
		int count = 0;
		for (int i = 0; i < size; i++) {
			int sp = i * 25; // "Start Point" for each ranks group of numbers
			int im = (sp + current[0].length - 1) % current[0].length;
			int ip = (sp + 24 + 1) % current[0].length;
			for (int j = 0; j < 100; j++) {
				try {
					sendArray[count] = current[im][j];
					count++;
				} catch (Exception e) {
					System.out.println("Exception is " + e);
					System.out.println("The count is " + count);
				}
			}
			for (int j = 1; j < 26; j++) {
				for (int k = 0; k < 100; k++) {
					try {
						sendArray[count] = current[(j + sp - 1) % N][k];
						count++;
					} catch (Exception e) {
						System.out.println("Exception is " + e);
						System.out.println("count1 is " + (j + sp - 1));
						System.out.println("The size is " + sendArray.length);
					}
				}
			}
			for (int j = 0; j < 100; j++) {
				sendArray[count] = current[ip][j];
				count++;
			}
		}

		return sendArray;
	}

	// Draw the points that have value 1
	public void paint(Graphics g) {
		for (int i = 0; i < N; i++) {
			for (int j = 0; j < N; j++) {
				if (current[i][j] == 1) {
					drawPoint(i, j, 1, g);
				}
			}
		}

		g.setColor(Color.black);
		g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
	}

	private void drawPoint(int x, int y, int v, Graphics g) {
		Dimension d = (getSize());
		int mx = d.width * x / N;
		int my = d.height * y / N;
		if (v == 1) {
			g.setColor(POINT_COLOR);
		} else {
			g.setColor(getBackground());
		}
		g.fillOval(mx, my, POINT_SIZE, POINT_SIZE);
	}
}

class Worker extends Thread {

	private GameofLife game;

	public Worker(GameofLife g) {
		game = g;
	}

	public void run() {
		game.work();
	}
}

class ThreadStart {
	public static void main(String[] args) {
		MPI.Init(args);
		int myRank = MPI.COMM_WORLD.Rank();
		GameofLife g = new GameofLife();
		// When the process will start with rank 0 it will initiaize the window
		// and start the genrartions
		if (myRank == 0) {
			JFrame frame = new JFrame();
			frame.getContentPane().add(g);
			Container c = frame.getContentPane();
			Dimension d = new Dimension(800, 720);
			c.setPreferredSize(d);
			frame.pack();
			frame.setVisible(true);
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			g.init();
			MPI.Finalize();
		} else { // All the other processes will meet the first process at the
					// LifeEnv.runOneIteration method
			g.init();
		}

	}
}