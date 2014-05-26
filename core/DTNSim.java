/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package core;
import gui.DTNSimGUI;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import ui.DTNSimTextUI;

/**
 * Simulator's main class 
 */
public class DTNSim {
	/** If this option ({@value}) is given to program, batch mode and
	 * Text UI are used*/
	public static final String BATCH_MODE_FLAG = "-b";
	/** Delimiter for batch mode index single element values (comma) */
	public static final String ELEMENT_DELIMETER = ", ";
	/** Delimiter for batch mode index range values (colon) */
	public static final String RANGE_DELIMETER = ":";
	
	/** Name of the static method that all resettable classes must have
	 * @see #registerForReset(String) */
	public static final String RESET_METHOD_NAME = "reset";
	/** List of class names that should be reset between batch runs */
	private static List<Class<?>> resetList = new ArrayList<Class<?>>();
	
	/**
	 * Starts the user interface with given arguments.
	 * If first argument is {@link #BATCH_MODE_FLAG}, the batch mode and text UI
	 * is started. The batch mode option must be followed by the number of runs,
	 * or a with a combination of starting run and the number of runs, 
	 * delimited with a {@value #RANGE_DELIMETER}. Different settings from run
	 * arrays are used for different runs (see 
	 * {@link Settings#setRunIndex(int)}). Following arguments are the settings 
	 * files for the simulation run (if any). For GUI mode, the number before 
	 * settings files (if given) is the run index to use for that run.
	 * @param args Command line arguments
	 */
	public static void main(String[] args) {
		boolean batchMode = false;
		List<Integer> nrofRuns = null;
		String confFiles[];
		int firstConfIndex = 0;
		int guiIndex = 0;

		/* set US locale to parse decimals in consistent way */
		java.util.Locale.setDefault(java.util.Locale.US);
		
		if (args.length > 0) {
			if (args[0].equals(BATCH_MODE_FLAG)) {
				batchMode = true;
                if (args.length == 1) {
                    firstConfIndex = 1;
                }
                else {
                    nrofRuns = parseNrofRuns(args[1]);
                    firstConfIndex = 2;
                }
			}
			else { /* GUI mode */				
				try { /* is there a run index for the GUI mode ? */
					guiIndex = Integer.parseInt(args[0]);
					firstConfIndex = 1;
				} catch (NumberFormatException e) {
					firstConfIndex = 0;
				}
			}
			confFiles = args;
		}
		else {
			confFiles = new String[] {null};
		}
		
		initSettings(confFiles, firstConfIndex);
		
		if (batchMode) {
			print("Will run following indexes: " + nrofRuns);
			int runNum = 1;
			long startTime = System.currentTimeMillis();
			for (int i : nrofRuns) {
				print("Run " + runNum + "/" + nrofRuns.size() + ", index=" + i);
				Settings.setRunIndex(i);
				resetForNextRun();
				new DTNSimTextUI().start();
				runNum++;
			}
			double duration = (System.currentTimeMillis() - startTime) / 1000.0;
			print("---\nAll done in " + String.format("%.2f", duration) + "s");
		}
		else {
			Settings.setRunIndex(guiIndex);
			new DTNSimGUI().start();
		}
	}
	
	/**
	 * Initializes Settings
	 * @param confFiles File name paths where to read additional settings 
	 * @param firstIndex Index of the first config file name
	 */
	private static void initSettings(String[] confFiles, int firstIndex) {
		int i = firstIndex;

        if (i >= confFiles.length) {
            return;
        }

		try {
			Settings.init(confFiles[i]);
			for (i=firstIndex+1; i<confFiles.length; i++) {
				Settings.addSettings(confFiles[i]);
			}
		}
		catch (SettingsError er) {
			try {
				Integer.parseInt(confFiles[i]);
			}
			catch (NumberFormatException nfe) {
				/* was not a numeric value */
				System.err.println("Failed to load settings: " + er);
				System.err.println("Caught at " + er.getStackTrace()[0]);			
				System.exit(-1);
			}
			System.err.println("Warning: using deprecated way of " + 
					"expressing run indexes. Run index should be the " + 
					"first option, or right after -b option (optionally " +
					"as a range of start and end values).");
			System.exit(-1);
		}
	}
	
	/**
	 * Registers a class for resetting. Reset is performed after every
	 * batch run of the simulator to reset the class' state to initial
	 * state. All classes that have static fields that should be resetted
	 * to initial values between the batch runs should register using 
	 * this method. The given class must have a static implementation
	 * for the resetting method (a method called {@value #RESET_METHOD_NAME} 
	 * without any parameters).
	 * @param className Full name (i.e., containing the packet path) 
	 * of the class to register. For example: <code>core.SimClock</code> 
	 */
	public static void registerForReset(String className) {
		Class<?> c = null;
		try {
			c = Class.forName(className);
			c.getMethod(RESET_METHOD_NAME);
		} catch (ClassNotFoundException e) {
			System.err.println("Can't register class " + className + " for resetting; " +
								"class not found");
			System.exit(-1);
		}
		catch (NoSuchMethodException e) {
			System.err.println("Can't register class " + className + " for resetting; " +
								"class doesn't contain resetting method");
			System.exit(-2);
		}
		
		resetList.add(c);
	}
	
	/**
	 * Resets all registered classes.
	 */
	private static void resetForNextRun() {
		for (Class<?> c : resetList) {
			try {
				Method m = c.getMethod(RESET_METHOD_NAME);
				m.invoke(null);
			} catch (Exception e) {
				System.err.println("Failed to reset class " + c.getName());
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}
	
	/**
	 * Parses the number of runs, and an optional starting run index, from a 
	 * command line argument
	 * @param arg The argument to parse
	 * @return The first and (last_run_index - 1) in an array
	 */
	private static List<Integer> parseNrofRuns(String arg) {
		int val[] = {0,1};
		StringTokenizer tokenizer = new StringTokenizer(arg.trim(), ELEMENT_DELIMETER, false);
		ArrayList<Integer> valList = new ArrayList<Integer>();
		if (tokenizer.countTokens() == 1) {
			String element = tokenizer.nextToken();
			try {
				if (element.contains(RANGE_DELIMETER)) {
					val[0] = Integer.parseInt(element.substring(0,
												element.indexOf(RANGE_DELIMETER))) - 1;
					val[1] = Integer.parseInt(element.substring(
												element.indexOf(RANGE_DELIMETER) + 1,
												element.length()));
				}
				else {
					val[0] = 0;
					val[1] = Integer.parseInt(element);
				}
			} catch (NumberFormatException e) {
				System.err.println("Invalid argument '" + element + "' for number of runs");
				System.err.println("The argument must be either a single value or a " + 
									"comma-separated list of values. Range of values " +
									"(e.g., '2:5') are also admitted. Note that this " +
									"option has changed since version 1.3.");
				System.exit(-1);
			}
			
			for (int i = val[0]; i < val[1]; i++) {
				valList.add(i);
			}
		}
		else {
			while (tokenizer.hasMoreTokens()) {
				String element = tokenizer.nextToken();
				try {
					if (element.contains(RANGE_DELIMETER)) {
						val[0] = Integer.parseInt(element.substring(0,
													element.indexOf(RANGE_DELIMETER))) - 1;
						val[1] = Integer.parseInt(element.substring(
													element.indexOf(RANGE_DELIMETER) + 1,
													element.length()));
						for (int i = val[0]; i < val[1]; i++) {
							valList.add(i);
						}
					}
					else {
						valList.add(Integer.parseInt(element) - 1);
					}
				} catch (NumberFormatException e) {
					System.err.println("Invalid argument '" + element + "' for number of runs");
					System.err.println("The argument must be either a single value or a " + 
										"comma-separated list of values. Range of values " +
										"(e.g., '2:5') are also admitted. Note that this " +
										"option has changed since version 1.3.");
					System.exit(-1);
				}
			}
		}
		
		if (val[0] < 0) {
			System.err.println("Starting run value can't be smaller than 1");
			System.exit(-1);
		}
		for (int i = 0; i < valList.size() - 1; i++) {
			if (valList.get(i) >= valList.get(i + 1)) {
				System.err.println("Values in the run indexes list has to be in increasing order");
				System.exit(-1);
			}
		}
				
		return valList;
	}
	
	/**
	 * Prints text to stdout
	 * @param txt Text to print
	 */
	private static void print(String txt) {
		System.out.println(txt);
	}
}
