import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

public class Main {
	private static String inputPath;
	private static String outputPath;
	private static int width;
	private static int height;
	private static int[] pixelsIn;
	private static int[] pixelsOut;
	private static String fileOut = "";
	
	private static File inputFile;
	private static int threads = 4;
	
	public static void main(String[] args) {
		
		boolean threadsSpecified = false;
		for (int i = 0; i < args.length; i++) {
			if (outputPath == null) {
				if (new File(args[i]).exists() && inputPath == null) {
					inputPath = args[i];
				} else if (args[i].equals("-o")) {
					if (inputPath == null) {
						System.err.println("You must specify the input before the output.");
						System.exit(0);
					} else {
						if (outputPath == null) {
							if (i + 1 < args.length) {
								outputPath = args[i + 1];
							} else {
								System.err.println("You have to specify an output if you use \"-o\"");
								System.exit(0);
							}
						} else {
							System.err.println("There can only be one output.");
							System.exit(0);
						}
					}
				} else if (!(new File(args[i]).exists()) && !threadsSpecified && !args[i].equals("-t")) {
					System.err.println("Error at \"" + args[i] + "\"");
					System.exit(0);
				}
			}
			if (args[i].equals("-t")) {
				threadsSpecified = true;
				if (i + 1 < args.length) {
					try {
						threads = Integer.parseInt(args[i+1]);
						if (threads <= 0) {
							System.err.println("\""+args[i+1] + "\" is not a valid thread count! Setting threads to 4.");
							threads = 4;
						}
					} catch (NumberFormatException e) {
						System.err.println("\""+args[i+1] + "\" is not a valid integer! Setting threads to 4.");
					}
				}
			}
		}
		
		if (inputPath == null) {
			System.err.println("You need to specify an input path!");
			System.exit(0);
		}
		
		if (outputPath == null) {
			outputPath = inputPath.substring(0, inputPath.lastIndexOf(".")) + ".joj";
		}
		
		inputFile = new File(inputPath);
		
		BufferedImage imageIn = null;
		
		System.out.println("Loading input image \"" + inputPath + "\"...");
		try {
			imageIn = ImageIO.read(inputFile);
			width = imageIn.getWidth();
			height = imageIn.getHeight();
			pixelsIn = new int[width * height];
			pixelsOut = new int[width * height];
			imageIn.getRGB(0, 0, width, height, pixelsIn, 0, width);			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		System.out.println("Processing input image with " + threads + " thread(s)...");
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		ImageWorker[] workers = new ImageWorker[threads];
		CountDownLatch latch = new CountDownLatch(threads);
		
		for (int i = 0; i < threads; i++) {
			int start = (i*pixelsIn.length) / threads;
			int end = ((i+1)*pixelsIn.length)/threads;
			workers[i] = new ImageWorker(start,end, latch);
		}
		
		for (int i = 0; i < workers.length; i++) {
			executor.submit(workers[i]);
		}
		long beginTime = System.currentTimeMillis();
		
		try {
			latch.await();
			long endTime = System.currentTimeMillis() - beginTime;
			System.out.println("Completed in " + endTime + " ms.");
			
			for (int i = 0; i < workers.length; i++) {
				fileOut += workers[i].fileOut;
			}
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		
		fileOut = fileOut.substring(0, fileOut.length()-1);
		try {
			System.out.println("Saving BRAM initializer at \"" + outputPath + "\"...");
			PrintWriter out = new PrintWriter(new FileOutputStream(outputPath));
			out.print(fileOut);
			out.close();
			
			System.out.println("Saving 12-bit color image at \"out.png\"...");
			BufferedImage imageOut = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					imageOut.setRGB(x, y, pixelsOut[x + y * width]);
				}
			}
			
			ImageIO.write(imageOut, "png", new File("out.png"));
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		executor.shutdownNow();
		try {
			System.out.println("Waiting for worker thread termination...");
			executor.awaitTermination(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Done.");
		System.exit(0);
	}
	
	private static class ImageWorker implements Runnable {
		private final int start;
		private final int end;
		private final CountDownLatch latch;
		public String fileOut = "";
		
		public ImageWorker(final int _start, final int _end, final CountDownLatch _latch) {
			start = _start;
			end = _end;
			latch = _latch;
		}
		
		@Override
		public void run() {
			for (int i = start; i < end; i++) {
				int color = pixelsIn[i];
				int blue = color & 0xFF;
				int green = (color >> 8) & 0xFF;
				int red = (color >> 16) & 0xFF;
				
				blue >>= 4;
				green >>= 4;
				red >>= 4;
				
				pixelsOut[i] = (blue << 4) | (green << 12) | (red << 20);
				pixelsOut[i] = (red << 20) | (green << 12) | (blue << 4);
				
				this.fileOut += Integer.toHexString(green) + Integer.toHexString(blue) + Integer.toHexString(red) + "\n";
			}
			latch.countDown();
		}
	}
	
}
