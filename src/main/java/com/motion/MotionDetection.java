package com.motion;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.highgui.VideoCapture;
import org.opencv.imgproc.Imgproc;

public class MotionDetection extends Thread {
	static {


	}

	JFrame jframe = new JFrame("Detection Monitor");
	public static List<BufferedImage> detectionFrames = new ArrayList();
	static Mat finalImage = null;
	public static final int BUFFER_FRAMES = 100;
	boolean motionDetected = false;
	int detectionFrameCount = 0;

	public MotionDetection() {
		// this.run();
	}

	@Override
	public void run() {
		System.out.println("Started Motion Detector");
		JButton closeButton = new JButton("Close Server");

		closeButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				System.exit(0);
				System.out.println("Stopped Server");
			}

		});
		closeButton.setLayout(null);
		jframe.setDefaultCloseOperation(2);
		JLabel vidpanel = new JLabel();
		JLabel clientsConnected = new JLabel("Clients Connected: ");

		jframe.add(closeButton, "South");
		jframe.add(clientsConnected, "North");
		jframe.add(vidpanel);

		jframe.pack();

		jframe.setSize(640, 480);
		jframe.setVisible(true);
		jframe.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				System.exit(0);
			}
		});
		Mat frame = new Mat();
		Mat outerBox = new Mat();
		Mat diffFrame = null;
		Mat tempFrame = null;
		ArrayList<Rect> array = new ArrayList<Rect>();
		VideoCapture camera = new VideoCapture(0);
		Size sz = new Size(640, 480);
		boolean firstFrame = true;
		if (!camera.isOpened()) {
			System.out.println("Camera Not Initialized");
			clientsConnected.setText("No Camera Found");
		}
		for (;;) {
			if (camera.read(frame)) {
				Imgproc.resize(frame, frame, sz);
				finalImage = frame.clone();
				outerBox = new Mat(frame.size(), CvType.CV_8UC1);
				Imgproc.cvtColor(frame, outerBox, 7);


				if (firstFrame) {
					jframe.setSize(frame.width(), frame.height());
					diffFrame = new Mat(outerBox.size(), CvType.CV_8UC1);
					tempFrame = new Mat(outerBox.size(), CvType.CV_8UC1);
					diffFrame = outerBox.clone();
				} else {
					Core.subtract(outerBox, tempFrame, diffFrame);
					Imgproc.adaptiveThreshold(diffFrame, diffFrame, 30, Imgproc.ADAPTIVE_THRESH_MEAN_C,
									Imgproc.THRESH_BINARY_INV, 11, 10);
					array = detect(diffFrame);
					if ((array.size() > 0) && (!motionDetected)) {
						System.out.println("Motion detected! Will send next 10 frames!");
						motionDetected = true;
						Iterator<Rect> it2 = array.iterator();
						while (it2.hasNext()) {
							Rect obj = it2.next();
							Core.rectangle(finalImage, obj.br(), obj.tl(), new Scalar(0.0, 255.0, 0.0), 1);
						}

						detectionFrames.add(Mat2bufferedImage(frame));

					}
					if ((motionDetected) && (detectionFrameCount < 10)) {
						if (detectionFrameCount == 9) {
							motionDetected = false;
							detectionFrameCount = 0;
							detectionFrames.clear();
							continue;
						}
						Iterator<Rect> it2 = array.iterator();
						while (it2.hasNext()) {
							Rect obj = it2.next();
							Core.rectangle(finalImage, obj.br(), obj.tl(), new Scalar(0.0D, 255.0D, 0.0D), 1);
						}
						if(detectionFrames.size()>0) {
							BufferedImage save = detectionFrames.remove(0);
							// Saving the image with a different name
							File ouptut = new File("D:/Images/Capture/sample" + System.currentTimeMillis() + ".jpg");
							try {
								ImageIO.write(save, "jpg", ouptut);
							} catch (IOException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}

							System.out.println("image Saved");

//							detectionFrames.add(Mat2bufferedImage(frame));
//							if (detectionFrames.size() >= 100) {
//								while (detectionFrames.size() >= 100) {
	//
//								}
//							}
						}
						

						detectionFrameCount += 1;
					}
				}

				firstFrame = false;

				ImageIcon image = new ImageIcon(Mat2bufferedImage(finalImage));
				//clientsConnected.setText("Clients Connected: ");

				vidpanel.setIcon(image);
				vidpanel.repaint();
				tempFrame = outerBox.clone();
				outerBox = null;
				image = null;
				try {
					Thread.sleep(15L);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	public BufferedImage Mat2bufferedImage(Mat image) {
		MatOfByte bytemat = new MatOfByte();
		Highgui.imencode(".jpg", image, bytemat);
		byte[] bytes = bytemat.toArray();
		InputStream in = new ByteArrayInputStream(bytes);
		BufferedImage img = null;
		try {
			img = ImageIO.read(in);
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		bytemat.release();
		bytes=null;
		return img;
	}

	public ArrayList<Rect> detect(Mat outmat) {
		Mat v = new Mat();
		Mat vv = outmat.clone();
		List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
		Imgproc.findContours(vv, contours, v, 1, 2);

		double maxArea = 100.0D;
		int maxAreaIdx = -1;
		Rect r = null;
		ArrayList<Rect> changeResults = new ArrayList<Rect>();

		for (int idx = 0; idx < contours.size(); idx++) {
			Mat contour = contours.get(idx);
			double contourarea = Imgproc.contourArea(contour);
			if (contourarea > maxArea) {
				maxAreaIdx = idx;
				r = Imgproc.boundingRect(contours.get(maxAreaIdx));
				changeResults.add(r);
				Imgproc.drawContours(finalImage, contours, maxAreaIdx, new Scalar(0.0D, 0.0D, 255.0D));
			}
			contour.release();
		}

		v.release();
		vv.release();
		r=null;
		return changeResults;
	}

	public static void main(String[] args) {
		System.load("D:\\Development\\opencv\\build\\java\\x64\\opencv_java249.dll");
		 System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

		MotionDetection m = new MotionDetection();
		m.start();
	}
}